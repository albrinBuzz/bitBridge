package org.bitBridge.Tests.rsync;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

public class RealRsyncInPlaceFolder {

    private static final int BLOCK_SIZE = 1024;
    private static final int MOD_ADLER = 65521;

    record BlockSignature(int index, long weak, byte[] strong) {}

    static class TransferStats {
        long totalSize = 0;
        long bytesSent = 0;
        long bytesMatched = 0;
        int speedupBlocks = 0;
        String modoSincro = "DELTA"; // DELTA, NUEVO, ENVIO_DIRECTO, IDENTICO_SALTADO

        void add(TransferStats other) {
            this.totalSize += other.totalSize;
            this.bytesSent += other.bytesSent;
            this.bytesMatched += other.bytesMatched;
            this.speedupBlocks += other.speedupBlocks;
        }
    }

    public static void main(String[] args) throws Exception {
        long inicio = System.currentTimeMillis();
        Path folderOrigen = Paths.get("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/scripts/origen");
        Path folderDestino = Paths.get("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/scripts/destino");

        TransferStats globalStats = new TransferStats();

        System.out.println("=========================================================");
        System.out.println(">>> INICIANDO PROTOCOLO BITBRIDGE-RSYNC LOCAL (OPTIMIZADO)");
        System.out.println("=========================================================");

        if (!Files.exists(folderOrigen)) {
            System.err.println("El directorio origen no existe.");
            return;
        }

        Files.walkFileTree(folderOrigen, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = folderDestino.resolve(folderOrigen.relativize(dir));
                if (!Files.exists(targetDir)) Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = folderDestino.resolve(folderOrigen.relativize(file));

                System.out.println("\n[ARCHIVO]: " + folderOrigen.relativize(file));

                try {
                    TransferStats stats = new TransferStats();
                    long srcLength = Files.size(file);
                    stats.totalSize = srcLength;

                    if (!Files.exists(targetFile)) {
                        // Caso 1: Archivo completamente nuevo
                        stats = copiarArchivoCompleto(file.toFile(), targetFile.toFile());
                        stats.modoSincro = "NUEVO";
                    } else {
                        // EL FILTRO RÁPIDO: Verificar si el archivo ya es exactamente igual
                        if (srcLength == Files.size(targetFile) && calcularHashArchivoCompleto(file.toFile()).equals(calcularHashArchivoCompleto(targetFile.toFile()))) {
                            // Caso 2: El archivo ya existe y es idéntico. ¡LO SALTAMOS!
                            stats.bytesSent = 0;
                            stats.bytesMatched = srcLength; // Se asume que todo el contenido ya está reutilizado
                            stats.modoSincro = "IDENTICO_SALTADO";
                        } else if (srcLength < BLOCK_SIZE) {
                            // Caso 3: Modificado pero muy pequeño para Delta
                            stats = copiarArchivoCompleto(file.toFile(), targetFile.toFile());
                            stats.modoSincro = "ENVIO_DIRECTO";
                        } else {
                            // Caso 4: Candidato real para sincronización diferencial por bloques
                            Map<Long, List<BlockSignature>> sigs = generateSignatures(targetFile.toFile());
                            stats = updateInPlace(file.toFile(), targetFile.toFile(), sigs);
                            stats.modoSincro = "DELTA";
                        }
                    }

                    globalStats.add(stats);

                    double reusoFichero = (stats.totalSize == 0) ? 0 : (double) stats.bytesMatched / stats.totalSize * 100;
                    System.out.printf("  -> Tipo Sincro: [%s] | Total: %,d B | Reutilizado: %,d B (%.1f%%) | Transmitido: %,d B%n",
                            stats.modoSincro, stats.totalSize, stats.bytesMatched, reusoFichero, stats.bytesSent);

                } catch (Exception e) {
                    System.err.println("  -> Error procesando archivo: " + e.getMessage());
                    e.printStackTrace();
                }
                return FileVisitResult.CONTINUE;
            }
        });

        long fin = System.currentTimeMillis();
        double segundos = (fin - inicio) / 1000.0;

        printSummary(globalStats, segundos);
    }

    private static TransferStats updateInPlace(File source, File target, Map<Long, List<BlockSignature>> remoteSigs) throws Exception {
        TransferStats stats = new TransferStats();
        stats.totalSize = source.length();

        try (RandomAccessFile rafSource = new RandomAccessFile(source, "r");
             RandomAccessFile rafTarget = new RandomAccessFile(target, "r")) {

            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            long currentSourcePos = 0;
            StringBuilder newCharsBuffer = new StringBuilder();

            while (currentSourcePos < stats.totalSize) {
                boolean matchFound = false;

                if (currentSourcePos <= stats.totalSize - BLOCK_SIZE) {
                    rafSource.seek(currentSourcePos);
                    byte[] window = new byte[BLOCK_SIZE];
                    rafSource.readFully(window);

                    long currentWeak = calculateAdler32(window, BLOCK_SIZE);

                    if (remoteSigs.containsKey(currentWeak)) {
                        byte[] currentStrong = calculateMD5(window, BLOCK_SIZE);
                        for (BlockSignature sig : remoteSigs.get(currentWeak)) {
                            if (Arrays.equals(sig.strong, currentStrong)) {
                                flushNewData(newCharsBuffer);

                                byte[] blockFromTarget = new byte[BLOCK_SIZE];
                                long targetBlockPos = (long) sig.index * BLOCK_SIZE;

                                if (targetBlockPos + BLOCK_SIZE <= rafTarget.length()) {
                                    rafTarget.seek(targetBlockPos);
                                    rafTarget.readFully(blockFromTarget);
                                    outputBuffer.write(blockFromTarget);
                                } else {
                                    outputBuffer.write(window);
                                }

                                stats.bytesMatched += BLOCK_SIZE;
                                stats.speedupBlocks++;
                                currentSourcePos += BLOCK_SIZE;
                                matchFound = true;
                                break;
                            }
                        }
                    }
                }

                if (!matchFound) {
                    rafSource.seek(currentSourcePos);
                    int b = rafSource.read();
                    outputBuffer.write(b);

                    if (b >= 32 && b <= 126 || b == '\n' || b == '\r') {
                        newCharsBuffer.append((char) b);
                    } else {
                        newCharsBuffer.append(".");
                    }

                    stats.bytesSent++;
                    currentSourcePos++;
                }
            }
            flushNewData(newCharsBuffer);

            try (RandomAccessFile rafTargetWrite = new RandomAccessFile(target, "rw")) {
                rafTargetWrite.seek(0);
                rafTargetWrite.write(outputBuffer.toByteArray());
                rafTargetWrite.setLength(outputBuffer.size());
            }
        }
        return stats;
    }

    private static TransferStats copiarArchivoCompleto(File source, File target) throws IOException {
        TransferStats stats = new TransferStats();
        stats.totalSize = source.length();
        stats.bytesSent = source.length();
        stats.bytesMatched = 0;

        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(target)) {
            byte[] buf = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buf)) != -1) {
                os.write(buf, 0, bytesRead);
            }
        }
        return stats;
    }

    private static String calcularHashArchivoCompleto(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void flushNewData(StringBuilder buffer) {
        if (buffer.length() > 0) {
            String output = buffer.toString().trim();
            System.out.println("   [DATO NUEVO INYECTADO]: "+output );

            buffer.setLength(0);
        }
    }

    private static Map<Long, List<BlockSignature>> generateSignatures(File file) throws Exception {
        Map<Long, List<BlockSignature>> signatures = new HashMap<>();
        if (!file.exists() || file.length() < BLOCK_SIZE) return signatures;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead, blockIndex = 0;
            while ((bytesRead = raf.read(buffer)) != -1) {
                if (bytesRead < BLOCK_SIZE) break;
                signatures.computeIfAbsent(calculateAdler32(buffer, bytesRead), k -> new ArrayList<>())
                        .add(new BlockSignature(blockIndex++, 0, calculateMD5(buffer, bytesRead)));
            }
        }
        return signatures;
    }

    private static void printSummary(TransferStats stats, double segundos) {
        double ratioAhorro = (stats.totalSize == 0) ? 0 : (double) stats.bytesMatched / stats.totalSize * 100;
        long totalTransmitidoRed = stats.bytesSent;
        double factorAceleracion = totalTransmitidoRed == 0 ? stats.totalSize : (double) stats.totalSize / totalTransmitidoRed;

        System.out.println("\n=======================================================");
        System.out.println("   RESUMEN METRICAS GLOBALES DE SINCRO (BITBRIDGE)");
        System.out.println("=======================================================");
        System.out.printf(" Tiempo Total de Ejecución:  %.3f segundos%n", segundos);
        System.out.printf(" Volumen Total de la Carpeta:%,d bytes%n", stats.totalSize);
        System.out.printf(" Datos Transmitidos (Netos): %,d bytes%n", stats.bytesSent);
        System.out.printf(" Datos Reutilizados (Espejo):%,d bytes%n", stats.bytesMatched);
        System.out.printf(" Bloques Delta Reutilizados: %d bloques (Tamaño: %dB)%n", stats.speedupBlocks, BLOCK_SIZE);
        System.out.printf(" Eficiencia de Transferencia: %.2f%%%n", ratioAhorro);
        System.out.printf(" Multiplicador de Velocidad:  %.2fx%n", factorAceleracion);
        System.out.println("=======================================================");
    }

    private static long calculateAdler32(byte[] data, int len) {
        long a = 1, b = 0;
        for (int i = 0; i < len; i++) {
            a = (a + (data[i] & 0xFF)) % 65521;
            b = (b + a) % 65521;
        }
        return (b << 16) | a;
    }

    private static byte[] calculateMD5(byte[] data, int len) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(data, 0, len);
        return md.digest();
    }
}
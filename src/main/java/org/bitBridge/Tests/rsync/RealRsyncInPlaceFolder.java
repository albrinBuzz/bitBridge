package org.bitBridge.Tests.rsync;



import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

public class RealRsyncInPlaceFolder {

    private static final int BLOCK_SIZE = 8;

    record BlockSignature(int index, long weak, byte[] strong) {}

    static class TransferStats {
        long totalSize = 0;
        long bytesSent = 0;
        long bytesMatched = 0;
        int speedupBlocks = 0;

        void add(TransferStats other) {
            this.totalSize += other.totalSize;
            this.bytesSent += other.bytesSent;
            this.bytesMatched += other.bytesMatched;
            this.speedupBlocks += other.speedupBlocks;
        }
    }

    public static void main(String[] args) throws Exception {
        // Rutas de carpetas
        Path folderOrigen = Paths.get("/home/cris/java/javafx/proyectos/bitBrige/bitBrige/scripts/origen");
        Path folderDestino = Paths.get("/home/cris/java/javafx/proyectos/bitBrige/bitBrige/scripts/destino");

        TransferStats globalStats = new TransferStats();

        System.out.println(">>> Iniciando Sincronización de Carpetas...");

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
                System.out.println("\n--- Procesando: " + file.getFileName() + " ---");

                try {
                    Map<Long, List<BlockSignature>> sigs = generateSignatures(targetFile.toFile());
                    TransferStats stats = updateInPlace(file.toFile(), targetFile.toFile(), sigs);
                    globalStats.add(stats);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return FileVisitResult.CONTINUE;
            }
        });

        printSummary(globalStats);
    }

    private static TransferStats updateInPlace(File source, File target, Map<Long, List<BlockSignature>> remoteSigs) throws Exception {
        TransferStats stats = new TransferStats();
        stats.totalSize = source.length();

        try (RandomAccessFile rafSource = new RandomAccessFile(source, "r");
             RandomAccessFile rafTarget = new RandomAccessFile(target, "rw")) {

            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            long currentSourcePos = 0;
            StringBuilder newCharsBuffer = new StringBuilder(); // Para mostrar qué se envió

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
                                // Antes de escribir un bloque, vaciamos lo acumulado como "nuevo"
                                flushNewData(newCharsBuffer);

                                outputBuffer.write(window);
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
                    newCharsBuffer.append((char) b); // Guardamos para el log
                    stats.bytesSent++;
                    currentSourcePos++;
                }
            }
            flushNewData(newCharsBuffer); // Últimos datos literales

            rafTarget.seek(0);
            rafTarget.write(outputBuffer.toByteArray());
            rafTarget.setLength(outputBuffer.size());
        }
        return stats;
    }

    private static void flushNewData(StringBuilder buffer) {
        if (buffer.length() > 0) {
            System.out.println(" [ENVIADO LITERAL]: \"" + buffer.toString().replace("\n", "\\n") + "\"");
            buffer.setLength(0);
        }
    }

    // --- MÉTODOS DE APOYO ---

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

    private static void printSummary(TransferStats stats) {
        double ratio = (stats.totalSize == 0) ? 0 : (double) stats.bytesMatched / stats.totalSize * 100;
        System.out.println("\n========================================");
        System.out.println("   RESUMEN GLOBAL DE SINCRONIZACIÓN");
        System.out.println("========================================");
        System.out.printf("Total procesado:    %d bytes\n", stats.totalSize);
        System.out.printf("Total enviado:      %d bytes\n", stats.bytesSent);
        System.out.printf("Total reutilizado:  %d bytes\n", stats.bytesMatched);
        System.out.printf("Ahorro de red:      %.2f%%\n", ratio);
        System.out.println("========================================");
    }

    private static long calculateAdler32(byte[] data, int len) {
        long a = 1, b = 0;
        for (int i = 0; i < len; i++) { a = (a + (data[i] & 0xFF)) % 65521; b = (b + a) % 65521; }
        return (b << 16) | a;
    }

    private static byte[] calculateMD5(byte[] data, int len) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(data, 0, len);
        return md.digest();
    }
}

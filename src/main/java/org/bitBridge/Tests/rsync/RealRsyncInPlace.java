package org.bitBridge.Tests.rsync;



import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

public class RealRsyncInPlace {
    private static final int BLOCK_SIZE = 1024;

    record BlockSignature(int index, long weak, byte[] strong) {}
    // Clase para capturar métricas al estilo rsync
    static class TransferStats {
        long totalSize = 0;
        long bytesSent = 0;    // Datos nuevos (literales)
        long bytesMatched = 0; // Datos reutilizados
        int speedupBlocks = 0;
    }

    public static void main(String[] args) throws Exception {
        // El archivo que será MODIFICADO directamente
        File fileDestino = new File("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/scripts/destino/logTexto.txt");
        //File fileDestino = new File("/home/cris/java/javafx/proyectos/bitBrige/bitBrige/scripts/archivo_destino.txt");

        // La fuente de la verdad
        //File fileOrigen = new File("/home/cris/java/javafx/proyectos/bitBrige/bitBrige/scripts/archivo_origen.txt");
        File fileOrigen = new File("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/scripts/origen/logTexto.txt");

        //prepararEscenario(fileDestino, fileOrigen);

        System.out.println("--- 1. Firmando el destino actual ---");
        Map<Long, List<BlockSignature>> remoteSignatures = generateSignatures(fileDestino);

        /*System.out.println("--- 2. Actualizando destino IN-PLACE ---");
        updateInPlace(fileOrigen, fileDestino, remoteSignatures);*/

        System.out.println("--- Iniciando transferencia delta ---");
        TransferStats stats = updateInPlace(fileOrigen, fileDestino, remoteSignatures);

        // Imprimir resumen final como rsync -v
        double ratio = (stats.totalSize == 0) ? 0 : (double) stats.bytesMatched / stats.totalSize * 100;

        System.out.println("\n--- ESTADÍSTICAS DE RSYNC ---");
        System.out.printf("Tamaño total del archivo: %d bytes\n", stats.totalSize);
        System.out.printf("Datos enviados (nuevos):  %d bytes\n", stats.bytesSent);
        System.out.printf("Datos igualados (locales): %d bytes\n", stats.bytesMatched);
        System.out.printf("Bloques reutilizados:      %d\n", stats.speedupBlocks);
        System.out.printf("Ahorro de ancho de banda:  %.2f%%\n", ratio);

    }

    private static TransferStats updateInPlace(File source, File target, Map<Long, List<BlockSignature>> remoteSigs) throws Exception {
        TransferStats stats = new TransferStats();
        stats.totalSize = source.length();

        try (RandomAccessFile rafSource = new RandomAccessFile(source, "r");
             RandomAccessFile rafTarget = new RandomAccessFile(target, "rw")) {

            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            long currentSourcePos = 0;

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
                    stats.bytesSent++; // Sumamos a la "red"
                    currentSourcePos++;
                }
            }

            rafTarget.seek(0);
            rafTarget.write(outputBuffer.toByteArray());
            rafTarget.setLength(outputBuffer.size());
        }
        return stats;
    }

    // --- MÉTODOS DE APOYO (Iguales a la versión anterior) ---

    private static Map<Long, List<BlockSignature>> generateSignatures(File file) throws Exception {
        Map<Long, List<BlockSignature>> signatures = new HashMap<>();
        if (!file.exists()) return signatures;
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

    private static void prepararEscenario(File dest, File src) throws IOException {
        Files.writeString(dest.toPath(), "TEXTO VIEJO QUE SERA ACTUALIZADO");
        Files.writeString(src.toPath(), "TEXTO NUEVO - EL VIEJO FUE ACTUALIZADO");
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
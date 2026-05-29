package org.bitBridge.Tests.rsync;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public class RealRsync {
    // Para ver el algoritmo en acción con texto, un bloque de 8 es ideal.
    private static final int BLOCK_SIZE = 8;

    record BlockSignature(int index, long weak, byte[] strong) {}

    public static void main(String[] args) throws Exception {
        // Rutas (ajustadas a tus variables o archivos locales)
        File fileDestino = new File("numero.txt");
        File fileOrigen = new File("numero2.txt");
        File fileFinal = new File("numero_RESTRUIDO.txt");

        // Crear archivos de prueba si no existen (Solo para demostración)
        prepararEscenarioDePrueba(fileDestino, fileOrigen);

        System.out.println("--- 1. Generando firmas del archivo DESTINO (el que ya tenemos) ---");
        Map<Long, List<BlockSignature>> remoteSignatures = generateSignatures(fileDestino);

        System.out.println("--- 2. Iniciando Sincronización Delta ---");
        Metrics metrics = syncAndReconstruct(fileOrigen, fileDestino, fileFinal, remoteSignatures);

        System.out.println("\n--- RESULTADOS DE EFICIENCIA ---");
        System.out.printf("Tamaño archivo nuevo: %d bytes\n", metrics.totalBytes);
        System.out.printf("Bytes reutilizados (locales): %d\n", metrics.reusedBytes);
        System.out.printf("Bytes transferidos (nuevos): %d\n", metrics.newBytes);
        System.out.printf("EFICIENCIA: %.2f%% de ahorro en red\n",
                ((double) metrics.reusedBytes / metrics.totalBytes) * 100);
    }

    private static Map<Long, List<BlockSignature>> generateSignatures(File file) throws Exception {
        Map<Long, List<BlockSignature>> signatures = new HashMap<>();
        if (!file.exists()) return signatures;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            int blockIndex = 0;
            while ((bytesRead = raf.read(buffer)) != -1) {
                if (bytesRead < BLOCK_SIZE) break;

                long weak = calculateAdler32(buffer, bytesRead);
                byte[] strong = calculateMD5(buffer, bytesRead);

                signatures.computeIfAbsent(weak, k -> new ArrayList<>())
                        .add(new BlockSignature(blockIndex, weak, strong));
                blockIndex++;
            }
        }
        return signatures;
    }

    private static Metrics syncAndReconstruct(File source, File targetOld, File targetNew,
                                              Map<Long, List<BlockSignature>> remoteSigs) throws Exception {
        Metrics m = new Metrics();
        m.totalBytes = source.length();

        try (RandomAccessFile rafSource = new RandomAccessFile(source, "r");
             RandomAccessFile rafOld = new RandomAccessFile(targetOld, "r");
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(targetNew))) {

            long currentPos = 0;
            byte[] window = new byte[BLOCK_SIZE];

            while (currentPos < m.totalBytes) {
                boolean matchFound = false;

                if (currentPos <= m.totalBytes - BLOCK_SIZE) {
                    rafSource.seek(currentPos);
                    rafSource.readFully(window);
                    long currentWeak = calculateAdler32(window, BLOCK_SIZE);

                    if (remoteSigs.containsKey(currentWeak)) {
                        byte[] currentStrong = calculateMD5(window, BLOCK_SIZE);
                        for (BlockSignature sig : remoteSigs.get(currentWeak)) {
                            if (Arrays.equals(sig.strong, currentStrong)) {
                                // REUTILIZACIÓN
                                byte[] oldData = new byte[BLOCK_SIZE];
                                rafOld.seek((long) sig.index * BLOCK_SIZE);
                                rafOld.readFully(oldData);
                                out.write(oldData);

                                m.reusedBytes += BLOCK_SIZE;
                                currentPos += BLOCK_SIZE;
                                matchFound = true;
                                break;
                            }
                        }
                    }
                }

                if (!matchFound) {
                    // DATO NUEVO
                    rafSource.seek(currentPos);
                    out.write(rafSource.read());
                    m.newBytes++;
                    currentPos++;
                }
            }
        }
        return m;
    }

    private static void prepararEscenarioDePrueba(File v1, File v2) throws IOException {
        String base = "ESTE_ES_UN_BLOQUE_MUY_LARGO_QUE_NO_DEBERIA_MOVERSE_POR_LA_RED_12345678";
        // Escribimos v1
        try (FileWriter fw = new FileWriter(v1)) { fw.write(base + " (VERSION VIEJA)"); }
        // Escribimos v2 (Cambiamos el inicio, pero el resto es igual)
        try (FileWriter fw = new FileWriter(v2)) { fw.write("MODIFICADO_" + base + " (VERSION NUEVA)"); }
    }

    private static long calculateAdler32(byte[] data, int length) {
        long a = 1, b = 0;
        for (int i = 0; i < length; i++) {
            a = (a + (data[i] & 0xFF)) % 65521;
            b = (b + a) % 65521;
        }
        return (b << 16) | a;
    }

    private static byte[] calculateMD5(byte[] data, int length) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(data, 0, length);
        return md.digest();
    }

    static class Metrics {
        long totalBytes = 0;
        long reusedBytes = 0;
        long newBytes = 0;
    }
}
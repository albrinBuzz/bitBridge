package org.bitBridge.Tests.rsync;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

public class RsyncLocalTest {
    // Bloques pequeños para que sea fácil ver el log en consola
    private static final int BLOCK_SIZE = 16;

    public static void main(String[] args) throws Exception {
        // 1. PREPARACIÓN DE DATOS
        // Archivo A: Datos originales
        String original = "ESTO ES UN TEXTO DE PRUEBA PARA BITBRIDGE RSYNC ALGORITHM";
        byte[] fileA = original.getBytes();

        // Archivo B: Modificado (insertamos "NUEVO " en el medio)
        // Esto desplaza todo lo demás, lo que mataría a una copia normal
        String modificado = "ESTO ES UN TEXTO NUEVOSD DE PRUEBA PARA La BITBRIDGE RSYNC ALGORITHM";
        byte[] fileB = modificado.getBytes();

        System.out.println("--- INICIANDO SIMULACIÓN DE DELTA SYNC ---");
        System.out.println("Archivo A (Destino): " + original);
        System.out.println("Archivo B (Origen):  " + modificado);
        System.out.println("------------------------------------------\n");

        // 2. FASE DE FIRMAS (Lado Destino / Archivo A)
        // El servidor genera firmas de lo que ya tiene
        Map<Long, String> signatureTable = new HashMap<>();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        for (int i = 0; i < fileA.length; i += BLOCK_SIZE) {
            int len = Math.min(BLOCK_SIZE, fileA.length - i);
            long weak = calculateWeakChecksum(fileA, i, len);

            md5.update(fileA, i, len);
            String strong = bytesToHex(md5.digest());

            signatureTable.put(weak, strong);
            System.out.printf("[FIRMA] Bloque en %d: Weak=%d, Strong=%s%n", i, weak, strong.substring(0, 8));
        }

        // 3. FASE DE ESCANEO DESLIZANTE (Lado Origen / Archivo B)
        System.out.println("\n--- ESCANEANDO DIFERENCIAS EN ARCHIVO B ---");
        int cursor = 0;
        StringBuilder literalBuffer = new StringBuilder();

        while (cursor < fileB.length) {
            int remaining = fileB.length - cursor;
            int currentLen = Math.min(BLOCK_SIZE, remaining);

            // Calculamos el checksum débil de la ventana actual
            long currentWeak = calculateWeakChecksum(fileB, cursor, currentLen);

            boolean match = false;
            if (signatureTable.containsKey(currentWeak)) {
                // Posible coincidencia, verificar hash fuerte
                md5.update(fileB, cursor, currentLen);
                String currentStrong = bytesToHex(md5.digest());

                if (currentStrong.equals(signatureTable.get(currentWeak))) {
                    // ¡COINCIDENCIA ENCONTRADA!
                    if (!literalBuffer.isEmpty()) {
                        System.err.println("[DATOS NUEVOS]: " + literalBuffer);
                        literalBuffer.setLength(0);
                    }
                    System.out.println("[BLOQUE EXISTENTE] Encontrado en pos B:" + cursor + " (Se copia del original)");
                    cursor += currentLen;
                    match = true;
                }
            }

            if (!match) {
                // No hay match: guardamos el byte actual y rodamos la ventana 1 byte
                literalBuffer.append((char) fileB[cursor]);
                cursor++;
            }
        }

        // Mostrar remanente
        if (literalBuffer.length() > 0) {
            System.err.println("[DATOS NUEVOS]: " + literalBuffer);
        }
    }

    // Algoritmo de Checksum Débil (Suma simple para el ejemplo)
    private static long calculateWeakChecksum(byte[] data, int offset, int len) {
        long a = 0, b = 0;
        for (int i = 0; i < len; i++) {
            a = (a + (data[offset + i] & 0xFF)) % 65521;
            b = (b + a) % 65521;
        }
        return (b << 16) | a;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
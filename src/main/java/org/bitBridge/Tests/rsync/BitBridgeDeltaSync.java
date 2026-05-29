package org.bitBridge.Tests.rsync;


import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

public class BitBridgeDeltaSync {
    private static final int BLOCK_SIZE = 1024; // 1KB por bloque

    // Estructura para el archivo reconstruido
    static class DeltaInstruction {
        Integer blockIndex; // Si es != null, copiar bloque del archivo viejo
        byte[] literalData; // Si es != null, estos son datos nuevos
    }

    public static void main(String[] args) throws Exception {
        // 1. SIMULACIÓN DE ARCHIVOS BINARIOS (Puedes usar archivos reales con Files.readAllBytes)
        byte[] fileOld = "CONTENIDO_INICIAL_BINARIO_MUY_LARGO_DATOS_DATOS_FIN".getBytes();
        // El nuevo tiene cambios al inicio, medio y fin
        //byte[] fileNew = "NUEVO_INICIO_INICIAL_BINARIO_MODIFICADO_LARGO_DATOS_MAS_DATOS_FIN_V2".getBytes();
        byte[] fileNew = "CONTENIDO_INICIAL_BINARIO_1_MUY_LARGO_DATOS_DATOS_FIN".getBytes();

        System.out.println("Tamaño Original: " + fileOld.length + " bytes");
        System.out.println("Tamaño Nuevo:    " + fileNew.length + " bytes");

        // --- FASE 1: GENERACIÓN DE FIRMAS (RECEPTOR) ---
        Map<Long, Map<String, Integer>> signatureMap = generateSignatures(fileOld);

        // --- FASE 2: ESCANEO Y GENERACIÓN DE DELTAS (EMISOR) ---
        List<DeltaInstruction> deltas = generateDeltas(fileNew, signatureMap);

        // --- FASE 3: RECONSTRUCCIÓN (RECEPTOR) ---
        byte[] reconstructedFile = reconstruct(fileOld, deltas);

        // VERIFICACIÓN
        boolean success = Arrays.equals(fileNew, reconstructedFile);
        System.out.println("\n------------------------------------------");
        System.out.println("¿Reconstrucción Exitosa?: " + (success ? "SÍ ✅" : "NO ❌"));
        System.out.println("Instrucciones enviadas: " + deltas.size());
        System.out.println("Contenido: " + new String(reconstructedFile));
    }

    // --- LÓGICA DEL ALGORITMO ---

    private static Map<Long, Map<String, Integer>> generateSignatures(byte[] data) throws Exception {
        Map<Long, Map<String, Integer>> signatures = new HashMap<>();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        for (int i = 0; i < data.length; i += BLOCK_SIZE) {
            int len = Math.min(BLOCK_SIZE, data.length - i);
            long weak = calculateWeakChecksum(data, i, len);

            md5.update(data, i, len);
            String strong = bytesToHex(md5.digest());

            // Agrupamos por Weak para manejar colisiones raras
            signatures.computeIfAbsent(weak, k -> new HashMap<>()).put(strong, i / BLOCK_SIZE);
        }
        return signatures;
    }

    private static List<DeltaInstruction> generateDeltas(byte[] data, Map<Long, Map<String, Integer>> sigs) throws Exception {
        List<DeltaInstruction> instructions = new ArrayList<>();
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        int cursor = 0;
        List<Byte> literalAccumulator = new ArrayList<>();

        while (cursor < data.length) {
            int len = Math.min(BLOCK_SIZE, data.length - cursor);
            long currentWeak = calculateWeakChecksum(data, cursor, len);

            Integer matchIndex = null;
            if (sigs.containsKey(currentWeak)) {
                md5.update(data, cursor, len);
                String currentStrong = bytesToHex(md5.digest());
                matchIndex = sigs.get(currentWeak).get(currentStrong);
            }

            if (matchIndex != null) {
                // Si había literales acumulados, guardarlos antes del bloque
                flushLiterals(literalAccumulator, instructions);

                DeltaInstruction inst = new DeltaInstruction();
                inst.blockIndex = matchIndex;
                instructions.add(inst);
                cursor += len;
            } else {
                literalAccumulator.add(data[cursor]);
                cursor++;
            }
        }
        flushLiterals(literalAccumulator, instructions);
        return instructions;
    }

    private static byte[] reconstruct(byte[] oldFile, List<DeltaInstruction> deltas) {
        List<Byte> result = new ArrayList<>();
        for (DeltaInstruction inst : deltas) {
            if (inst.blockIndex != null) {
                // Copiar bloque del viejo
                int start = inst.blockIndex * BLOCK_SIZE;
                int len = Math.min(BLOCK_SIZE, oldFile.length - start);
                for (int i = 0; i < len; i++) result.add(oldFile[start + i]);
            } else {
                String contenidoNuevo = new String(inst.literalData);
                System.out.println("[LITERAL] Datos nuevos detectados: [" + contenidoNuevo + "]");
                // Insertar datos nuevos
                for (byte b : inst.literalData) result.add(b);
            }
        }
        // Convertir List<Byte> a byte[]
        byte[] out = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) out[i] = result.get(i);
        return out;
    }

    private static void reconstruirEnDisco(Path viejo, Path destino, List<DeltaInstruction> deltas) throws Exception {
        try (RandomAccessFile readerViejo = new RandomAccessFile(viejo.toFile(), "r");
             FileOutputStream writerNuevo = new FileOutputStream(destino.toFile())) {

            for (DeltaInstruction inst : deltas) {
                if (inst.blockIndex != null) {
                    // Magia de Rsync: Copiamos del archivo que YA tenemos en disco
                    byte[] buffer = new byte[BLOCK_SIZE];
                    readerViejo.seek((long) inst.blockIndex * BLOCK_SIZE);
                    int leidos = readerViejo.read(buffer);
                    writerNuevo.write(buffer, 0, leidos);
                } else {
                    // Datos nuevos: Se escriben directamente
                    writerNuevo.write(inst.literalData);
                }
            }
        }
    }

    // --- UTILIDADES ---

    private static void flushLiterals(List<Byte> accumulator, List<DeltaInstruction> instructions) {
        if (!accumulator.isEmpty()) {
            DeltaInstruction inst = new DeltaInstruction();
            inst.literalData = new byte[accumulator.size()];
            for (int i = 0; i < accumulator.size(); i++) inst.literalData[i] = accumulator.get(i);
            instructions.add(inst);
            accumulator.clear();
        }
    }

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
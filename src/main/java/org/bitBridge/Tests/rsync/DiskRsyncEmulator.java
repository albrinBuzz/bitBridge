package org.bitBridge.Tests.rsync;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class DiskRsyncEmulator {
    private static final int BLOCK_SIZE = 1024; // 1KB

    static class DeltaInstruction {
        Integer blockIndex; // Si es != null, copiar bloque del archivo viejo
        byte[] literalData; // Si es != null, estos son datos nuevos
    }

    public static void main(String[] args) throws Exception {
        // Rutas de archivos reales
        Path pathViejo = Paths.get("scripts","numero.txt");
        Path pathNuevo = Paths.get("scripts","numero2.txt");
        Path pathReconstruido = Paths.get("scripts","textoResc.txt");

        // 1. CREAR DATOS DE PRUEBA REALES
        System.out.println("Cargando archivos de disco...");
        // Generamos un archivo de 10KB de basura binaria + texto
        byte[] datosOriginales = new byte[1024 * 10];
        new Random().nextBytes(datosOriginales);
        Files.write(pathViejo, datosOriginales);

        // Creamos la versión nueva: Mismos datos pero con un "parche" de 100 bytes al inicio
        byte[] parche = "ESTO ES UN CAMBIO MANUAL EN EL ARCHIVO BINARIO PARA PROBAR EL DELTA SYNC EN DISCO".getBytes();
        ByteBuffer bb = ByteBuffer.allocate(datosOriginales.length + parche.length);
        bb.put(datosOriginales, 0, 500); // Ponemos un trozo del original
        bb.put(parche);                  // Metemos el cambio en medio
        bb.put(datosOriginales, 500, datosOriginales.length - 500); // El resto
        Files.write(pathNuevo, bb.array());

        // --- INICIO DEL ALGORITMO RSYNC ---

        // FASE 1: RECEPTOR genera firmas del archivo viejo
        System.out.println("Generando firmas del archivo viejo...");
        Map<Long, Map<String, Integer>> firmas = generarFirmas(Files.readAllBytes(pathViejo));

        // FASE 2: EMISOR escanea el archivo nuevo y busca coincidencias
        System.out.println("Escaneando archivo nuevo para encontrar deltas...");
        List<DeltaInstruction> deltas = generarDeltas(Files.readAllBytes(pathNuevo), firmas);

        // FASE 3: RECONSTRUCCIÓN (Cerrando el ciclo)
        System.out.println("Reconstruyendo archivo final...");
        reconstruirEnDisco(pathViejo, pathReconstruido, deltas);

        // --- VERIFICACIÓN FINAL ---
        long originalSize = Files.size(pathNuevo);
        long reconstruidoSize = Files.size(pathReconstruido);
        boolean iguales = Arrays.equals(Files.readAllBytes(pathNuevo), Files.readAllBytes(pathReconstruido));

        System.out.println("\n--- RESULTADOS ---");
        System.out.println("Archivo Nuevo Original: " + originalSize + " bytes");
        System.out.println("Archivo Reconstruido:   " + reconstruidoSize + " bytes");
        System.out.println("¿Integridad perfecta?:  " + (iguales ? "SÍ ✅" : "NO ❌"));
    }

    private static List<DeltaInstruction> generarDeltas(byte[] data, Map<Long, Map<String, Integer>> sigs) throws NoSuchAlgorithmException {
        List<DeltaInstruction> instructions = new ArrayList<>();
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        int cursor = 0;

        // Usamos un stream de bytes, mucho más eficiente que List<Byte>
        ByteArrayOutputStream literalBuffer = new ByteArrayOutputStream();

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
                // Antes de añadir el bloque, vaciamos los literales acumulados
                if (literalBuffer.size() > 0) {
                    DeltaInstruction inst = new DeltaInstruction();
                    inst.literalData = literalBuffer.toByteArray();
                    instructions.add(inst);
                    literalBuffer.reset();
                }

                DeltaInstruction inst = new DeltaInstruction();
                inst.blockIndex = matchIndex;
                instructions.add(inst);
                cursor += len;
            } else {
                // Escribimos el byte actual al buffer de literales
                literalBuffer.write(data[cursor]);
                cursor++;
            }
        }

        // Aseguramos que no queden literales al final
        if (literalBuffer.size() > 0) {
            DeltaInstruction inst = new DeltaInstruction();
            inst.literalData = literalBuffer.toByteArray();
            instructions.add(inst);
        }
        return instructions;
    }

    private static Map<Long, Map<String, Integer>> generarFirmas(byte[] data) throws NoSuchAlgorithmException {
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


    // --- MÉTODOS DE SOPORTE ---

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
// (Aquí irían los métodos de generarFirmas, generarDeltas y calculateWeakChecksum del ejemplo anterior)
// ... Por brevedad, asume que están integrados aquí ...
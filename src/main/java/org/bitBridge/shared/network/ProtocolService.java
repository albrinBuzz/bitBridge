package org.bitBridge.shared.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaInstruction;
import org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaPackage;
import org.bitBridge.shared.memory.DirectBufferPool;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolService {
    private static final Gson gson = new Gson();
    private static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

    private static final String PACKET_PACKAGE = "org.bitBridge.shared.core.comunication.";
    private static final Map<String, Class<?>> dynamicClassRegistry = new HashMap<>();
    private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    static {
        long startTime = System.currentTimeMillis();
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages("org.bitBridge.shared.core.comunication")
                .scan()) {

            List<Class<?>> communicationSubclasses = scanResult
                    .getSubclasses(Communication.class.getName())
                    .loadClasses();

            for (Class<?> clazz : communicationSubclasses) {
                dynamicClassRegistry.put(clazz.getSimpleName(), clazz);
            }

            long duration = System.currentTimeMillis() - startTime;
            Logger.logInfo("[ProtocolService] Escaneo dinámico completado en " + duration + "ms. " +
                    "Se indexaron " + dynamicClassRegistry.size() + " paquetes de red de forma automática.");

        } catch (Exception e) {
            Logger.logError("[ProtocolService] Error crítico inicializando el registro dinámico de paquetes: " + e.getMessage());
        }
    }

    /**
     * Escribe un objeto Communication en el stream usando el formato compatible.
     */
    public static void writeFormattedPayload(DataOutputStream out, Communication comm) throws IOException {
        // Intercepción binaria para streams convencionales
        if (comm instanceof org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaPackage deltaPkg) {
            writeRsyncDeltaStream(out, deltaPkg);
            return;
        }

        String json = gson.toJson(comm);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] typeBytes = comm.getCommunicationId().getBytes(StandardCharsets.UTF_8);

        out.writeInt(jsonBytes.length);
        out.writeInt(typeBytes.length);
        out.write(typeBytes);
        out.write(jsonBytes);
        out.flush();
    }

    /**
     * Lee y reconstruye el objeto desde un DataInputStream tradicional.
     */
    public static Communication readFormattedPayload(DataInputStream in) throws IOException {
        int jsonLen = in.readInt(); // Representa payloadSize si es binario
        int typeLen = in.readInt();

        byte[] typeBytes = new byte[typeLen];
        in.readFully(typeBytes);
        String className = new String(typeBytes, StandardCharsets.UTF_8).trim();

        if ("RsyncDeltaPackage".equals(className)) {
            return deserializeRsyncDeltaFromStream(in, jsonLen);
        }

        byte[] jsonBytes = new byte[jsonLen];
        in.readFully(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);

        logJsonString(json, className);
        return deserializeByClassName(json, className);
    }

    /**
     * 🚀 ARREGLADO: LEER DESDE ARREGLOS DE BYTES (El punto exacto del Crash del Relay)
     */
    public static Communication fromBytes(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        if (buffer.remaining() < 8) throw new IOException("Paquete demasiado corto (falta longitud)");

        int jsonLen = buffer.getInt(); // Representa payloadSize si es binario
        int typeLen = buffer.getInt();

        if (buffer.remaining() < typeLen) throw new IOException("Paquete incompleto (falta identificador)");
        byte[] typeBytes = new byte[typeLen];
        buffer.get(typeBytes);
        String className = new String(typeBytes, StandardCharsets.UTF_8).trim();

        if (className.isEmpty()) {
            throw new IOException("Protocol Desync: Nombre de tipo vacío detectado.");
        }

        // Interceptamos la conversión antes de que Gson intente parsear binario crudo
        if ("RsyncDeltaPackage".equals(className)) {
            return deserializeRsyncDeltaFromBuffer(buffer, jsonLen);
        }

        if (buffer.remaining() < jsonLen) throw new IOException("Paquete incompleto (faltan bytes de JSON)");
        byte[] jsonBytes = new byte[jsonLen];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);

        logJsonString(json, className);

        return deserializeByClassName(json, className);
    }

    public static Communication readNIO(SocketChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8);
        while (header.hasRemaining()) {
            if (channel.read(header) == -1) throw new IOException("Canal cerrado en lectura de Header");
        }
        header.flip();

        int jsonLen = header.getInt();
        int typeLen = header.getInt();
        int bodySize = typeLen + jsonLen;

        ByteBuffer typeNameBuffer = ByteBuffer.allocate(typeLen);
        while (typeNameBuffer.hasRemaining()) {
            channel.read(typeNameBuffer);
        }
        String className = new String(typeNameBuffer.array(), StandardCharsets.UTF_8).trim();

        if ("RsyncDeltaPackage".equals(className)) {
            return deserializeRsyncDeltaDirect(channel, jsonLen);
        }

        DirectBufferPool.BufferType poolType = getPoolForClassName(className);
        ByteBuffer body = DirectBufferPool.acquire(poolType, 100);

        if (body == null || body.capacity() < bodySize) {
            if (body != null) DirectBufferPool.release(body);
            body = ByteBuffer.allocate(bodySize);
        }

        try {
            while (body.position() < bodySize - typeLen) {
                channel.read(body);
            }
            body.flip();

            byte[] jsonBytes = new byte[jsonLen];
            body.get(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);

            logJsonString(json, className);

            return deserializeByClassName(json, className);
        } finally {
            DirectBufferPool.release(body);
        }
    }

    public static void writeNIO(SocketChannel channel, Communication comm) throws IOException {
        ByteBuffer buffer = toNioBuffer(comm, 100);
        if (buffer == null) return;

        //Logger.logInfo("Tamaño del paquete "+formatHumanReadable(buffer.capacity()));
        try {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } finally {
            if (buffer.isDirect()) {
                DirectBufferPool.release(buffer);
            }
        }
    }

    public static ByteBuffer toNioBuffer(Communication comm, long timeout) throws IOException {
        if (comm instanceof RsyncDeltaPackage deltaPkg) {
            return serializeRsyncDeltaDirect(deltaPkg, timeout);
        }

        String json = gson.toJson(comm);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] typeBytes = comm.getCommunicationId().getBytes(StandardCharsets.UTF_8);

        int totalSize = 4 + 4 + typeBytes.length + jsonBytes.length;
        DirectBufferPool.BufferType targetPool = getPoolForClassName(comm.getCommunicationId());

        ByteBuffer buffer = DirectBufferPool.acquire(targetPool, timeout);
        if (buffer == null || buffer.capacity() < totalSize) {
            if (buffer != null) DirectBufferPool.release(buffer);

            ByteBuffer fallback = ByteBuffer.allocate(totalSize);
            fillBuffer(fallback, jsonBytes, typeBytes);
            return fallback;
        }

        fillBuffer(buffer, jsonBytes, typeBytes);
        return buffer;
    }

    // --- MÉTODOS AUXILIARES DE SERIALIZACIÓN Y DESERIALIZACIÓN BINARIA ---

    private static ByteBuffer serializeRsyncDeltaDirect(RsyncDeltaPackage deltaPkg, long timeout) throws IOException {
        byte[] typeBytes = deltaPkg.getCommunicationId().getBytes(StandardCharsets.UTF_8);

        // Estructura: 8 bytes (longitud del archivo) + 4 bytes (tamaño de lista) + iteración de elementos
        int payloadSize = 8 + 4;
        for (var inst : deltaPkg.getInstructions()) {
            if (inst.isLiteral()) {
                payloadSize += 1 + 4 + inst.getLiteralData().length;
            } else {
                payloadSize += 1 + 4;
            }
        }

        int totalSize = 4 + 4 + typeBytes.length + payloadSize;
        DirectBufferPool.BufferType targetPool = getPoolForClassName(deltaPkg.getCommunicationId());

        ByteBuffer buffer = DirectBufferPool.acquire(targetPool, timeout);
        if (buffer == null || buffer.capacity() < totalSize) {
            if (buffer != null) DirectBufferPool.release(buffer);
            buffer = ByteBuffer.allocate(totalSize);
        }

        buffer.clear();
        buffer.putInt(payloadSize);
        buffer.putInt(typeBytes.length);
        buffer.put(typeBytes);

        // Payload de datos
        buffer.putLong(deltaPkg.getTotalFileSize()); // Mantenemos el metadato del tamaño lógico
        buffer.putInt(deltaPkg.getInstructions().size());
        for (var inst : deltaPkg.getInstructions()) {
            if (inst.isLiteral()) {
                buffer.put((byte) 1);
                byte[] rawBytes = inst.getLiteralData();
                buffer.putInt(rawBytes.length);
                buffer.put(rawBytes);
            } else {
                buffer.put((byte) 0);
                buffer.putInt(inst.getBlockIndex());
            }
        }

        buffer.flip();
        return buffer;
    }

    private static void writeRsyncDeltaStream(DataOutputStream out, RsyncDeltaPackage deltaPkg) throws IOException {
        byte[] typeBytes = deltaPkg.getCommunicationId().getBytes(StandardCharsets.UTF_8);

        int payloadSize = 8 + 4;
        for (var inst : deltaPkg.getInstructions()) {
            if (inst.isLiteral()) {
                payloadSize += 1 + 4 + inst.getLiteralData().length;
            } else {
                payloadSize += 1 + 4;
            }
        }

        out.writeInt(payloadSize);
        out.writeInt(typeBytes.length);
        out.write(typeBytes);

        out.writeLong(deltaPkg.getTotalFileSize());
        out.writeInt(deltaPkg.getInstructions().size());
        for (var inst : deltaPkg.getInstructions()) {
            if (inst.isLiteral()) {
                out.writeByte(1);
                byte[] rawBytes = inst.getLiteralData();
                out.writeInt(rawBytes.length);
                out.write(rawBytes);
            } else {
                out.writeByte(0);
                out.writeInt(inst.getBlockIndex());
            }
        }
        out.flush();
    }

    private static Communication deserializeRsyncDeltaDirect(SocketChannel channel, int payloadSize) throws IOException {
        ByteBuffer body = ByteBuffer.allocate(payloadSize);
        while (body.hasRemaining()) {
            if (channel.read(body) == -1) throw new IOException("Canal cerrado leyendo payload binario rsync");
        }
        body.flip();
        return deserializeRsyncDeltaFromBuffer(body, payloadSize);
    }

    private static Communication deserializeRsyncDeltaFromBuffer(ByteBuffer buffer, int payloadSize) throws IOException {
        long totalFileSize = buffer.getLong();
        int totalInstructions = buffer.getInt();
        List<RsyncDeltaInstruction> instructions = new ArrayList<>(totalInstructions);

        for (int k = 0; k < totalInstructions; k++) {
            byte flag = buffer.get();
            if (flag == 1) {
                int len = buffer.getInt();
                byte[] rawBytes = new byte[len];
                buffer.get(rawBytes);
                instructions.add(new RsyncDeltaInstruction(rawBytes));
            } else if (flag == 0) {
                int blockIdx = buffer.getInt();
                instructions.add(new RsyncDeltaInstruction(blockIdx));
            } else {
                throw new IOException("Protocol Desync binario en Buffer: Flag desconocido " + flag);
            }
        }
        return new RsyncDeltaPackage(instructions, totalFileSize);
    }

    private static Communication deserializeRsyncDeltaFromStream(DataInputStream in, int payloadSize) throws IOException {
        long totalFileSize = in.readLong();
        int totalInstructions = in.readInt();
        List<org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaInstruction> instructions = new ArrayList<>(totalInstructions);

        for (int k = 0; k < totalInstructions; k++) {
            byte flag = in.readByte();
            if (flag == 1) {
                int len = in.readInt();
                byte[] rawBytes = new byte[len];
                in.readFully(rawBytes);
                instructions.add(new org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaInstruction(rawBytes));
            } else if (flag == 0) {
                int blockIdx = in.readInt();
                instructions.add(new org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaInstruction(blockIdx));
            } else {
                throw new IOException("Protocol Desync binario en Stream: Flag desconocido " + flag);
            }
        }
        return new org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaPackage(instructions, totalFileSize);
    }

    private static void fillBuffer(ByteBuffer buffer, byte[] json, byte[] type) {
        buffer.clear();
        int totalNeeded = 4 + 4 + type.length + json.length;

        if (buffer.capacity() < totalNeeded) {
            throw new RuntimeException("Buffer insuficiente. Capacidad: " + buffer.capacity() + " | Necesario: " + totalNeeded);
        }

        buffer.putInt(json.length);
        buffer.putInt(type.length);
        buffer.put(type);
        buffer.put(json);

        buffer.flip();
    }

    private static Communication deserializeByClassName(String json, String className) throws IOException {
        String fixedClassName = switch (className.toUpperCase()) {
            case "MESSAGE" -> "Mensaje";
            case "ACK" -> "MessageAck";
            case "HANDSHAKE" -> "FileHandshakeCommunication";
            default -> className;
        };

        Class<?> clazz = dynamicClassRegistry.get(className);
        if (clazz == null) {
            Logger.logError("[PROTOCOL DESYNC] No se encontró la clase '" + fixedClassName + "' en ninguna subcarpeta.");
            throw new IOException("Fallo de deserialización: " + fixedClassName);
        }

        try {
            return (Communication) gson.fromJson(json, clazz);
        } catch (Exception e) {
            throw new IOException("Error deserializando clase [" + fixedClassName + "]: " + e.getMessage());
        }
    }

    public static DirectBufferPool.BufferType getPoolForClassName(String className) {
        String fixedClassName = switch (className.toUpperCase()) {
            case "MESSAGE" -> "Mensaje";
            case "ACK" -> "MessageAck";
            case "HANDSHAKE" -> "FileHandshakeCommunication";
            default -> className;
        };

        Class<?> clazz = dynamicClassRegistry.get(fixedClassName);
        if (clazz == null) return DirectBufferPool.BufferType.MESSAGE;

        if (clazz.isAnnotationPresent(BufferPoolMapping.class)) {
            BufferPoolMapping mapping = clazz.getAnnotation(BufferPoolMapping.class);
            return mapping.value();
        }
        return DirectBufferPool.BufferType.MESSAGE;
    }

    public static byte[] readHandshakePacket(BitBridgeClient client) throws IOException {
        ReadableByteChannel channel = client.getReadableChannel();

        ByteBuffer header = ByteBuffer.allocate(8);
        while (header.hasRemaining()) {
            int read = channel.read(header);
            if (read == -1) throw new IOException("Conexión cerrada durante lectura de header handshake");
        }
        header.flip();

        int jsonSize = header.getInt();
        int typeSize = header.getInt();

        if (jsonSize <= 0 || jsonSize > 1024 * 1024 * 1024) {
            Logger.logError("[NIO-SYNC] ¡Desfase de flujo detectado! Tamaño JSON inválido: " + jsonSize);
            throw new IOException("Protocol Desync: Invalid JSON size.");
        }

        ByteBuffer payload = ByteBuffer.allocate(typeSize + jsonSize);
        while (payload.hasRemaining()) {
            int read = channel.read(payload);
            if (read == -1) throw new IOException("Conexión cerrada durante lectura de payload handshake");
        }

        ByteBuffer fullPacket = ByteBuffer.allocate(8 + typeSize + jsonSize);
        header.rewind();
        fullPacket.put(header);
        payload.flip();
        fullPacket.put(payload);

        return fullPacket.array();
    }

    private static void logJsonString(String json, String className) {
        /*try {
            com.google.gson.JsonElement jsonElement = com.google.gson.JsonParser.parseString(json);
            String prettyJsonString = prettyGson.toJson(jsonElement);

            Logger.logInfo("\n📥 [PACKET SNIFFER] -> Clase: " + className +
                    "\n" + "─".repeat(50) +
                    "\n" + prettyJsonString +
                    "\n" + "─".repeat(50));
        } catch (Exception e) {
            Logger.logWarn("[ProtocolService] No se pudo formatear el JSON entrante para '" + className + "': " + e.getMessage());
        }*/
    }

    /**
     * Realiza una autopsia anatómica y dinámica de un paquete en bruto.
     * Se adapta dinámicamente al tamaño real del buffer y segmenta las capas
     * de control (Header JSON, Header Tipo, Payload Clase y Payload JSON).
     *
     * @param rawPacket El arreglo de bytes crudo capturado directamente del canal de red.
     * @param traceId   Identificador del contexto o hilo de ejecución.
     */

    public static void dumpTargetPacket(byte[] rawPacket, String traceId) {
        if (rawPacket == null) {
            Logger.logWarn("⚠️ [DYNAMIC SNIFFER] Paquete nulo recibido.");
            return;
        }

        int totalBytes = rawPacket.length;

        // --- LECTURA DINÁMICA DE CABECERAS (Defensa contra desbordamientos) ---
        Integer jsonLen = null;
        Integer typeLen = null;

        if (totalBytes >= 4) {
            jsonLen = ((rawPacket[0] & 0xFF) << 24) | ((rawPacket[1] & 0xFF) << 16) |
                    ((rawPacket[2] & 0xFF) << 8)  | (rawPacket[3] & 0xFF);
        }
        if (totalBytes >= 8) {
            typeLen = ((rawPacket[4] & 0xFF) << 24) | ((rawPacket[5] & 0xFF) << 16) |
                    ((rawPacket[6] & 0xFF) << 8)  | (rawPacket[7] & 0xFF);
        }

        // --- MAPEO DE FRONTERAS DINÁMICAS ---
        int finalHeaderB = Math.min(8, totalBytes);
        int finalClassPayload = (typeLen != null) ? Math.min(8 + typeLen, totalBytes) : finalHeaderB;
        int expectedTotal = (jsonLen != null && typeLen != null) ? (8 + jsonLen + typeLen) : -1;

        // --- RECONSTRUCCIÓN DE STRINGS SOBRE LA MARCHA ---
        String extractedClassName = "N/A";
        if (typeLen != null && totalBytes > 8) {
            int bytesToRead = Math.min(typeLen, totalBytes - 8);
            extractedClassName = new String(rawPacket, 8, bytesToRead, StandardCharsets.UTF_8).trim();
        }

        String extractedJsonSnippet = "N/A";
        if (typeLen != null && jsonLen != null && totalBytes > (8 + typeLen)) {
            int start = 8 + typeLen;
            int bytesToRead = Math.min(jsonLen, totalBytes - start);
            String rawJson = new String(rawPacket, start, bytesToRead, StandardCharsets.UTF_8);
            extractedJsonSnippet = rawJson.length() > 60 ? rawJson.substring(0, 57) + "..." : rawJson;
            //extractedJsonSnippet=rawJson.toString();
        }

        // --- CONSTRUCCIÓN DEL REPORTE VISUAL ---
        StringBuilder dump = new StringBuilder();
        dump.append("\n╔═════════════════════════════════════════════════════════════════════════╗\n");
        dump.append(String.format("║ 🛰️  BITBRIDGE LIVE TELEMETRY RADAR      │ Trace: %-22s ║\n", traceId));
        dump.append("╚═════════════════════════════════════════════════════════════════════════╝\n");

        // 📊 DATOS DINÁMICOS DEL PAQUETE EN BRUTO (Estilo Human Readable)


        // Análisis de integridad de la trama
        dump.append("\n  ───[ DIAGNÓSTICO DE LA PILA TCP ]───\n");
        if (expectedTotal == -1) {
            dump.append("  🚨 ESTATUS: CRÍTICO - El paquete no contiene suficientes bytes de cabecera.\n");
        } else if (totalBytes < expectedTotal) {
            dump.append(String.format("  ⚠️  ESTATUS: FRAGMENTADO - Faltan %s por llegar (Esperados: %s)\n",
                    formatHumanReadable(expectedTotal - totalBytes), formatHumanReadable(expectedTotal)));
        } else if (totalBytes > expectedTotal) {
            dump.append(String.format("  🚨 ESTATUS: OVERFLOW / BASURA - Sobran %s en el buffer (Esperados: %s)\n",
                    formatHumanReadable(totalBytes - expectedTotal), formatHumanReadable(expectedTotal)));
        } else {
            dump.append("  ✅ ESTATUS: PERFECTO - Trama íntegra y alineada correctamente.\n");
        }

        dump.append("\n  🧠 MAPA ANATÓMICO DE MEMORIA (BYTE-BY-BYTE SEGMENTATION):\n");
        dump.append("  ").append("─".repeat(73)).append("\n");
        dump.append("  OFFSET    │ HEXADECIMAL DATA LAYER                           │ ASCII TEXT LAYER\n");
        dump.append("  ").append("─".repeat(73)).append("\n");

        // Renderizado de la matriz de memoria
        for (int rowStart = 0; rowStart < totalBytes; rowStart += 16) {
            dump.append(String.format("  %08X │ ", rowStart));
            int rowEnd = Math.min(rowStart + 16, totalBytes);

            // Capa Hexadecimal
            for (int i = rowStart; i < rowStart + 16; i++) {
                if (i < rowEnd) {
                    int b = rawPacket[i] & 0xFF;
                    if (i < 4) {
                        dump.append(String.format("%02X* ", b));
                    } else if (i < 8) {
                        dump.append(String.format("%02X' ", b));
                    } else if (i < finalClassPayload) {
                        dump.append(String.format("%02X. ", b));
                    } else {
                        dump.append(String.format("%02X  ", b));
                    }
                } else {
                    dump.append("    ");
                }

                if (i == rowStart + 7) {
                    dump.append("│ ");
                }
            }

            dump.append("│ ");

            // Capa ASCII Dinámica
            for (int i = rowStart; i < rowEnd; i++) {
                byte b = rawPacket[i];
                if (b >= 32 && b < 127) {
                    dump.append((char) b);
                } else {
                    if (i < 4) dump.append("🟥");
                    else if (i < 8) dump.append("🟨");
                    else dump.append(".");
                }
            }
            dump.append("\n");
        }

        dump.append("  📊 TELEMETRÍA DE LA TRAMA:\n");
        dump.append(String.format("  ├── 📐 Tamaño en Red       : %s\n", formatHumanReadable(totalBytes)));
        dump.append(String.format("  ├── 🟥 [Header A] Vol. JSON: %s\n", (jsonLen != null) ? formatHumanReadable(jsonLen) : "INCOMPLETO"));
        dump.append(String.format("  ├── 🟨 [Header B] Vol. Tipo: %s\n", (typeLen != null) ? formatHumanReadable(typeLen) : "INCOMPLETO"));
        dump.append(String.format("  ├── 🏷️  Clase Identificada : [%s]\n", extractedClassName));
        dump.append(String.format("  └── 📄 Payload JSON        : %s\n", extractedJsonSnippet));
        dump.append("  ").append("─".repeat(73)).append("\n");
        dump.append("  Marcadores: XX* [Long. JSON] │ XX' [Long. Tipo] │ XX. [Identificador] │ XX [Cuerpo JSON]\n");

        Logger.logInfo(dump.toString());
    }

    /**
     * Convierte un tamaño en bytes a un string formateado y legible (-h).
     */
    private static String formatHumanReadable(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB (%d bytes)", bytes / Math.pow(1024, exp), pre, bytes);
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.2f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
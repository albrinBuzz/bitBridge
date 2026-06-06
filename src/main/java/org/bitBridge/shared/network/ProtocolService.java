package org.bitBridge.shared.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.*;
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

    // Paquete base común donde residen las clases de datos
// Cambia esto para que use "comunication" (con una sola m, como tu carpeta) y termine en ".model.basic."
    private static final String PACKET_PACKAGE = "org.bitBridge.shared.core.comunication.";
    private static final Map<String, Class<?>> dynamicClassRegistry = new HashMap<>();

    // Caché de clases para evitar penalizaciones de rendimiento por reflexión en el loop NIO
    private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    static {
        long startTime = System.currentTimeMillis();

        // Escaneamos el paquete raíz de comunicaciones de forma completamente recursiva
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages("org.bitBridge.shared.core.comunication")
                .scan()) {

            // Buscamos todas las clases que extiendan de tu clase base abstracta
            List<Class<?>> communicationSubclasses = scanResult
                    .getSubclasses(Communication.class.getName())
                    .loadClasses();

            for (Class<?> clazz : communicationSubclasses) {
                // El Key será el nombre simple (ej: "Mensaje", "RsyncDeltaPackage")
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
     * Escribe un objeto Communication en el stream usando el formato compatible:
     * [INT: Tamaño JSON] [INT: Tamaño Tipo] [BYTES: Nombre Clase] [BYTES: JSON]
     */
    public static void writeFormattedPayload(DataOutputStream out, Communication comm) throws IOException {
        String json = gson.toJson(comm);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] typeBytes = comm.getCommunicationId().getBytes(StandardCharsets.UTF_8);

        out.writeInt(jsonBytes.length);          // 4 bytes
        out.writeInt(typeBytes.length);          // 4 bytes simétricos
        out.write(typeBytes);                     // Nombre de la clase como bytes
        out.write(jsonBytes);                    // El JSON crudo
        out.flush();
    }

    /**
     * Lee y reconstruye el objeto desde el stream (Firma mantenida para compatibilidad)
     */
    public static Communication readFormattedPayload(DataInputStream in) throws IOException {
        int jsonLen = in.readInt();
        int typeLen = in.readInt();

        byte[] typeBytes = new byte[typeLen];
        in.readFully(typeBytes);
        String className = new String(typeBytes, StandardCharsets.UTF_8).trim();

        byte[] jsonBytes = new byte[jsonLen];
        in.readFully(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);

        logJsonString(json, className);
        return deserializeByClassName(json, className);
    }

    /**
     * LEER DESDE NIO: Reconstruye lo que viene de un SocketChannel o de un DataStream alternativo.
     */
    public static Communication fromBytes(byte[] data) throws IOException {
        //dumpTargetPacket(data, "THREAD-" + Thread.currentThread().getName());
        ByteBuffer buffer = ByteBuffer.wrap(data);

        if (buffer.remaining() < 8) throw new IOException("Paquete demasiado corto (falta longitud)");

        int jsonLen = buffer.getInt();
        int typeLen = buffer.getInt();

        if (buffer.remaining() < typeLen) throw new IOException("Paquete incompleto (falta identificador)");
        byte[] typeBytes = new byte[typeLen];
        buffer.get(typeBytes);
        String className = new String(typeBytes, StandardCharsets.UTF_8).trim();

        if (className.isEmpty()) {
            throw new IOException("Protocol Desync: Nombre de tipo vacío detectado.");
        }

        if (buffer.remaining() < jsonLen) throw new IOException("Paquete incompleto (faltan bytes de JSON)");
        byte[] jsonBytes = new byte[jsonLen];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);

        logJsonString(json, className);

        return deserializeByClassName(json, className);
    }

    /**
     * MÉTODOS DE RED NIO CONSERVADOS PARA RETROCOMPATIBILIDAD ABSOLUTA
     */

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

        // Obtención dinámica del pool según la clase
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

    /**
     * Deserializador Dinámico basado en Reflexión con Caché Atómica.
     */

    private static Communication deserializeByClassName(String json, String className) throws IOException {
        // Normalización de compatibilidad con clientes CLI antiguos
        String fixedClassName = switch (className.toUpperCase()) {
            case "MESSAGE" -> "Mensaje";
            case "ACK" -> "MessageAck";
            case "HANDSHAKE" -> "FileHandshakeCommunication";
            default -> className;
        };

        // Búsqueda directa O(1) en el mapa indexado por ClassGraph
        Class<?> clazz = dynamicClassRegistry.get(className);

        if (clazz == null) {
            Logger.logError("[PROTOCOL DESYNC] No se encontró la clase '" + fixedClassName +
                    "' en ninguna subcarpeta de comunicación indexada.");
            throw new IOException("Fallo de deserialización: " + fixedClassName);
        }

        try {
            return (Communication) gson.fromJson(json, clazz);
        } catch (Exception e) {
            throw new IOException("Error deserializando clase [" + fixedClassName + "]: " + e.getMessage());
        }
    }

    /**
     * Mapeo de pools off-heap 100% DINÁMICO guiado por anotaciones.
     * ¡Ya no requiere actualizar ningún switch manual!
     */
    public static DirectBufferPool.BufferType getPoolForClassName(String className) {
        // 1. Normalizamos el nombre por compatibilidad con clientes viejos
        String fixedClassName = switch (className.toUpperCase()) {
            case "MESSAGE" -> "Mensaje";
            case "ACK" -> "MessageAck";
            case "HANDSHAKE" -> "FileHandshakeCommunication";
            default -> className;
        };

        // 2. Buscamos la clase real en nuestro registro dinámico indexado por ClassGraph
        Class<?> clazz = dynamicClassRegistry.get(fixedClassName);

        if (clazz == null) {
            // Si de verdad no existe la clase, aplicamos el fallback seguro
            return DirectBufferPool.BufferType.MESSAGE;
        }

        // 3. Extraemos la anotación directamente del objeto Class real
        if (clazz.isAnnotationPresent(BufferPoolMapping.class)) {
            BufferPoolMapping mapping = clazz.getAnnotation(BufferPoolMapping.class);
            //Logger.logInfo(clazz.getSimpleName());
            return mapping.value(); // Retorna MESSAGE, DIRECTORY o TRANSFER perfectamente
        }

        // Fallback si la clase existe pero olvidaste ponerle la anotación
        return DirectBufferPool.BufferType.MESSAGE;
    }



    private static void logJsonString(String json, String className) {
        /*try {
            // Parseamos el JSON crudo a un elemento genérico de Gson para formatearlo limpiamente
            com.google.gson.JsonElement jsonElement = com.google.gson.JsonParser.parseString(json);
            String prettyJsonString = prettyGson.toJson(jsonElement);

            Logger.logInfo("\n📥 [PACKET SNIFFER] -> Clase: " + className +
                    "\n" + "─".repeat(50) +
                    "\n" + prettyJsonString +
                    "\n" + "─".repeat(50));
        } catch (Exception e) {
            // Fallback defensivo si el JSON viene corrupto o incompleto para no tirar la lectura de red
            Logger.logWarn("[ProtocolService] No se pudo formatear el JSON entrante para '" + className + "': " + e.getMessage());
        }*/
    }

    /**
     * Mapeo heredado e inalterado para métodos externos heredados que aún invoquen este registro manual.
     * @deprecated El sistema ahora autodesubre los tipos dinámicamente mediante el nombre de la clase.
     */
    /*@Deprecated
    public static void registerType(CommunicationType type, Class<? extends Communication> clazz) {
        // Mantenido únicamente por firmas de compatibilidad binaria externa.
    }*/

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
}
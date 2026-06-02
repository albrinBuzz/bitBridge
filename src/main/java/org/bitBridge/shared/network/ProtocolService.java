package org.bitBridge.shared.network;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.json.Json;
import com.google.gson.Gson;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.sync.RsyncDeltaPackage;
import org.bitBridge.shared.core.comunication.sync.RsyncSignatures;
import org.bitBridge.shared.memory.DirectBufferPool;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProtocolService {
    private static final Gson gson = new Gson();

    private static final Map<CommunicationType, Class<? extends Communication>> typeRegistry = new EnumMap<>(CommunicationType.class);

    static {
        typeRegistry.put(CommunicationType.MESSAGE, Mensaje.class);
        typeRegistry.put(CommunicationType.FILE, FileDirectoryCommunication.class);
        typeRegistry.put(CommunicationType.DIRECTORY, FileDirectoryCommunication.class);
        typeRegistry.put(CommunicationType.UPDATE, ClientListMessage.class);
        typeRegistry.put(CommunicationType.NOTIFICATION, FileHandshakeCommunication.class);
        typeRegistry.put(CommunicationType.ACK, MessageAck.class);
        typeRegistry.put(CommunicationType.DIRECTORY_QUERY, DirectoryQuery.class);
        typeRegistry.put(CommunicationType.DIRECTORY_QUERY_RESULT, DirectoryQueryResponse.class);
        typeRegistry.put(CommunicationType.FILE_PULL_REQUEST, FilePullRequest.class);

        typeRegistry.put(CommunicationType.RSYNC_SIGNATURES, RsyncSignatures.class);
        typeRegistry.put(CommunicationType.RSYNC_DELTAS, RsyncDeltaPackage.class);
    }

    /**
     * Escribe un objeto Communication en el stream usando el formato:
     * [INT: Tamaño] [UTF: Tipo] [BYTES: JSON]
     */
    public static void writeFormattedPayload(DataOutputStream out, Communication comm) throws IOException {
        String json = gson.toJson(comm);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);

        out.writeInt(payload.length);          // 4 bytes
        out.writeUTF(comm.getType().name());    // Nombre del Enum
        out.write(payload);                     // El JSON crudo
        out.flush();
    }

    /**
     * Permite registrar nuevos tipos desde cualquier parte del proyecto
     * (Incluso desde módulos externos o plugins)
     */
    public static void registerType(CommunicationType type, Class<? extends Communication> clazz) {
        typeRegistry.put(type, clazz);
    }

    /**
     * Lee y reconstruye el objeto desde el stream
     */
    public static Communication readFormattedPayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        String typeStr = in.readUTF();
        CommunicationType type = CommunicationType.valueOf(typeStr);

        byte[] payload = new byte[length];
        in.readFully(payload);
        String json = new String(payload, StandardCharsets.UTF_8);
        logJsonString(json, type);
        try {
            return switch (type) {
                case MESSAGE -> gson.fromJson(json, Mensaje.class);
                case FILE, DIRECTORY -> gson.fromJson(json, FileDirectoryCommunication.class);
                case UPDATE -> gson.fromJson(json, ClientListMessage.class);
                case NOTIFICATION -> gson.fromJson(json, FileHandshakeCommunication.class);

                case RSYNC_SIGNATURES -> gson.fromJson(json, RsyncSignatures.class);
                case RSYNC_DELTAS -> gson.fromJson(json, RsyncDeltaPackage.class);
                // Si el tipo no coincide, devolvemos la clase base para evitar nulls
                default -> gson.fromJson(json, Communication.class);
            };
        } catch (Exception e) {
            // Si el JSON estaba mal formado para esa clase específica
            throw new IOException("Error haciendo casting de JSON a " + type + ": " + e.getMessage());
        }
    }
    /**
     * LEER DESDE NIO: Reconstruye lo que viene de un SocketChannel o de un DataStream
     */
    public static Communication fromBytes(byte[] data) throws IOException {
        //dumpTargetPacket(data, "[FROM-BYTES-INTERCEPT]");
        //Logger.logInfo();
        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 1. Leer la longitud del JSON (4 bytes - Equivale a in.readInt())
        if (buffer.remaining() < 8) throw new IOException("Paquete demasiado corto (falta longitud)");

        int payloadLen = buffer.getInt();

        // 2. Leer la longitud del tipo (2 bytes - Equivale al prefijo de readUTF())
        if (buffer.remaining() < 2) throw new IOException("Paquete corrupto (falta longitud de tipo)");
        int typeLen = buffer.getInt();

        // 3. Leer el nombre del tipo
        if (buffer.remaining() < typeLen) throw new IOException("Paquete incompleto (falta nombre de tipo)");
        byte[] typeBytes = new byte[typeLen];
        buffer.get(typeBytes);
        // ProtocolService.java
        String typeStr = new String(typeBytes, StandardCharsets.UTF_8).trim();
       // Logger.logInfo("Tipo de comunicacion [" + typeStr + "]");

        if (typeStr.isEmpty()) {
            throw new IOException("Protocol Desync: Nombre de tipo vacío detectado.");
        }

        CommunicationType type;
        try {
            type = CommunicationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IOException("Protocol Desync: Tipo desconocido [" + typeStr + "]");
        }

        // 4. Leer el JSON usando el payloadLen obtenido al inicio
        // Usamos payloadLen para ser exactos, aunque buffer.remaining() debería coincidir
        if (buffer.remaining() < payloadLen) throw new IOException("Paquete incompleto (faltan bytes de JSON)");
        byte[] jsonBytes = new byte[payloadLen];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        logJsonString(json, type);

        return deserializeByType(json, type);
    }

    public static byte[] readHandshakePacket(BitBridgeClient client) throws IOException {
        ReadableByteChannel channel = client.getReadableChannel();

        // 1. Leer el Header (6 bytes: 4 para Int size, 2 para Short type)
        ByteBuffer header = ByteBuffer.allocate(8);
        while (header.hasRemaining()) {
            int read = channel.read(header);
            if (read == -1) throw new IOException("Conexión cerrada durante lectura de header");
        }
        header.flip();

        int jsonSize = header.getInt();
        int typeSize = header.getInt();

        // 2. VALIDACIÓN CRÍTICA: Evitar el error "1145655877" (bytes de texto leídos como int)
        // Si el tamaño es mayor a 1MB para un JSON de control, algo anda mal
        if (jsonSize <= 0 || jsonSize > 10* 1024 * 1024) {
            Logger.logError("[NIO-SYNC] ¡Desfase de flujo detectado! Tamaño JSON inválido: " + jsonSize);
            throw new IOException("Protocol Desync: Invalid JSON size.");
        }

        // 3. Leer el Payload exacto
        // Importante: No creamos un buffer con el header de nuevo, ya lo procesamos
        // En ProtocolService.java dentro de readHandshakePacket

        ByteBuffer payload = ByteBuffer.allocate(typeSize + jsonSize);
        while (payload.hasRemaining()) {
            int read = channel.read(payload);
            if (read == -1) throw new IOException("Conexión cerrada durante lectura de payload");
        }

        // 4. Reconstruir el paquete completo para ProtocolService.fromBytes
        // ProtocolService espera: [4 bytes size][2 bytes typeSize][Bytes...]
        ByteBuffer fullPacket = ByteBuffer.allocate(8 + typeSize + jsonSize);
        header.rewind();
        fullPacket.put(header);
        payload.flip();
        fullPacket.put(payload);

        return fullPacket.array();
    }


    public static Communication readNIO(SocketChannel channel) throws IOException {
        // 1. Leer el Header (6 bytes: 4 para JSON + 2 para Tipo)
        ByteBuffer header = ByteBuffer.allocate(8);
        while (header.hasRemaining()) {
            if (channel.read(header) == -1) throw new IOException("Canal cerrado");
        }
        header.flip();

        int jsonLen = header.getInt();
        int typeLen = header.getInt();
        int bodySize = typeLen + jsonLen;

        // 2. Leer el cuerpo completo (Tipo + JSON)
        // Creamos un buffer con el tamaño exacto del contenido faltante
        ByteBuffer typeNameBuffer = ByteBuffer.allocate(typeLen);
        while (typeNameBuffer.hasRemaining()) {
            channel.read(typeNameBuffer);
        }
        String typeStr = new String(typeNameBuffer.array(), StandardCharsets.UTF_8);
        CommunicationType type = CommunicationType.valueOf(typeStr);

        // 3. OBTENER BUFFER DEL POOL ESPECIALIZADO
        DirectBufferPool.BufferType poolType = getPoolForType(type);
        ByteBuffer body = DirectBufferPool.acquire(poolType, 100);

        // Si el mensaje es más grande que el buffer del pool (ej. un directorio gigante),
        // usamos heap para no crashear
        if (body == null || body.capacity() < bodySize) {
            if (body != null) DirectBufferPool.release(body);
            body = ByteBuffer.allocate(bodySize);
        }

        try {
            // 4. Leer el JSON restante (el type ya lo leímos arriba)
            ByteBuffer jsonPart = body.duplicate();
            jsonPart.limit(jsonLen); // Solo leemos lo que falta

            while (body.position() < bodySize - typeLen) { // Ajustar según tu protocolo exacto
                channel.read(body);
            }
            body.flip();

            byte[] jsonBytes = new byte[jsonLen];
            body.get(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            logJsonString(json, type);

            return deserializeByType(json, type);
        } finally {
            DirectBufferPool.release(body);
        }
    }

    private static Communication deserializeByType(String json, CommunicationType type) throws IOException {
        try {
            Class<? extends Communication> clazz = typeRegistry.get(type);

            if (clazz == null) {
                Logger.logWarn("Tipo desconocido: " + type + ". Usando clase base Communication.");
                return gson.fromJson(json, Communication.class);
            }

            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            throw new IOException("Error deserializando " + type + ": " + e.getMessage());
        }
    }

    /*private static Communication deserializeByType(String json, CommunicationType type) throws IOException {
        try {
            return switch (type) {
                case MESSAGE -> gson.fromJson(json, Mensaje.class);
                case FILE, DIRECTORY -> gson.fromJson(json, FileDirectoryCommunication.class);
                case UPDATE -> gson.fromJson(json, ClientListMessage.class);
                case NOTIFICATION -> gson.fromJson(json, FileHandshakeCommunication.class);
                default -> gson.fromJson(json, Communication.class);
            };
        } catch (Exception e) {
            throw new IOException("Error en JSON para " + type + ": " + e.getMessage());
        }
    }*/

    /**
     * ESCRIBIR PARA NIO: Debe replicar exactamente el formato de DataOutputStream
     * [INT: Payload Len] [SHORT: Type Len] [BYTES: Type Name] [BYTES: JSON]
     */

    public static void writeNIO(SocketChannel channel, Communication comm) throws IOException {
        ByteBuffer buffer = toNioBuffer(comm, 100);
        if (buffer == null) return;

        try {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } finally {
            // Solo liberar si es DIRECTO (del pool).
            // Los de Heap los limpia el Garbage Collector solo.
            if (buffer.isDirect()) {
                DirectBufferPool.release(buffer);
            }
        }
    }

    public static ByteBuffer toNioBuffer(Communication comm, long timeout) throws IOException {
        String json = gson.toJson(comm);

        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] typeBytes = comm.getType().name().getBytes(StandardCharsets.UTF_8);

        // Cambia el 2 por un 4
        int totalSize = 4 + 4 + typeBytes.length + jsonBytes.length;
        DirectBufferPool.BufferType targetPool = getPoolForType(comm.getType());
        // 1. Si es más grande que el buffer del pool (8KB), usamos HEAP
        // El Heap no causa Memory Leaks nativos y es gestionado por el GC.
        /*if (totalSize > 8192) {
            //return null;
            ByteBuffer heapBuffer = ByteBuffer.allocate(totalSize);
            fillBuffer(heapBuffer, jsonBytes, typeBytes);
            return heapBuffer;
        }*/

        // 2. Uso del Pool solo para lo que realmente cabe
        ByteBuffer buffer = DirectBufferPool.acquire(targetPool, timeout);
        if (buffer == null || buffer.capacity() < totalSize) {
            // Si el pool nos dio un buffer pequeño pero el JSON creció de más, lo devolvemos
            if (buffer != null) DirectBufferPool.release(buffer);

            // Creamos uno en HEAP como red de seguridad
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

        // 2. Verificación de seguridad
        if (buffer.capacity() < totalNeeded) {
            throw new RuntimeException("Buffer insuficiente. Capacidad: " +
                    buffer.capacity() + " | Necesario: " + totalNeeded);
        }

        // 3. Escritura explícita y simétrica
        buffer.putInt(json.length);
        buffer.putInt(type.length);
        buffer.put(type);
        buffer.put(json);

        buffer.flip();
    }


    private static DirectBufferPool.BufferType getPoolForType(CommunicationType type) {
        return switch (type) {
            // Mensajes de control, ACKs y Notificaciones -> Pool Pequeño
            case MESSAGE, ACK, NOTIFICATION, UPDATE -> DirectBufferPool.BufferType.MESSAGE;

            // Listados de carpetas -> Pool Mediano
            case DIRECTORY, DIRECTORY_QUERY, DIRECTORY_QUERY_RESULT -> DirectBufferPool.BufferType.DIRECTORY;

            case FILE, FILE_PULL_REQUEST, RSYNC_SIGNATURES, RSYNC_DELTAS -> DirectBufferPool.BufferType.TRANSFER;


            default -> DirectBufferPool.BufferType.MESSAGE;
        };
    }

    private static final com.google.gson.Gson prettyGson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();


    // Pool de hilos para procesar JSON sin bloquear el canal NIO
    private static final ExecutorService asyncProcessor = Executors.newFixedThreadPool(2);
    /**
     * Toma el String JSON crudo que se acaba de leer del canal/stream,
     * lo formatea visualmente y lo manda al Logger.
     */
    private static void logJsonString(String json, CommunicationType type) {
        //Logger.logInfo(json);
        /*try {
            // Re-parseamos el string a un JsonElement genérico para que 'prettyGson' lo pueda indentar
            Object jsonElement = gson.fromJson(json, Object.class);
            String prettyJson = prettyGson.toJson(jsonElement);

            Logger.logInfo(String.format(
                    "\n▲=== [INCOMING JSON AUDIT: %s] ===▲\n%s\n▼==========================================▼",
                    type, prettyJson
            ));
        } catch (Exception e) {
            Logger.logError("[JSON-PRETTY] No se pudo formatear el JSON crudo: " + e.getMessage());
            // Fallback: Imprimir el JSON lineal si el formateo falla
            Logger.logInfo("[JSON-RAW-FALLBACK]: " + json);
        }*/
    }

    /**
     * Realiza un volcado de memoria (Hex/ASCII Dump) del paquete crudo en tránsito.
     * Ideal para interceptar desincronizaciones de protocolo directamente en el Log.
     * ▲=================== [ BITBRIDGE NETWORK SNIFFER INTERCEPT ] ===================▲
     *  TRACE ID: [DEBUG-READ-FILE_1512-85] | Longitud Total en Tránsito: 22 bytes
     * ─────────────────────────────────────────────────────────────────────────────────
     *  DIRECCIÓN HEXADECIMAL     | BINARIO / HEX DUMP            | REPRESENTACIÓN ASCII
     * ─────────────────────────────────────────────────────────────────────────────────
     *  [Offset: 0x0000]           00 00 00 0F 00 00 00 07  4D 45 53 53 41 47 45 7B  | ........MESSAGE{
     *  [Offset: 0x0010]           22 74 65 78 74 22 3A 22  48 6F 6C 61 22 7D        | "text":"Hola"}
     * ▼===============================================================================▼
     */


    public static void dumpTargetPacket(byte[] rawPacket, String traceId) {
        if (rawPacket == null || rawPacket.length == 0) {
            System.out.println(traceId + " -> [DUMP] Paquete vacio o nulo.");
            return;
        }

        // 1. Extraer metadatos del Header binario
        int jsonLen = 0;
        int typeLen = 0;
        String detectedType = "UNKNOWN";

        if (rawPacket.length >= 8) {
            jsonLen = ((rawPacket[0] & 0xFF) << 24) | ((rawPacket[1] & 0xFF) << 16) |
                    ((rawPacket[2] & 0xFF) << 8)  | (rawPacket[3] & 0xFF);
            typeLen = ((rawPacket[4] & 0xFF) << 24) | ((rawPacket[5] & 0xFF) << 16) |
                    ((rawPacket[6] & 0xFF) << 8)  | (rawPacket[7] & 0xFF);

            if (rawPacket.length >= 8 + typeLen && typeLen > 0) {
                detectedType = new String(rawPacket, 8, typeLen, StandardCharsets.UTF_8).trim();
            }
        }

        // Nombres base de las columnas
        String hOffset = "OFFSET";
        String hHex    = "HEX DUMP";
        String hAscii  = "REPRESENTACION ASCII";

        // Inicializar anchos mínimos basados en el tamaño del texto del Header
        int maxOffsetWidth = hOffset.length();
        int maxHexWidth    = hHex.length();
        int maxAsciiWidth  = hAscii.length();

        // Estructuras temporales para almacenar las filas procesadas en la primera pasada
        List<String> offsets = new ArrayList<>();
        List<String> hexDumps = new ArrayList<>();
        List<String> asciis   = new ArrayList<>();

        // --- PRIMERA PASADA: Calcular contenidos y medir anchos maximos ---
        for (int i = 0; i < rawPacket.length; i += 16) {
            // Generar Offset
            String offStr = String.format("0x%04X (%d)", i, i);
            offsets.add(offStr);
            if (offStr.length() > maxOffsetWidth) maxOffsetWidth = offStr.length();

            int remainingInRow = Math.min(16, rawPacket.length - i);

            // Generar Hex Dump
            StringBuilder hexSb = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                if (j < remainingInRow) {
                    hexSb.append(String.format("%02X ", rawPacket[i + j]));
                } else {
                    hexSb.append("   ");
                }
                if (j == 7) hexSb.append(" ");
            }
            String hexStr = hexSb.toString().trim();
            hexDumps.add(hexStr);
            if (hexStr.length() > maxHexWidth) maxHexWidth = hexStr.length();

            // Generar ASCII interpretado con delimitadores de cabecera
            StringBuilder asciiSb = new StringBuilder();
            for (int j = 0; j < remainingInRow; j++) {
                int currentPos = i + j;
                char c = (char) rawPacket[currentPos];

                if (currentPos == 8) asciiSb.append("[");
                if (currentPos == 8 + typeLen) asciiSb.append("]");

                if (c >= 32 && c <= 126) {
                    asciiSb.append(c);
                } else {
                    asciiSb.append(".");
                }
            }
            String asciiStr = asciiSb.toString();
            asciis.add(asciiStr);
            if (asciiStr.length() > maxAsciiWidth) maxAsciiWidth = asciiStr.length();
        }

        // --- SEGUNDA PASADA: Renderizado dinamico al estilo psql (Postgres) ---
        StringBuilder sb = new StringBuilder();

        // Formato dinámico para las filas de datos y cabeceras
        // Añadimos márgenes internos de 1 espacio a los lados de cada columna como hace Postgres
        String headerFormat = " %-" + maxOffsetWidth + "s | %-" + maxHexWidth + "s | %-" + maxAsciiWidth + "s\n";
        String rowFormat    = " %-" + maxOffsetWidth + "s | %-" + maxHexWidth + "s | %-" + maxAsciiWidth + "s\n";

        // Imprimir metadatos generales arriba de la tabla
        sb.append(String.format("\n-[ BITBRIDGE SNIFFER AUDIT ]--------------------------------------------------\n"));
        sb.append(String.format(" TRACE ID      : %s\n", traceId));
        sb.append(String.format(" PAYLOAD TOTAL : %d bytes\n", rawPacket.length));
        sb.append(String.format(" TIPO MENSAJE  : %s (Header: %d bytes)\n", detectedType, 8 + typeLen));
        sb.append(String.format(" JSON ESPERADO : %d bytes\n", jsonLen));
        sb.append("------------------------------------------------------------------------------\n\n");

        // 1. Escribir Header de la tabla
        sb.append(String.format(headerFormat, hOffset, hHex, hAscii));

        // 2. Escribir Línea Divisoria Dinámica de Postgres
        // Sumamos + 2 por los espacios iniciales/finales de cada columna y + 6 por los separadores " | "
        for (int k = 0; k < maxOffsetWidth + 2; k++) sb.append("-");
        sb.append("+");
        for (int k = 0; k < maxHexWidth + 2; k++) sb.append("-");
        sb.append("+");
        for (int k = 0; k < maxAsciiWidth + 2; k++) sb.append("-");
        sb.append("\n");

        // 3. Escribir el Cuerpo de Datos perfectamente alineado
        for (int i = 0; i < offsets.size(); i++) {
            sb.append(String.format(rowFormat, offsets.get(i), hexDumps.get(i), asciis.get(i)));
        }

        // Mostrar recuento final de filas al estilo psql
        sb.append(String.format("(%d filas)\n", offsets.size()));

        // Envío atómico directo al stdout estándar
        System.out.print(sb.toString());
        System.out.flush();
    }
}
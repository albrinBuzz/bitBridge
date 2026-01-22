package org.bitBridge.shared.network;


import com.google.gson.Gson;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.shared.*;
import org.bitBridge.shared.core.comunication.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

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

        try {
            return switch (type) {
                case MESSAGE -> gson.fromJson(json, Mensaje.class);
                case FILE, DIRECTORY -> gson.fromJson(json, FileDirectoryCommunication.class);
                case UPDATE -> gson.fromJson(json, ClientListMessage.class);
                case NOTIFICATION -> gson.fromJson(json, FileHandshakeCommunication.class);
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
        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 1. Leer la longitud del JSON (4 bytes - Equivale a in.readInt())
        if (buffer.remaining() < 4) throw new IOException("Paquete demasiado corto (falta longitud)");
        int payloadLen = buffer.getInt();

        // 2. Leer la longitud del tipo (2 bytes - Equivale al prefijo de readUTF())
        if (buffer.remaining() < 2) throw new IOException("Paquete corrupto (falta longitud de tipo)");
        short typeLen = buffer.getShort();

        // 3. Leer el nombre del tipo
        if (buffer.remaining() < typeLen) throw new IOException("Paquete incompleto (falta nombre de tipo)");
        byte[] typeBytes = new byte[typeLen];
        buffer.get(typeBytes);
        String typeStr = new String(typeBytes, StandardCharsets.UTF_8);
        CommunicationType type = CommunicationType.valueOf(typeStr);

        // 4. Leer el JSON usando el payloadLen obtenido al inicio
        // Usamos payloadLen para ser exactos, aunque buffer.remaining() debería coincidir
        if (buffer.remaining() < payloadLen) throw new IOException("Paquete incompleto (faltan bytes de JSON)");
        byte[] jsonBytes = new byte[payloadLen];
        buffer.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);

        return deserializeByType(json, type);
    }

    public static byte[] readHandshakePacket(BitBridgeClient client) throws IOException {
        ReadableByteChannel channel = client.getReadableChannel();

        // 1. Leer el Header (6 bytes: 4 para Int size, 2 para Short type)
        ByteBuffer header = ByteBuffer.allocate(6);
        while (header.hasRemaining()) {
            int read = channel.read(header);
            if (read == -1) throw new IOException("Conexión cerrada durante lectura de header");
        }
        header.flip();

        int jsonSize = header.getInt();
        short typeSize = header.getShort();

        // 2. VALIDACIÓN CRÍTICA: Evitar el error "1145655877" (bytes de texto leídos como int)
        // Si el tamaño es mayor a 1MB para un JSON de control, algo anda mal
        if (jsonSize <= 0 || jsonSize > 1024 * 1024) {
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
        ByteBuffer fullPacket = ByteBuffer.allocate(6 + typeSize + jsonSize);
        header.rewind();
        fullPacket.put(header);
        payload.flip();
        fullPacket.put(payload);

        return fullPacket.array();
    }


    public static Communication readNIO(SocketChannel channel) throws IOException {
        // 1. Leer el Header (6 bytes: 4 para JSON + 2 para Tipo)
        ByteBuffer header = ByteBuffer.allocate(6);
        while (header.hasRemaining()) {
            if (channel.read(header) == -1) throw new IOException("Canal cerrado");
        }
        header.flip();

        int jsonLen = header.getInt();
        short typeLen = header.getShort();

        // 2. Leer el cuerpo completo (Tipo + JSON)
        // Creamos un buffer con el tamaño exacto del contenido faltante
        ByteBuffer body = ByteBuffer.allocate(typeLen + jsonLen);
        while (body.hasRemaining()) {
            if (channel.read(body) == -1) throw new IOException("Canal interrumpido");
        }
        body.flip();

        // 3. Extraer el nombre del tipo
        byte[] typeBytes = new byte[typeLen];
        body.get(typeBytes);
        CommunicationType type = CommunicationType.valueOf(new String(typeBytes, StandardCharsets.UTF_8));

        // 4. Extraer el JSON
        byte[] jsonBytes = new byte[jsonLen];
        body.get(jsonBytes);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);

        return deserializeByType(json, type);
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
        String json = gson.toJson(comm);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] typeBytes = comm.getType().name().getBytes(StandardCharsets.UTF_8);

        // [INT: 4] + [SHORT: 2] + [TYPE: N] + [JSON: M]
        ByteBuffer buffer = ByteBuffer.allocate(6 + typeBytes.length + jsonBytes.length);

        buffer.putInt(jsonBytes.length);           // <--- Los primeros 4 bytes
        buffer.putShort((short) typeBytes.length); // <--- Los siguientes 2 bytes
        buffer.put(typeBytes);
        buffer.put(jsonBytes);

        buffer.flip();
        while(buffer.hasRemaining()) channel.write(buffer);
    }





}
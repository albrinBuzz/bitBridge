package org.bitBridge.Tests.nio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.memory.DirectBufferPool;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

public class ProtocolBinary {
    // Sustituimos GSON por Jackson MessagePack (Binario)
    private static final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

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
     * 1. ESCRIBIR PARA STREAMS (BLOQUEANTE/ANTIGUO)
     * [INT: Payload Len] [UTF: Tipo] [BYTES: MSGPACK]
     */
    public static void writeFormattedPayload(DataOutputStream out, Communication comm) throws IOException {
        byte[] payload = mapper.writeValueAsBytes(comm);
        out.writeInt(payload.length);
        out.writeUTF(comm.getType().name());
        out.write(payload);
        out.flush();
    }

    /**
     * 2. LEER DESDE STREAMS (BLOQUEANTE/ANTIGUO)
     */
    public static Communication readFormattedPayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        String typeStr = in.readUTF();
        CommunicationType type = CommunicationType.valueOf(typeStr);

        byte[] payload = new byte[length];
        in.readFully(payload);

        return deserializeByType(ByteBuffer.wrap(payload), type);
    }

    /**
     * 3. ESCRIBIR PARA NIO (SOCKETCHANNEL)
     * Usa el Pool de buffers para evitar asignaciones en el Pentium.
     */
    public static void writeNIO(SocketChannel channel, Communication comm) throws IOException {
        byte[] payload = mapper.writeValueAsBytes(comm);
        byte[] typeBytes = comm.getType().name().getBytes(StandardCharsets.UTF_8);

        // [INT: 4] + [SHORT: 2] + [TYPE: N] + [PAYLOAD: M]
        ByteBuffer buffer = DirectBufferPool.acquire(50);
        if (buffer == null) {
            return;
            //buffer = ByteBuffer.allocateDirect(6 + typeBytes.length + payload.length);
        }

        buffer.clear();
        buffer.putInt(payload.length);
        buffer.putShort((short) typeBytes.length);
        buffer.put(typeBytes);
        buffer.put(payload);
        buffer.flip();

        while (buffer.hasRemaining()) channel.write(buffer);
        // DirectBufferPool.release(buffer); // Liberar según tu implementación del pool
    }

    /**
     * 4. LEER DESDE NIO (SOCKETCHANNEL)
     */
    public static Communication readNIO(SocketChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(6);
        while (header.hasRemaining()) {
            if (channel.read(header) == -1) throw new IOException("Canal cerrado");
        }
        header.flip();

        int payloadLen = header.getInt();
        short typeLen = header.getShort();

        ByteBuffer body = ByteBuffer.allocate(typeLen + payloadLen);
        while (body.hasRemaining()) {
            if (channel.read(body) == -1) throw new IOException("Canal interrumpido");
        }
        body.flip();

        byte[] typeBytes = new byte[typeLen];
        body.get(typeBytes);
        CommunicationType type = CommunicationType.valueOf(new String(typeBytes, StandardCharsets.UTF_8));

        return deserializeByType(body, type);
    }

    /**
     * 5. HANDSHAKE OPTIMIZADO PARA NIO
     */
    public static byte[] readHandshakePacket(BitBridgeClient client) throws IOException {
        ReadableByteChannel channel = client.getReadableChannel();

        ByteBuffer header = ByteBuffer.allocate(6);
        while (header.hasRemaining()) {
            if (channel.read(header) == -1) throw new IOException("Error en handshake");
        }
        header.flip();

        int payloadLen = header.getInt();
        short typeLen = header.getShort();

        // Validación de seguridad para el Pentium
        if (payloadLen <= 0 || payloadLen > 1024 * 1024) throw new IOException("Payload inválido");

        ByteBuffer payload = ByteBuffer.allocate(typeLen + payloadLen);
        while (payload.hasRemaining()) {
            if (channel.read(payload) == -1) throw new IOException("Error leyendo handshake");
        }

        ByteBuffer fullPacket = ByteBuffer.allocate(6 + typeLen + payloadLen);
        header.rewind();
        fullPacket.put(header);
        payload.flip();
        fullPacket.put(payload);

        return fullPacket.array();
    }

    /**
     * 6. RECONSTRUIR DESDE BYTES (NIO/DMI)
     */
    public static Communication fromBytes(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int payloadLen = buffer.getInt();
        short typeLen = buffer.getShort();

        byte[] typeBytes = new byte[typeLen];
        buffer.get(typeBytes);
        CommunicationType type = CommunicationType.valueOf(new String(typeBytes, StandardCharsets.UTF_8));

        return deserializeByType(buffer, type);
    }

    /**
     * 7. MÉTODO CORE: DESERIALIZACIÓN BINARIA
     */
    private static Communication deserializeByType(ByteBuffer body, CommunicationType type) throws IOException {
        try {
            Class<? extends Communication> clazz = typeRegistry.get(type);
            if (clazz == null) clazz = Communication.class;

            // Extraemos los bytes restantes del buffer (el payload binario)
            byte[] binaryData = new byte[body.remaining()];
            body.get(binaryData);

            return mapper.readValue(binaryData, clazz);
        } catch (Exception e) {
            throw new IOException("Error binarizando " + type + ": " + e.getMessage());
        }
    }

    /**
     * 8. CONVERTIR A BUFFER PARA ENVÍO ASÍNCRONO
     */
    public static ByteBuffer toNioBuffer(Communication comm, long timeout) throws IOException {
        byte[] payload = mapper.writeValueAsBytes(comm);
        byte[] typeBytes = comm.getType().name().getBytes(StandardCharsets.UTF_8);

        int totalSize = 4 + 2 + typeBytes.length + payload.length;

        ByteBuffer buffer;
        if (totalSize > 65536) {
            buffer = ByteBuffer.allocateDirect(totalSize);
            //return null;
        } else {
            buffer = DirectBufferPool.acquire(timeout);
            if (buffer == null) return null;
        }

        fillBuffer(buffer, payload, typeBytes);
        return buffer;
    }

    private static void fillBuffer(ByteBuffer buffer, byte[] json, byte[] type) {
        buffer.clear();
        // Verificación de seguridad extra antes del put
        if (buffer.remaining() < (4 + 2 + type.length + json.length)) {
            //return;
            throw new RuntimeException("Error crítico: El buffer es muy pequeño para los datos");
        }
        buffer.putInt(json.length);
        buffer.putShort((short) type.length);
        buffer.put(type);
        buffer.put(json);
        buffer.flip();
    }
    public static void registerType(CommunicationType type, Class<? extends Communication> clazz) {
        typeRegistry.put(type, clazz);
    }
}
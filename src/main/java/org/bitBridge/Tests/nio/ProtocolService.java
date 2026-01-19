package org.bitBridge.Tests.nio;

import com.google.gson.Gson;
import org.bitBridge.shared.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class ProtocolService {
    private static final Gson gson = new Gson();
    private static final int MAX_FRAME_SIZE = 1024 * 1024; // 1MB

    // --- IMPLEMENTACIÓN IO (La que ya tienes) ---
    public static void writeFormattedPayload(DataOutputStream out, Communication comm) throws IOException {
        String json = gson.toJson(comm);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length); // Cabecera: Tamaño
        out.write(data);           // Cuerpo: Datos
        out.flush();
    }

    public static Communication readFormattedPayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readFully(data);
        return deserialize(new String(data, StandardCharsets.UTF_8));
    }

    /*public static Communication fromBytes(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        return gson.fromJson(json, Communication.class);
    }*/

    // --- IMPLEMENTACIÓN NIO (Nueva) ---
    /**
     * Intenta leer un objeto completo de un ByteBuffer acumulador.
     * Retorna null si el mensaje está incompleto.
     */
    public static Communication readNextMessageNIO(ByteBuffer buffer) {
        buffer.flip(); // Modo lectura
        if (buffer.remaining() < 4) { // No hay ni para el entero de longitud
            buffer.compact();
            return null;
        }

        int messageLength = buffer.getInt();
        if (buffer.remaining() < messageLength) {
            // El mensaje no ha llegado completo, rebobinamos
            buffer.rewind();
            buffer.compact();
            return null;
        }

        byte[] data = new byte[messageLength];
        buffer.get(data);
        buffer.compact(); // Prepara para la siguiente lectura

        return deserialize(new String(data, StandardCharsets.UTF_8));
    }

    /*public static void writeNIO(SocketChannel channel, Communication comm) throws IOException {
        String json = gson.toJson(comm);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(4 + data.length);
        buffer.putInt(data.length);
        buffer.put(data);
        buffer.flip();

        while(buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }*/

    private static Communication deserialize(String json) {
        // Tu lógica actual de GSON para detectar el tipo de objeto
        return gson.fromJson(json, Communication.class);
    }

    public static void writeNIO(SocketChannel channel, Communication comm) throws IOException {
        ByteBuffer buffer;

        if (comm instanceof Mensaje m) {
            byte[] content = m.getContenido().getBytes(StandardCharsets.UTF_8);
            // 1 (tipo) + 4 (longitud) + contenido
            buffer = ByteBuffer.allocate(5 + content.length);
            buffer.put(CommunicationType.MESSAGE.id);
            buffer.putInt(content.length);
            buffer.put(content);
        }
        else if (comm instanceof FileHandshakeCommunication handshake) {
            byte[] sessionId = handshake.getSessionId().getBytes(StandardCharsets.UTF_8);
            // 1 (tipo) + 4 (longitud) + 1 (acción) + sesión
            buffer = ByteBuffer.allocate(6 + sessionId.length);
            buffer.put(CommunicationType.NOTIFICATION.id);
            buffer.putInt(sessionId.length + 1);
            buffer.put((byte) handshake.getAction().ordinal());
            buffer.put(sessionId);
        }
        else {
            // Si no está optimizado, podemos seguir usando JSON para tipos raros
            // pero con un prefijo especial (ej. ID 99)
            return;
        }

        buffer.flip();
        while(buffer.hasRemaining()) channel.write(buffer);
    }

    public static Communication fromBytes(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte typeId = buffer.get();
        int payloadLen = buffer.getInt();
        CommunicationType type = CommunicationType.fromId(typeId);

        return switch (type) {
            case MESSAGE -> {
                byte[] content = new byte[payloadLen];
                buffer.get(content);
                yield new Mensaje(new String(content, StandardCharsets.UTF_8), type);
            }
            case NOTIFICATION -> {
                int actionIdx = buffer.get();
                byte[] sessionBytes = new byte[payloadLen - 1];
                buffer.get(sessionBytes);
                yield new FileHandshakeCommunication(
                        FileHandshakeAction.values()[actionIdx],
                        new String(sessionBytes, StandardCharsets.UTF_8)
                );
            }
            default -> throw new IOException("Tipo binario no soportado");
        };
    }
}
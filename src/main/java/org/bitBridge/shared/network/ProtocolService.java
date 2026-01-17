package org.bitBridge.shared.network;


import com.google.gson.Gson;
import org.bitBridge.shared.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ProtocolService {
    private static final Gson gson = new Gson();

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
}
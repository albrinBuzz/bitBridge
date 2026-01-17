package org.bitBridge.shared;

import com.google.gson.Gson;
import org.bitBridge.shared.*;

import java.nio.charset.StandardCharsets;

public class UniversalCodec {
    private static final Gson gson = new Gson();

    // Convierte cualquier objeto Communication a JSON bytes
    public byte[] encode(Communication comm) {
        String json = gson.toJson(comm);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    // Primero leemos el tipo, luego el objeto completo
    public Communication decode(String json, CommunicationType type) {
        return switch (type) {
            case MESSAGE -> gson.fromJson(json, Mensaje.class);
            case FILE, DIRECTORY -> gson.fromJson(json, FileDirectoryCommunication.class);
            case UPDATE -> gson.fromJson(json, ClientListMessage.class);
            case NOTIFICATION -> gson.fromJson(json, FileHandshakeCommunication.class);
            default -> gson.fromJson(json, Communication.class);
        };
    }
}
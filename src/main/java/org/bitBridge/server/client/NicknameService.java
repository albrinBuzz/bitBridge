package org.bitBridge.server.client;


import java.util.List;

public class NicknameService {

    /**
     * Valida si un nick está disponible consultando al registro.
     */
    public boolean isAvailable(String nick, ClientRegistry registry) {
        if (nick == null || nick.trim().isEmpty()) return false;

        // Buscamos si ya existe un handler con ese nick en el registro
        return registry.findByNick(nick.trim()) == null;
    }

    /**
     * Genera un nick único si el deseado ya está ocupado.
     */
    public String generateUniqueNick(String desiredNick, ClientRegistry registry) {
        desiredNick = desiredNick.trim();

        if (isAvailable(desiredNick, registry)) {
            return desiredNick;
        }

        int suffix = 1;
        String candidate;
        do {
            candidate = desiredNick + suffix;
            suffix++;
        } while (!isAvailable(candidate, registry));

        return candidate;
    }
}
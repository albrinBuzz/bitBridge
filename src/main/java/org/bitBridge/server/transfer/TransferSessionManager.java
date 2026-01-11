package org.bitBridge.server.transfer;



import org.bitBridge.server.client.ClientHandler;
import org.bitBridge.shared.Logger;

import java.util.concurrent.*;

public class TransferSessionManager {
    // Movemos el mapa aquí
    private final ConcurrentHashMap<String, Exchanger<ClientHandler>> transferSessions = new ConcurrentHashMap<>();

    public boolean isTransferSession(String nick) {
        return nick != null && (nick.startsWith("SENDER_") ||
                nick.startsWith("FILE_") ||
                nick.startsWith("DIR_"));
    }

    public ClientHandler waitForReceptor(String sessionId, int timeoutSeconds) {
        Exchanger<ClientHandler> exchanger = new Exchanger<>();
        transferSessions.put(sessionId, exchanger);
        try {
            return exchanger.exchange(null, timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            Logger.logError("Timeout esperando receptor: " + sessionId);
            return null;
        } finally {
            transferSessions.remove(sessionId);
        }
    }

    public void registerReceptor(String sessionId, ClientHandler receptor) {
        Exchanger<ClientHandler> exchanger = transferSessions.get(sessionId);
        if (exchanger != null) {
            try {
                exchanger.exchange(receptor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
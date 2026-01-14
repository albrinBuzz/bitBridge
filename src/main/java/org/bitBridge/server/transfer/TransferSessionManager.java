package org.bitBridge.server.transfer;



import org.bitBridge.server.client.ClientHandler;
import org.bitBridge.shared.FileHandshakeAction;
import org.bitBridge.shared.FileHandshakeCommunication;
import org.bitBridge.shared.Logger;

import java.util.concurrent.*;

public class TransferSessionManager {
    // Movemos el mapa aquí
    private final ConcurrentHashMap<String, Exchanger<ClientHandler>> transferSessions = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, FileHandshakeCommunication>handshakeCom=new ConcurrentHashMap<>();
    // Promesas para la decisión del handshake (Aceptar/Rechazar)
    private final ConcurrentHashMap<String, CompletableFuture<FileHandshakeCommunication>> handshakeFutures = new ConcurrentHashMap<>();

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

    public void registerHandshek(String sessionId,FileHandshakeCommunication communication){
        Logger.logInfo(sessionId+" "+communication.getAction().name());
        handshakeCom.put(sessionId, communication);
    }
    public void registerHandshake(String sessionId, FileHandshakeCommunication communication) {
        Logger.logInfo("Registrando handshake para " + sessionId + ": " + communication.getAction());

        // Obtenemos el future existente o creamos uno nuevo si el receptor llegó antes que el emisor
        //CompletableFuture<FileHandshakeCommunication> future = handshakeFutures.computeIfAbsent(sessionId, k -> new CompletableFuture<>());

        // IMPORTANTE: Esto libera al hilo que está bloqueado en .get()
        //future.complete(communication);

        // Obtenemos la promesa y la completamos. Si no existe, la creamos ya completada.
        handshakeFutures.computeIfAbsent(sessionId, k -> new CompletableFuture<>())
                .complete(communication);
    }

    public FileHandshakeAction responseAction(String sessionId){
        Logger.logInfo(sessionId);
        var hand= handshakeCom.get(sessionId).getAction();
        Logger.logInfo(hand.name());
        return hand;
    }

    // --- LÓGICA DEL EMISOR ---

    /**
     * El emisor llama a esto y SE BLOQUEA hasta que el receptor responda o pase el tiempo
     */
    public FileHandshakeAction waitForResponseAction(String sessionId, int timeoutSeconds) {
        // Obtenemos o creamos la promesa para esta sesión
        CompletableFuture<FileHandshakeCommunication> future =
                handshakeFutures.computeIfAbsent(sessionId, k -> new CompletableFuture<>());

        try {
            Logger.logInfo("Esperando decisión del receptor para sesión: " + sessionId);
            // Bloquea aquí hasta que registerHandshake sea llamado
            FileHandshakeCommunication com = future.get(timeoutSeconds, TimeUnit.SECONDS);
            Logger.logInfo("Respuesta recibida para " + sessionId + ": " + com.getAction());
            return com.getAction();
        } catch (TimeoutException e) {
            Logger.logError("Timeout: El receptor no respondió al handshake en " + sessionId);
            return FileHandshakeAction.ERROR_TIMEOUT;
        } catch (InterruptedException | ExecutionException e) {
            Logger.logError("Error esperando respuesta de handshake: " + e.getMessage());
            return FileHandshakeAction.DECLINE_REQUEST;
        } finally {
            // Limpieza vital para evitar fugas de memoria
            handshakeFutures.remove(sessionId);
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
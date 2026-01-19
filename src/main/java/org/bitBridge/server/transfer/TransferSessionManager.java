package org.bitBridge.server.transfer;



import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.server.core.client.ClientHandler;
import org.bitBridge.shared.FileHandshakeAction;
import org.bitBridge.shared.FileHandshakeCommunication;
import org.bitBridge.shared.Logger;

import java.util.concurrent.*;

public class TransferSessionManager {
    // Movemos el mapa aquí
    private final ConcurrentHashMap<String, Exchanger<BitBridgeClient>> transferSessions = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, FileHandshakeCommunication>handshakeCom=new ConcurrentHashMap<>();
    // Promesas para la decisión del handshake (Aceptar/Rechazar)
    private final ConcurrentHashMap<String, CompletableFuture<FileHandshakeCommunication>> handshakeFutures = new ConcurrentHashMap<>();

    public boolean isTransferSession(String nick) {
        return nick != null && (nick.startsWith("SENDER_") ||
                nick.startsWith("FILE_") ||
                nick.startsWith("DIR_"));
    }

    public BitBridgeClient waitForReceptor(String sessionId, int timeoutSeconds) {
        Exchanger<BitBridgeClient> exchanger = new Exchanger<>();
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
        Logger.logInfo("[SESSION] Registrando handshake para " + sessionId + ": " + communication.getAction());

        // Usamos compute para manejar el caso donde el receptor llega antes que el emisor
        handshakeFutures.compute(sessionId, (id, existingFuture) -> {
            if (existingFuture == null) {
                // El receptor se adelantó al emisor. Creamos un future ya completado.
                CompletableFuture<FileHandshakeCommunication> f = new CompletableFuture<>();
                f.complete(communication);
                return f;
            } else {
                // El emisor ya estaba bloqueado en .get(). Lo liberamos.
                existingFuture.complete(communication);
                return existingFuture;
            }
        });
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
        // Si el receptor ya registró su handshake, esto nos devuelve el future completado.
        // Si no, crea uno nuevo donde nos bloquearemos.
        CompletableFuture<FileHandshakeCommunication> future =
                handshakeFutures.computeIfAbsent(sessionId, k -> new CompletableFuture<>());

        try {
            Logger.logInfo("[SESSION] Esperando decisión del receptor para sesión: " + sessionId);

            // Bloqueo controlado
            FileHandshakeCommunication com = future.get(timeoutSeconds, TimeUnit.SECONDS);

            Logger.logInfo("[SESSION] Respuesta recuperada para " + sessionId + ": " + com.getAction());
            return com.getAction();
        } catch (TimeoutException e) {
            Logger.logError("[SESSION] Timeout: Nadie respondió al handshake en " + sessionId);
            return FileHandshakeAction.ERROR_TIMEOUT;
        } catch (InterruptedException | ExecutionException e) {
            Logger.logError("[SESSION] Error esperando respuesta: " + e.getMessage());
            return FileHandshakeAction.DECLINE_REQUEST;
        } finally {
            // Limpiamos siempre para evitar memory leaks
            handshakeFutures.remove(sessionId);
        }
    }


    public void registerReceptor(String sessionId, BitBridgeClient receptor) {
        Logger.logInfo("[SESSION-MGR] Solicitud de registro para sesión: " + sessionId);

        // Intentamos obtener el exchanger. Si no existe aún, el receptor llegó
        // ligeramente antes de que el hilo del emisor creara la entrada.
        // Usamos un bucle pequeño de reintento o simplemente esperamos a que aparezca.
        long startTime = System.currentTimeMillis();
        Exchanger<BitBridgeClient> exchanger = null;

        while (exchanger == null && (System.currentTimeMillis() - startTime) < 2000) {
            exchanger = transferSessions.get(sessionId);
            if (exchanger == null) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        }

        if (exchanger != null) {
            try {
                Logger.logInfo("[SESSION-MGR] Entregando socket al hilo de transferencia...");
                exchanger.exchange(receptor, 2, TimeUnit.SECONDS);
                Logger.logInfo("[SESSION-MGR] Intercambio completado.");
            } catch (Exception e) {
                Logger.logError("[SESSION-MGR] Fallo en intercambio: " + e.getMessage());
            }
        } else {
            Logger.logError("[SESSION-MGR] Sesión no encontrada después de espera: " + sessionId);
        }
    }

    public void removeSession(String nick) {
        transferSessions.remove(nick);
    }
}
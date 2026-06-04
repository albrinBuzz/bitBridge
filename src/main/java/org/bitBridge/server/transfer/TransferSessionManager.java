package org.bitBridge.server.transfer;

import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.FileHandshakeAction;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;

import java.util.concurrent.*;

public class TransferSessionManager {
    // Sincronización limpia usando CompletableFuture para el canal de datos
    private final ConcurrentHashMap<String, CompletableFuture<BitBridgeClient>> dataChannels = new ConcurrentHashMap<>();

    // Control de Handshakes síncronos compartidos
    private final ConcurrentHashMap<String, CompletableFuture<FileHandshakeCommunication>> handshakeFutures = new ConcurrentHashMap<>();

    // Almacenamiento temporal para acciones rápidas (Con limpieza automatizada)
    private final ConcurrentHashMap<String, FileHandshakeCommunication> handshakeCom = new ConcurrentHashMap<>();

    public static final String PREFIX_REQUEST = "REQ.TX.";
    public static final String PREFIX_DATA    = "STR.DATA.";

    public boolean isTransferSession(String nick) {
        if (nick == null) return false;
        return nick.startsWith(PREFIX_REQUEST) || nick.startsWith(PREFIX_DATA);
    }

    /**
     * Hilo A (Control/Relay): Se bloquea esperando a que el Socket secundario se conecte.
     */
    public BitBridgeClient waitForReceptor(String sessionId, int timeoutSeconds) {
        // Obtenemos o creamos el futuro de forma completamente atómica
        CompletableFuture<BitBridgeClient> channelFuture = dataChannels.computeIfAbsent(sessionId, k -> new CompletableFuture<>());
        try {
            return channelFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            Logger.logError("[SESSION-MGR] Timeout esperando receptor en sesión: " + sessionId);
            return null;
        } catch (InterruptedException | ExecutionException e) {
            Logger.logError("[SESSION-MGR] Error en acoplamiento de sesión: " + sessionId + " -> " + e.getMessage());
            return null;
        } finally {
            // Limpieza inmediata para evitar fugas
            dataChannels.remove(sessionId);
        }
    }

    /**
     * Hilo B (Socket de Datos de Red): Llega de imprevisto y deposita el canal físico.
     * ¡Ya no hay bucles de espera activos ni sleeps!
     */
    public void registerReceptor(String sessionId, BitBridgeClient receptor) {
        CompletableFuture<BitBridgeClient> channelFuture = dataChannels.computeIfAbsent(sessionId, k -> new CompletableFuture<>());

        // Completar el futuro despierta inmediatamente al Hilo A en su instrucción .get()
        if (channelFuture.complete(receptor)) {
            Logger.logInfo("[SESSION-MGR] Matchmaking de sockets exitoso para: " + sessionId);
        } else {
            Logger.logError("[SESSION-MGR] Conflicto o duplicidad al registrar el receptor para: " + sessionId);
        }
    }

    /**
     * Registra la respuesta del handshake de control de forma segura.
     */
    public void registerHandshake(String sessionId, FileHandshakeCommunication communication) {
        // Guardamos también en el mapa de consulta directa por si se requiere compatibilidad analítica rápida
        handshakeCom.put(sessionId, communication);

        CompletableFuture<FileHandshakeCommunication> future =
                handshakeFutures.computeIfAbsent(sessionId, k -> new CompletableFuture<>());

        future.complete(communication);
    }

    /**
     * Obtiene de forma segura el estado de una acción.
     */
    public FileHandshakeAction responseAction(String sessionId) {
        FileHandshakeCommunication hand = handshakeCom.get(sessionId);
        return hand != null ? hand.getAction() : FileHandshakeAction.DECLINE_REQUEST;
    }

    /**
     * Bloquea el hilo de control esperando la decisión del receptor remoto.
     */
    public FileHandshakeAction waitForResponseAction(String sessionId, int timeoutSeconds) {
        CompletableFuture<FileHandshakeCommunication> future =
                handshakeFutures.computeIfAbsent(sessionId, k -> new CompletableFuture<>());
        try {
            FileHandshakeCommunication com = future.get(timeoutSeconds, TimeUnit.SECONDS);
            return com.getAction();
        } catch (TimeoutException e) {
            Logger.logError("[SESSION] Timeout: Nadie respondió al handshake en " + sessionId);
            return FileHandshakeAction.ERROR_TIMEOUT;
        } catch (InterruptedException | ExecutionException e) {
            Logger.logError("[SESSION] Error esperando respuesta en: " + sessionId + " -> " + e.getMessage());
            return FileHandshakeAction.DECLINE_REQUEST;
        } finally {
            // Limpieza atómica total de estructuras compuestas para la sesión dada
            handshakeFutures.remove(sessionId);
            handshakeCom.remove(sessionId);
        }
    }

    /**
     * Limpieza explícita si la sesión se cancela o aborta abruptamente.
     */
    public void removeSession(String sessionId) {
        if (sessionId == null) return;
        dataChannels.remove(sessionId);
        handshakeFutures.remove(sessionId);
        handshakeCom.remove(sessionId);
    }
}
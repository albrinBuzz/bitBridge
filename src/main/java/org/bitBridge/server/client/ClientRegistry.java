package org.bitBridge.server.client;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.shared.Logger;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ClientRegistry {

    // Fuente de verdad para la lógica de red (Broadcast, Mensajes)
    private final List<ClientHandler> activeHandlers = new CopyOnWriteArrayList<>();

    // Fuente de verdad para datos e información de UI
    private final Map<Socket, ClientInfo> socketToInfoMap = new ConcurrentHashMap<>();

    /**
     * Registra un nuevo cliente en el sistema.
     */
    public void addClient(ClientHandler handler, ClientInfo info) {
        activeHandlers.add(handler);
        socketToInfoMap.put(handler.getClientSocket(), info);
    }

    /**
     * Elimina un cliente y limpia todos sus registros asociados.
     */
    public void removeClient(ClientHandler handler) {
        if (handler != null) {
            activeHandlers.remove(handler);
            socketToInfoMap.remove(handler.getClientSocket());
        }

    }

    /**
     * Busca un ClientHandler específico por su nombre de usuario.
     */
    public ClientHandler findByNick(String nick) {
        if (nick == null) return null;
        return activeHandlers.stream()
                .filter(h -> nick.equalsIgnoreCase(h.nick))
                .findFirst()
                .orElse(null);
    }

    /**
     * Retorna una copia de la lista de ClientInfos para enviar a los clientes (Broadcast Update).
     */
    public List<ClientInfo> getAllClientInfos() {
        return new ArrayList<>(socketToInfoMap.values());
    }

    /**
     * Retorna todos los handlers activos (excepto uno opcional) para realizar broadcasts.
     */
    public List<ClientHandler> getHandlersExcept(ClientHandler exclude) {
        return activeHandlers.stream()
                .filter(h -> h != exclude)
                .collect(Collectors.toList());
    }

    public List<ClientHandler> getAllHandlers() {
        return new ArrayList<>(activeHandlers);
    }

    public int count() {
        return activeHandlers.size();
    }

    public ClientInfo register(ClientHandler handler, int puerto) {
        ClientInfo info = new ClientInfo(handler.getClientSocket(), handler.nick, puerto);
        activeHandlers.add(handler);
        socketToInfoMap.put(handler.getClientSocket(), info);
        return info; // Devolvemos la info por si stats o la UI la necesitan
    }

    public void unregister(ClientHandler handler) {
        activeHandlers.remove(handler);
        socketToInfoMap.remove(handler.getClientSocket());
    }
    /**
     * Limpia todos los registros (Útil al apagar el servidor).
     */
    public void clear() {
        activeHandlers.clear();
        socketToInfoMap.clear();
    }

    public void shutDown() {
        // 1. Cerrar todos los clientes conectados de forma paralela
        activeHandlers.parallelStream().forEach(handler -> {
            try {
                handler.shutDown();
            } catch (Exception e) {
                Logger.logError("Error al cerrar un cliente: " + e.getMessage());
            }
        });
        activeHandlers.clear();
        socketToInfoMap.clear();
    }
}
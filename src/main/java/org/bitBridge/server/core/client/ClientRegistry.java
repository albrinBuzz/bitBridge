package org.bitBridge.server.core.client;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.shared.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
public class ClientRegistry {

    // Única fuente de verdad: El Handler ya contiene su ClientInfo
    private final List<BitBridgeClient> clients = new CopyOnWriteArrayList<>();

    public void addClient(BitBridgeClient handler) {
        clients.add(handler);
    }

    public void removeClient(BitBridgeClient handler) {
        if (handler != null) {
            clients.remove(handler);
        }
    }

    public BitBridgeClient findByNick(String nick) {
        if (nick == null) return null;
        return clients.stream()
                .filter(h -> nick.equalsIgnoreCase(h.getNick()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Retorna los ClientInfos extrayéndolos directamente de los handlers
     */
    public List<ClientInfo> getAllClientInfos() {
        return clients.stream()
                .map(BitBridgeClient::getInfo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<BitBridgeClient> getHandlersExcept(BitBridgeClient exclude) {
        return clients.stream()
                .filter(h -> h != exclude)
                .collect(Collectors.toList());
    }

    public List<BitBridgeClient> getAllHandlers() {
        return new ArrayList<>(clients);
    }

    public int count() {
        return clients.size();
    }

    /**
     * Registro simplificado para NIO
     */
    public ClientInfo register(BitBridgeClient handler, int puerto) throws IOException {
        ClientInfo info = new ClientInfo(handler.getRemoteAddress(), handler.getNick(), puerto);
        handler.setInfo(info);
        addClient(handler);
        return info;
    }

    public void clear() {
        clients.clear();
    }

    public void shutDown() {
        clients.parallelStream().forEach(handler -> {
            try {
                handler.shutDown();
            } catch (Exception e) {
                Logger.logError("Error al cerrar un cliente: " + e.getMessage());
            }
        });
        clients.clear();
    }
}
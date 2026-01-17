package org.bitBridge.server;



import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.client.ClientHandler;
import org.bitBridge.shared.ClientListMessage;
import org.bitBridge.shared.Communication;
import org.bitBridge.shared.CommunicationType;
import org.bitBridge.shared.Mensaje;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ClientRegistry {
    private final List<ClientHandler> clientPool = new CopyOnWriteArrayList<>();

    public void addClient(ClientHandler handler) {
        clientPool.add(handler);
    }

    public void removeClient(ClientHandler handler) {
        clientPool.remove(handler);
    }

    public List<ClientHandler> getAll() {
        return clientPool;
    }

    public boolean nickExists(String nick) {
        return clientPool.stream()
                .anyMatch(c -> c.nick != null && c.nick.equalsIgnoreCase(nick));
    }

    public synchronized String getUniqueNick(String desiredNick) {
        if (!nickExists(desiredNick)) return desiredNick;
        int suffix = 1;
        while (nickExists(desiredNick + suffix)) { suffix++; }
        return desiredNick + suffix;
    }

    public void broadcast(Object message, ClientHandler exclude) {
        clientPool.stream()
                .filter(c -> c != exclude)
                .forEach(c -> c.sendComunicacion((Communication) message));
    }

    public void broadcastSystemMessage(String text, ClientHandler exclude) {
        broadcast(new Mensaje(text, CommunicationType.MESSAGE), exclude);
    }

    public void updateAllClients() {
        var infoList = clientPool.stream()
                .map(c -> new ClientInfo(c.getClientSocket(), c.nick, 0))
                .collect(Collectors.toList());

        broadcast(new ClientListMessage(CommunicationType.UPDATE, infoList), null);
    }
}
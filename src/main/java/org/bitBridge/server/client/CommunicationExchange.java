package org.bitBridge.server.client;


import org.bitBridge.server.client.ClientHandler;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.Communication;

public class CommunicationExchange {
    private final ClientHandler sender;
    private final ServerContext context;

    public CommunicationExchange(ClientHandler sender, ServerContext context) {
        this.sender = sender;
        this.context = context;
    }

    public void sendTo(String receiverNick, Communication message) {
        ClientHandler target = context.registry().findByNick(receiverNick);
        if (target != null) {
            target.sendComunicacion(message);
        }
    }

    public ClientHandler getSender() { return sender; }
    public ServerContext getContext() { return context; }
}
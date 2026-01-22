package org.bitBridge.server.core.client;


import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.core.comunication.Communication;

public class CommunicationExchange {
    private final BitBridgeClient sender;
    private final ServerContext context;

    public CommunicationExchange(BitBridgeClient sender, ServerContext context) {
        this.sender = sender;
        this.context = context;
    }

    public void sendTo(String receiverNick, Communication message) {
        BitBridgeClient target = context.registry().findByNick(receiverNick);
        if (target != null) {
            target.sendComunicacion(message);
        }
    }

    public BitBridgeClient getSender() { return sender; }
    public ServerContext getContext() { return context; }
}
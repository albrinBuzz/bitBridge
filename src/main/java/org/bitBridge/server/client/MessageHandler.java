package org.bitBridge.server.client;

import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.Communication;
import org.bitBridge.shared.Mensaje;

public class MessageHandler implements CommunicationHandler{




    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        Mensaje m = (Mensaje) message;
        var client=exchange.getSender();
        var context=exchange.getContext();
        context.server().broadcastMessage("[" + client.getNick() + "] => " + m.getContenido(), client);
        context.server().addMessageHistory("[" + client.getNick() + "] => " + m.getContenido());
    }
}

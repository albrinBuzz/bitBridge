package org.bitBridge.server.core.client;

import org.bitBridge.shared.Communication;
import org.bitBridge.shared.Mensaje;

public class MessageHandler implements CommunicationHandler{




    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        Mensaje m = (Mensaje) message;
        var client=exchange.getSender();
        var context=exchange.getContext();
        context.getServer().broadcastMessage("[" + client.getNick() + "] => " + m.getContenido(), client);
        context.getServer().addMessageHistory("[" + client.getNick() + "] => " + m.getContenido());
    }
}

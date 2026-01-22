package org.bitBridge.server.core.client;

import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.Mensaje;
import org.bitBridge.shared.core.comunication.MessageAck;
import org.bitBridge.shared.core.comunication.ResponseCommunication;

public class MessageHandler implements CommunicationHandler{




    @Override
    public void handle(CommunicationExchange exchange, Communication message) {
        Mensaje m = (Mensaje) message;
        var sender = exchange.getSender();
        var context = exchange.getContext();

        String formattedMsg = "[" + sender.getNick() + "] => " + m.getContenido();
        context.getServer().broadcastMessage(formattedMsg, sender);
        context.getServer().addMessageHistory(formattedMsg);

        MessageAck ack = new MessageAck(MessageAck.Status.SUCCESS);
        sender.sendComunicacion(ack);
        //Logger.logInfo("Confirmación enviada a " + sender.getNick() + " para mensaje: " + m.getContenido());

    }
}

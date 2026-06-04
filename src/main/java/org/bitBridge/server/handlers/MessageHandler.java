package org.bitBridge.server.handlers;

import org.bitBridge.server.core.client.CommunicationExchange;
import org.bitBridge.server.core.client.CommunicationHandler;
import org.bitBridge.server.core.client.ServerHandler;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.ExecutionMode; // O el paquete donde tengas tu ExecutionMode
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;
import org.bitBridge.shared.core.comunication.model.basic.MessageAck;

@ServerHandler(value = Mensaje.class, mode = ExecutionMode.ASYNC)

public class MessageHandler implements CommunicationHandler {


    @Override
    public void handle(CommunicationExchange exchange, Communication message) {
        Mensaje m = (Mensaje) message;
        var sender = exchange.getSender();
        var context = exchange.getContext();

        Logger.logInfo(((Mensaje) message).getContenido());
        sender.sendComunicacion(new MessageAck(MessageAck.Status.SUCCESS));

        // 2. Procesar el broadcast de forma asíncrona para no bloquear el flujo
        Thread.ofVirtual().start(() -> {
            String formattedMsg = "[" + sender.getNick() + "] => " + m.getContenido();
            context.getServer().broadcastMessage(formattedMsg, sender);
            //context.getServer().addMessageHistory(formattedMsg);
        });
    }
        //Logger.logInfo("Confirmación enviada a " + sender.getNick() + " para mensaje: " + m.getContenido());

}

package org.bitBridge.Client.handlers;

import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;

@MessageMapping(Mensaje.class)
public class ClientChatHandler implements MessageHandler<Mensaje, ClientContext> {
    @Override
    public void handle(Mensaje data, ClientContext context) throws Exception {
        context.client().handleIncomingMessage(data.getContenido());
    }
}


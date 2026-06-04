package org.bitBridge.Client.handlers;


import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;

@ClientHandler(Mensaje.class)
public class ClientMessageHandler implements ClientActionHandler<Mensaje> {
    @Override
    public void handle(Mensaje data, Client cl, ClientContext ctx) throws Exception {
        cl.handleIncomingMessage(data.getContenido());
    }
}
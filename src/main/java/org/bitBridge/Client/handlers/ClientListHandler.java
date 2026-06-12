package org.bitBridge.Client.handlers;

import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.ClientListMessage;

@ClientHandler(ClientListMessage.class)
public class ClientListHandler implements ClientActionHandler<ClientListMessage> {
    @Override
    public void handle(ClientListMessage data, Client cl, ClientContext ctx) throws Exception {
        Logger.logInfo("Recibiiendo "+data.getClientNicks());
        cl.notifyHostobserves(data.getClientNicks());
    }
}
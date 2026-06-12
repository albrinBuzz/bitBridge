package org.bitBridge.Client.handlers;

import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.DirectoryQueryResponse;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;

@ClientHandler(DirectoryQueryResponse.class)
public class ClientDirectoryResponseHandler implements ClientActionHandler<DirectoryQueryResponse> {
    @Override
    public void handle(DirectoryQueryResponse data, Client cl, ClientContext ctx) throws Exception {
        cl.handleRemoteDirectoryUpdate(data.getNodo());
    }
}
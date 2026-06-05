package org.bitBridge.server.handlers;


import org.bitBridge.server.core.client.CommunicationExchange;
import org.bitBridge.server.core.client.CommunicationHandler;
import org.bitBridge.server.core.client.ServerHandler;
import org.bitBridge.server.core.client.ThreadCarrier;
import org.bitBridge.server.transfer.FileTransferService;
import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.core.comunication.Communication;

import org.bitBridge.shared.core.comunication.model.basic.FileDirectoryCommunication;

import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_DATA;

@ServerHandler(value = FileDirectoryCommunication.class, action = "FILE", mode = ExecutionMode.ASYNC,carrier = ThreadCarrier.PHYSICAL)
public class FileForwardHandler implements CommunicationHandler {
    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        var client = exchange.getSender();
        var ctx = exchange.getContext();
        String sessionId = PREFIX_DATA + System.currentTimeMillis() % 10000;

        new FileTransferService(ctx).handleForwardFile(
                (FileDirectoryCommunication) message,
                client,
                sessionId
        );
    }
}
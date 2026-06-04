package org.bitBridge.server.handlers;

import org.bitBridge.server.core.client.CommunicationExchange;
import org.bitBridge.server.core.client.CommunicationHandler;
import org.bitBridge.server.core.client.ServerHandler;

import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;

@ServerHandler(value = FileHandshakeCommunication.class, mode = ExecutionMode.SYNC)
public class NotificationHandler implements CommunicationHandler {
    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        if (message instanceof FileHandshakeCommunication handshake) {
            exchange.getContext().transferManager().registerHandshake(
                    handshake.getSessionId(),
                    handshake
            );
        }
    }
}
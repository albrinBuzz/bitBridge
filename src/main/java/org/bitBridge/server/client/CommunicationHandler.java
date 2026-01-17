package org.bitBridge.server.client;


import org.bitBridge.shared.Communication;

public interface CommunicationHandler {
    void handle(CommunicationExchange exchange, Communication message) throws Exception;
}

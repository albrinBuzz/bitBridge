package org.bitBridge.server.core.client;


import org.bitBridge.shared.core.comunication.Communication;

public interface CommunicationHandler {
    void handle(CommunicationExchange exchange, Communication message) throws Exception;
}

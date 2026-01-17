package org.bitBridge.server.client;

import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.Communication;

public interface ActionHandler {
    void handle(Communication comm, ClientHandler client, ServerContext context) throws Exception;
}
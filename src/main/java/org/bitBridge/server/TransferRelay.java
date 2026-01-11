package org.bitBridge.server;


import org.bitBridge.server.client.ClientHandler;

public interface TransferRelay {
    void registerWaitingReceptor(String sessionId, ClientHandler receptor);
    ClientHandler waitForReceptor(String sessionId, int timeoutSeconds);
}
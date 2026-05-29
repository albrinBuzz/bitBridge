package org.bitBridge.shared.network;


import org.bitBridge.models.LogEntry;

import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.server.core.client.NioClientHandler;
import org.bitBridge.shared.core.comunication.Communication;

import java.io.IOException;
import java.util.function.Consumer;

public interface ServerNetworkEngine extends NetworkEngine {
    String start(int port) throws IOException;
    // Envío a un canal específico a través de su identificador
    void sendTo(String clientId, Communication payload) throws IOException;
    public void setLogListener(Consumer<LogEntry> listener);
    Consumer<LogEntry> getLogListener();
    void disableRead(BitBridgeClient nioClientHandler);
}
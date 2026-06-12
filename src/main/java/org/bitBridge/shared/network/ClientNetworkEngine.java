package org.bitBridge.shared.network;

import org.bitBridge.shared.core.comunication.Communication;

import java.io.IOException;

public interface ClientNetworkEngine extends NetworkEngine {
    void connect(String host, int port) throws IOException;
    void send(Communication payload) throws IOException;
    String getStatus();
    String getHostName();
    void disconnect() throws IOException;
    boolean isActive();
    void setHost(String host, int port);
}

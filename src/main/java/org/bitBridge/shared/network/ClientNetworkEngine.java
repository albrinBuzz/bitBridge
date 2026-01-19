package org.bitBridge.shared.network;

import org.bitBridge.shared.Communication;

import java.io.File;
import java.io.IOException;

public interface ClientNetworkEngine extends NetworkEngine {
    void connect(String host, int port) throws IOException;
    void send(Communication payload) throws IOException;
    String getStatus();
    String getHostName();
    void disconnect();
}

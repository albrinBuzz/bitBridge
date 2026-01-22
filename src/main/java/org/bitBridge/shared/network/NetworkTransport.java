package org.bitBridge.shared.network;

import org.bitBridge.shared.core.comunication.Communication;

import java.io.IOException;

public interface NetworkTransport {
    void start(int port) throws Exception;
    void connect(String host, int port) throws Exception;
    void send(Communication payload) throws IOException;
    void stop() throws IOException;
    boolean isActive();
}

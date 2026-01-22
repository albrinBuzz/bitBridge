package org.bitBridge.shared.network;

import org.bitBridge.shared.core.comunication.Communication;

import java.io.IOException;

public interface NetworkNode {
    void start() throws IOException;
    void stop();
    void send(Communication payload) throws IOException;
    boolean isRunning();
}
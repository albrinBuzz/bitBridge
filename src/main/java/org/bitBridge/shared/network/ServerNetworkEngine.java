package org.bitBridge.shared.network;


import org.bitBridge.shared.Communication;

import java.io.IOException;

public interface ServerNetworkEngine extends NetworkEngine {
    void start(int port) throws IOException;
    // Envío a un canal específico a través de su identificador
    void sendTo(String clientId, Communication payload) throws IOException;
}
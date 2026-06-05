package org.bitBridge.server.core.auth;

import org.bitBridge.server.core.ServerContext;
import org.bitBridge.server.core.client.NioClientHandler;
import org.bitBridge.shared.core.comunication.model.basic.HandshakeMessage;

public interface AuthStrategy {
    void authenticate(NioClientHandler handler, HandshakeMessage handshake, ServerContext context) throws Exception;
}
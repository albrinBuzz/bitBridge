package org.bitBridge.shared.network;

import org.bitBridge.shared.Communication;

import java.io.IOException;

public interface TransportConnection {
    void write(Communication payload) throws IOException;
    Communication read() throws IOException;
    void close() throws IOException;
    String getRemoteAddress();
}

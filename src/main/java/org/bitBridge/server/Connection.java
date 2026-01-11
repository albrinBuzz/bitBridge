package org.bitBridge.server;

import java.io.IOException;

public interface Connection {
    void send(Object message) throws IOException;
    Object receive() throws IOException, ClassNotFoundException;
    void close() throws IOException;
    boolean isOpen();
}
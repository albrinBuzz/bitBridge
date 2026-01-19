package org.bitBridge.server.core.client;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.shared.Communication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public interface BitBridgeClient {
    String getNick();
    void sendComunicacion(Communication comm);
    void shutDown();
    String getRemoteAddress() throws IOException;

    ClientInfo getInfo();
    void setInfo(ClientInfo info);

    SocketChannel getSocketChannel();
    ReadableByteChannel getReadableChannel() throws IOException;
    WritableByteChannel getWritableChannel() throws IOException;
}
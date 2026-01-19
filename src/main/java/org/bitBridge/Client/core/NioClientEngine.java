package org.bitBridge.Client.core;
import org.bitBridge.Client.services.MessageDispatcher;
import org.bitBridge.Tests.nio.NioClientHandler;

import org.bitBridge.shared.Communication;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.network.ClientNetworkEngine;
import org.bitBridge.shared.network.NetworkTransport;
import org.bitBridge.shared.network.ProtocolService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NioClientEngine implements ClientNetworkEngine, Runnable {
    private SocketChannel socketChannel;
    private Selector selector;
    private MessageDispatcher dispatcher;
    private boolean running;

    // Buffers para reconstrucción de paquetes JSON
    private ByteBuffer headerBuffer = ByteBuffer.allocate(6);
    private ByteBuffer payloadBuffer;
    private boolean readingHeader = true;

    public NioClientEngine(MessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void connect(String host, int port) throws IOException {
        this.selector = Selector.open();
        this.socketChannel = SocketChannel.open();
        this.socketChannel.configureBlocking(false);
        this.socketChannel.connect(new InetSocketAddress(host, port));

        // Esperar a que la conexión se complete (NIO style)
        while (!socketChannel.finishConnect()) {
            try {
                Thread.sleep(10); // Pequeña espera para no saturar CPU en el handshaking
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        this.socketChannel.register(selector, SelectionKey.OP_READ);
        this.running = true;

        // Iniciamos el hilo que vigila la red de forma asíncrona
        new Thread(this, "NIO-Client-Worker").start();
    }

    @Override
    public void run() {
        while (running && socketChannel.isOpen()) {
            try {
                if (selector.select(1000) == 0) continue;

                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (key.isReadable()) {
                        readIncomingData();
                    }
                }
            } catch (IOException e) {
                Logger.logError("Conexión perdida con el servidor."+e.getMessage());
                running = false;
            }
        }
    }

    private void readIncomingData() throws IOException {
        if (readingHeader) {
            socketChannel.read(headerBuffer);
            if (!headerBuffer.hasRemaining()) {
                headerBuffer.flip();
                int jsonSize = headerBuffer.getInt();
                short typeSize = headerBuffer.getShort();

                // Preparamos el payloadBuffer incluyendo el espacio para el header
                // para que ProtocolService.fromBytes funcione correctamente
                payloadBuffer = ByteBuffer.allocate(6 + typeSize + jsonSize);

                headerBuffer.flip(); // Volvemos a flip para copiarlo
                payloadBuffer.put(headerBuffer);

                readingHeader = false;
            }
        }

        if (!readingHeader) {
            socketChannel.read(payloadBuffer);
            if (!payloadBuffer.hasRemaining()) {
                payloadBuffer.flip();
                byte[] data = payloadBuffer.array();

                Communication comm = ProtocolService.fromBytes(data);
                dispatcher.dispatch(comm);

                // Reset total
                headerBuffer.clear();
                payloadBuffer = null;
                readingHeader = true;
            }
        }
    }

    @Override
    public void send(Communication payload) throws IOException {

            ProtocolService.writeNIO(socketChannel,payload);

    }

    public void setDispatcher(MessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String getStatus() {
        return "";
    }

    @Override
    public String getHostName() {
        return "";
    }

    @Override
    public void disconnect() {

    }

    @Override
    public void stop() throws IOException {
        this.running = false;
        if (socketChannel != null) socketChannel.close();
        if (selector != null) selector.close();
    }

    @Override
    public boolean isActive() {
        return socketChannel != null && socketChannel.isConnected();
    }
}

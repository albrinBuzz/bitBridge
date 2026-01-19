package org.bitBridge.server.core;


import org.bitBridge.server.core.client.NioClientHandler;
import org.bitBridge.shared.Communication;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.network.NetworkTransport;
import org.bitBridge.shared.network.ServerNetworkEngine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NioServerEngine implements ServerNetworkEngine {
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private final ServerContext context;

    public NioServerEngine(ServerContext context) { this.context = context; }

    @Override
    public void start(int port) throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Hilo único para manejar TODOS los clientes
        new Thread(this::eventLoop, "NIO-Main-Loop").start();
    }

    @Override
    public void sendTo(String clientId, Communication payload) throws IOException {

    }

    private void eventLoop() {
        while (serverChannel.isOpen()) {
            try {
                // Bloquea hasta que ocurra algo en algún socket
                selector.select();

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            } catch (IOException e) { /* Log error */ }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false); // <--- CRÍTICO 1

        // Crear el handler y asociarlo a la interfaz BitBridgeClient
        NioClientHandler handler = new NioClientHandler(clientChannel, context);

        // Registrar el canal en el selector para LECTURA y adjuntar el handler
        clientChannel.register(selector, SelectionKey.OP_READ, handler); // <--- CRÍTICO 2

        //Logger.logInfo("Nueva conexión desde: " + clientChannel.getRemoteAddress());
    }

    public void unregisterChannel(SocketChannel channel) {
        SelectionKey key = channel.keyFor(selector);
        if (key != null) {
            key.cancel(); // Deja de vigilarlo en el loop de NIO
            // Importante: despertar al selector si está bloqueado en select()
            selector.wakeup();
            Logger.logInfo("[NIO] Socket extraído del Selector para transferencia directa.");
        }
    }

    // En NioServerEngine.java
    public void disableRead(NioClientHandler handler) {
        for (SelectionKey key : selector.keys()) {
            if (key.attachment() == handler) {
                key.interestOps(0); // Deja de escuchar eventos de lectura
                selector.wakeup();   // Despierta al selector para aplicar cambios
                break;
            }
        }
    }
    private void handleRead(SelectionKey key) {
        NioClientHandler handler = (NioClientHandler) key.attachment();
        if (handler != null) {
            handler.processRead();
        } else {
            Logger.logError("Error: Se detectó lectura pero no hay Handler adjunto.");
            key.cancel();
        }
    }

    @Override public void stop() throws IOException { serverChannel.close(); selector.close(); }
    @Override public boolean isActive() { return serverChannel.isOpen(); }

}
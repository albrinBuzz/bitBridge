package org.bitBridge.Tests.nio;


import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.Logger;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class NioBitBridgeServer {
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private boolean isRunning;
    private final ServerContext context; // Tu contexto original

    public NioBitBridgeServer(int port, ServerContext context) throws IOException {
        this.context = context;
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();

        // Configuración No Bloqueante
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));

        // Registramos que este canal solo escucha "Aceptaciones" (conexiones nuevas)
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        this.isRunning = true;
    }

    public void start() {
        Logger.logInfo("Servidor NIO iniciado en puerto " + serverChannel.socket().getLocalPort());

        while (isRunning) {
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
            } catch (IOException e) {
                Logger.logError("Error en el loop del Selector: " + e.getMessage());
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);

        // Creamos el manejador y lo vinculamos a la "llave" del selector
        NioClientHandler handler = new NioClientHandler(clientChannel, context);
        clientChannel.register(selector, SelectionKey.OP_READ, handler);
        Logger.logInfo("Nueva conexión desde: " + clientChannel.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) {
        NioClientHandler handler = (NioClientHandler) key.attachment();
        handler.processRead(); // Notificamos al handler que hay bytes listos
    }
}
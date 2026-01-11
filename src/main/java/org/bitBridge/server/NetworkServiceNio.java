package org.bitBridge.server;


import org.bitBridge.shared.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
public class NetworkServiceNio {

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private volatile boolean running;

    public void start(int port) throws IOException {
        // 1. Abrir el Selector y el Canal del Servidor
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();

        // 2. IMPORTANTE: Configurar como NO BLOQUEANTE
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));

        // 3. Registrar el canal en el selector para la operación de "ACEPTAR"
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        running = true;
        Logger.logInfo("Servidor NIO iniciado en puerto " + port);

        new Thread(this::eventLoop, "NIO-Selector-Thread").start();
    }

    private void eventLoop() {
        while (running) {
            try {
                // Bloquea hasta que haya algún evento de red
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        handleAccept();
                    }
                    if (key.isReadable()) {
                        handleRead(key);
                    }
                    iter.remove(); // Limpiar la llave procesada
                }
            } catch (IOException e) {
                Logger.logError("Error en loop NIO: " + e.getMessage());
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        // Registramos el nuevo cliente para LECTURA
        clientChannel.register(selector, SelectionKey.OP_READ);
        Logger.logInfo("Nuevo cliente NIO conectado: " + clientChannel.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024); // Pequeño búfer de prueba

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                clientChannel.close();
                return;
            }

            buffer.flip(); // Cambiar de modo escritura a modo lectura
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            String msg = new String(data);
            Logger.logInfo("Mensaje recibido vía NIO: " + msg);

            buffer.clear();
        } catch (IOException e) {
            key.cancel();
            try { clientChannel.close(); } catch (IOException ex) {}
        }
    }
}

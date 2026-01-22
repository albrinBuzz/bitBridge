package org.bitBridge.Tests.nio;



import org.bitBridge.shared.core.comunication.Communication;

import org.bitBridge.shared.Logger;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class BitBridgeNioClient implements Runnable {
    private SocketChannel clientChannel;
    private Selector selector;
    private boolean running;

    // Buffers para reconstrucción de mensajes (igual que el servidor)
    private ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
    private ByteBuffer payloadBuffer = null;
    private boolean readingLength = true;

    public void connect(String host, int port) throws IOException {
        selector = Selector.open();
        clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(false);
        clientChannel.connect(new InetSocketAddress(host, port));

        // Registrar para conexión y lectura
        clientChannel.register(selector, SelectionKey.OP_CONNECT);
        this.running = true;

        // Iniciar hilo de escucha
        new Thread(this, "NioClient-Worker").start();
    }

    @Override
    public void run() {
        while (running) {
            try {
                selector.select(); // Esperar eventos de red
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (key.isConnectable()) {
                        handleConnect(key);
                    } else if (key.isReadable()) {
                        handleRead();
                    }
                }
            } catch (IOException e) {
                Logger.logError("Error en conexión NIO: " + e.getMessage());
                stop();
            }
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        if (clientChannel.isConnectionPending()) {
            clientChannel.finishConnect();
        }
        clientChannel.register(selector, SelectionKey.OP_READ);
        Logger.logInfo("Conectado al servidor vía NIO.");
    }

    private void handleRead() throws IOException {
        if (readingLength) {
            int read = clientChannel.read(lengthBuffer);
            if (read == -1) throw new IOException("Servidor cerró la conexión");

            if (!lengthBuffer.hasRemaining()) {
                lengthBuffer.flip();
                int size = lengthBuffer.getInt();
                payloadBuffer = ByteBuffer.allocate(size);
                readingLength = false;
            }
        }

        if (!readingLength) {
            clientChannel.read(payloadBuffer);
            if (!payloadBuffer.hasRemaining()) {
                payloadBuffer.flip();
                byte[] data = payloadBuffer.array();

                // Aquí procesas el mensaje recibido
                onMessageReceived(data);

                // Reset para el próximo mensaje
                lengthBuffer.clear();
                payloadBuffer = null;
                readingLength = true;
            }
        }
    }

    public void send(Communication comm) {
        try {
            // Convertir a [Length][JSON]
            ProtocolService.writeNIO(clientChannel,comm);
            /*&ByteBuffer buffer = ProtocolService.writeNIO(clientChannel,comm);
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer);
            }*/
        } catch (IOException e) {
            Logger.logError("Error al enviar: " + e.getMessage());
        }
    }

    private void onMessageReceived(byte[] data) throws IOException {
        // Deserializar y enviar al controlador de la UI
        Communication comm = ProtocolService.fromBytes(data);
        System.out.println("Recibido: " + comm.getClass().getSimpleName());
    }

    public void stop() {
        running = false;
        try { clientChannel.close(); selector.close(); } catch (IOException ignored) {}
    }
}
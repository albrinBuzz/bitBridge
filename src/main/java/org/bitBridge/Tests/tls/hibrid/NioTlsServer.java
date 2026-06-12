package org.bitBridge.Tests.tls.hibrid;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;

public class NioTlsServer implements Runnable {

    // 🚀 DEBE COINCIDIR CON EL VALOR DEL CLIENTE PARA EVITAR CORRUPCIÓN DE PROTOCOLO
    private static final boolean USE_ENCRYPTION = true;

    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;
    private SSLContext sslContext;

    public NioTlsServer(int port, String keyStorePath, String password) throws Exception {
        if (USE_ENCRYPTION) {
            this.sslContext = SecurityUtils.createSSLContext(keyStorePath, password);
        }

        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.bind(new InetSocketAddress(port));
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("🤖 [SERVER-NIO] Servidor híbrido en puerto " + port + " (Cifrado: " + USE_ENCRYPTION + ")");
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.isReadable()) {
                        NioTlsHandler tlsHandler = (NioTlsHandler) key.attachment();
                        if (tlsHandler != null) tlsHandler.handleReadEvent();
                    }
                }
            } catch (IOException e) {
                System.err.println("🚨 Error en el Selector del Servidor: " + e.getMessage());
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);

        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

        try {
            NioTlsHandler tlsHandler = new NioTlsHandler(clientChannel, sslContext, clientKey, false, USE_ENCRYPTION);
            clientKey.attach(tlsHandler);
        } catch (Exception e) {
            clientChannel.close();
        }
    }

    public static void main(String[] args) throws Exception {
        String keystore = "/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/keystore_local.p12";
        new Thread(new NioTlsServer(8080, keystore, "miPasswordLocal123")).start();
    }
}
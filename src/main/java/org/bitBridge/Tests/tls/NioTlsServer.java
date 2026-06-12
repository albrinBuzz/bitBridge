package org.bitBridge.Tests.tls;


import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.security.KeyStore;
import java.util.Iterator;

public class NioTlsServer implements Runnable {

    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;
    private final SSLContext sslContext;

    public NioTlsServer(int port, String keyStorePath, String password) throws Exception {
        // 1. Inicializar el contexto SSLv1.3 del Servidor con su KeyStore
        this.sslContext = createSSLContext(keyStorePath, password);

        // 2. Configurar el Socket de Red en modo NIO
        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.bind(new InetSocketAddress(port));
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("🤖 [SERVER-NIO] Servidor headless seguro iniciado en puerto " + port);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Bloquea de forma reactiva hasta que ocurra un evento de red
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            } catch (IOException e) {
                System.err.println("🚨 Error en el bucle del Selector: " + e.getMessage());
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);

        // Registramos el cliente inicialmente en modo lectura
        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

        try {
            // Instanciamos el manejador TLS en modo SERVIDOR (isClient = false)
            NioTlsHandler tlsHandler = new NioTlsHandler(clientChannel, sslContext, clientKey, false);
            clientKey.attach(tlsHandler); // Lo adjuntamos a la llave NIO para recuperarlo después

            System.out.println("📡 [SERVER-NIO] Nueva conexión entrante. Iniciando handshake TLS...");
        } catch (Exception e) {
            System.err.println("❌ Falló el enlace TLS inicial: " + e.getMessage());
            clientChannel.close();
        }
    }

    private void handleRead(SelectionKey key) {
        // Recuperamos el Handler criptográfico asociado a este canal específico
        NioTlsHandler tlsHandler = (NioTlsHandler) key.attachment();
        if (tlsHandler != null) {
            try {
                tlsHandler.handleReadEvent();
            } catch (IOException e) {
                System.err.println("🔌 Cliente desconectado abruptamente: " + e.getMessage());
                key.cancel();
                try { key.channel().close(); } catch (IOException ignored) {}
            }
        }
    }

    private SSLContext createSSLContext(String path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(path)) {
            keyStore.load(fis, password.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    public static void main(String[] args) throws Exception {
        // Ajusta las rutas a tu keystore_local.p12 real
        String keystore = "/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/keystore_local.p12";
        new Thread(new NioTlsServer(8080, keystore, "miPasswordLocal123")).start();
    }
}
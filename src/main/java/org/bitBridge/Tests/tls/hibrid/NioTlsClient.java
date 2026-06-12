package org.bitBridge.Tests.tls.hibrid;

import org.bitBridge.shared.Logger;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NioTlsClient implements Runnable {

    // 🚀 INTERRUPTOR MAESTRO PARA WIRESHARK / TCPDUMP
    private static final boolean USE_ENCRYPTION = true;

    private final Selector selector;
    private final SocketChannel socketChannel;
    private SSLContext sslContext;
    private NioTlsHandler tlsHandler;

    public NioTlsClient(String host, int port, String trustStorePath) throws Exception {
        if (USE_ENCRYPTION) {
            // Solo creamos contexto criptográfico si la prueba lo exige
            this.sslContext = SecurityUtils.crearContextoDesdeCertificadoPlano(trustStorePath);
        }

        this.selector = Selector.open();
        this.socketChannel = SocketChannel.open();
        this.socketChannel.configureBlocking(false);
        this.socketChannel.connect(new InetSocketAddress(host, port));

        this.socketChannel.register(selector, SelectionKey.OP_CONNECT);
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

                    if (key.isConnectable()) {
                        handleConnect(key);
                    } else if (key.isReadable()) {
                        if (this.tlsHandler != null) this.tlsHandler.handleReadEvent();
                    }
                }
            } catch (IOException e) {
                Logger.logError("🚨 Error en el ciclo del cliente: " + e.getMessage());
                break;
            }
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        if (socketChannel.finishConnect()) {
            Logger.logInfo("🌐 [CLIENT-NIO] Enlace TCP establecido.");

            // Pasamos el flag USE_ENCRYPTION al Handler
            this.tlsHandler = new NioTlsHandler(socketChannel, sslContext, key, true, USE_ENCRYPTION);
            key.attach(this.tlsHandler);

            key.interestOps(SelectionKey.OP_READ);

            // Si está activo el cifrado, gatillar el ClientHello. Si no, queda listo para escribir.
            if (USE_ENCRYPTION) {
                this.tlsHandler.handleReadEvent();
            }
        }
    }

    public void send(String msg) throws IOException {
        if (this.tlsHandler != null) {
            this.tlsHandler.sendSecureMessage(msg.getBytes());
        }
    }

    public static void main(String[] args) throws Exception {
        String trustStore = "/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/certificado_local.crt";
        NioTlsClient client = new NioTlsClient("127.0.0.1", 8080, trustStore);

        new Thread(client).start();

        int intentos = 0;
        while ((client.tlsHandler == null || !client.tlsHandler.isHandshakeComplete()) && intentos < 50) {
            Thread.sleep(100);
            intentos++;
        }

        System.out.println("🚀 [CLIENT] Enviando payload de prueba...");
        client.send("Hola, esto es bitBridge transmitiendo datos!");
    }
}
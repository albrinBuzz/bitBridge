package org.bitBridge.Tests.tls;



import org.bitBridge.shared.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Iterator;

public class NioTlsClient implements Runnable {

    private final Selector selector;
    private final SocketChannel socketChannel;
    private final SSLContext sslContext;
    private NioTlsHandler tlsHandler;

    public NioTlsClient(String host, int port, String trustStorePath, String password) throws Exception {
        // 1. Inicializar el contexto SSL del Cliente con su TrustStore (certificados de confianza)
        //this.sslContext = createSSLContext(trustStorePath, password);
        this.sslContext = crearContextoDesdeCertificadoPlano(trustStorePath);

        // 2. Abrir el canal NIO
        this.selector = Selector.open();
        this.socketChannel = SocketChannel.open();
        this.socketChannel.configureBlocking(false);
        this.socketChannel.connect(new InetSocketAddress(host, port));

        // El cliente se registra inicialmente esperando terminar la conexión física (OP_CONNECT)
        this.socketChannel.register(selector, SelectionKey.OP_CONNECT);
    }


    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                selector.select(); // Detiene el hilo hasta que ocurra un evento registrado
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isConnectable()) {
                        handleConnect(key);
                    } else if (key.isReadable()) { // Quitamos el disparo por isWritable() ciego
                        handleNetworkEvent();
                    }
                }
            } catch (IOException e) {
                Logger.logError("🚨 Error en el ciclo del cliente: " + e.getMessage());
                break;
            }
        }
    }

    private void handleNetworkEvent() throws IOException {
        if (this.tlsHandler != null) {
            this.tlsHandler.handleReadEvent();
        }
    }


    private void handleConnect(SelectionKey key) throws IOException {
        if (socketChannel.finishConnect()) {
            Logger.logInfo("🌐 [CLIENT-NIO] Enlace TCP establecido con el servidor.");

            this.tlsHandler = new NioTlsHandler(socketChannel, sslContext, key, true);
            key.attach(this.tlsHandler);

            // Ajuste: Interés exclusivo en LECTURA. El wrap inicial se ejecuta inmediatamente aquí abajo.
            key.interestOps(SelectionKey.OP_READ);

            // Forzamos el arranque manual que disparará el primer NEED_WRAP (ClientHello)
            this.tlsHandler.handleReadEvent();
        }
    }

    private void handleRead() throws IOException {
        if (this.tlsHandler != null) {
            this.tlsHandler.handleReadEvent();
        }
    }

    public void send(String msg) throws IOException {
        if (this.tlsHandler != null) {
            this.tlsHandler.sendSecureMessage(msg.getBytes());
        }
    }

    private SSLContext createSSLContext(String path, String password) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(path)) {
            trustStore.load(fis, password.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    public SSLContext crearContextoDesdeCertificadoPlano(String rutaCertificadoCrt) throws Exception {
        Logger.logInfo("🔒 [SECURITY] Inyectando certificado plano .crt directamente en la memoria RAM del cliente.");

        try (InputStream fis = new FileInputStream(rutaCertificadoCrt)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificado = (X509Certificate) cf.generateCertificate(fis);

            // Crear un contenedor limpio directamente en RAM
            KeyStore ramTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            ramTrustStore.load(null, null);
            ramTrustStore.setCertificateEntry("vps-custom-entry", certificado);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ramTrustStore);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        }
    }

    public boolean isEncryptedAndReady() {
        return this.tlsHandler != null &&
                // Suponiendo que guardas una bandera cuando el handshake termina con éxito
                this.tlsHandler.isHandshakeComplete();
    }

    public static void main(String[] args) throws Exception {
        String trustStore = "/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/certificado_local.crt";
        NioTlsClient client = new NioTlsClient("127.0.0.1", 8080, trustStore, "miPasswordLocal123");

        Thread clientThread = new Thread(client);
        clientThread.start();

        // En lugar de Thread.sleep(5000), esperamos de forma dinámica con un timeout de seguridad
        int intentos = 0;
        while (!client.isEncryptedAndReady() && intentos < 50) {
            Thread.sleep(100); // Duerme bloques de 100ms
            intentos++;
        }

        if (client.isEncryptedAndReady()) {
            System.out.println("🚀 [CLIENT] Enviando mensaje de prueba cifrado...");
            client.send("Hola Servidor Seguro, esto es bitBridge sobre TLS 1.3!");
        } else {
            Logger.logError("❌ Error: El handshake TLS no logró consolidarse tras 5 segundos.");
        }
    }
}
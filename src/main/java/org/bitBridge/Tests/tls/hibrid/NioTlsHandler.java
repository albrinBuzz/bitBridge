package org.bitBridge.Tests.tls.hibrid;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioTlsHandler {

    private final SocketChannel socketChannel;
    private final SelectionKey selectionKey;
    private final boolean useEncryption;

    // Estos elementos solo se instancian si useEncryption == true
    private SSLEngine sslEngine;
    private ByteBuffer myNetData;
    private ByteBuffer peerNetData;
    private ByteBuffer peerAppData;
    private ExecutorService taskExecutor;

    private boolean handshakeComplete = false;

    public NioTlsHandler(SocketChannel socketChannel, SSLContext sslContext, SelectionKey selectionKey, boolean isClient, boolean useEncryption) throws IOException {
        this.socketChannel = socketChannel;
        this.selectionKey = selectionKey;
        this.useEncryption = useEncryption;

        if (useEncryption) {
            this.sslEngine = sslContext.createSSLEngine();
            this.sslEngine.setUseClientMode(isClient);

            SSLSession session = sslEngine.getSession();
            this.myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
            this.peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
            this.peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
            this.taskExecutor = Executors.newVirtualThreadPerTaskExecutor();

            // Arrancar negociación TLS
            this.sslEngine.beginHandshake();
        } else {
            // Modo Texto Plano: Solo reservamos un buffer ordinario para lecturas de red
            this.peerNetData = ByteBuffer.allocate(8192);
            this.handshakeComplete = true; // No requiere handshake
            System.out.println("🔓 [⚠️ ENCRYPTION OFF] Canal establecido en TEXTO PLANO.");
        }
    }

    /**
     * Punto de entrada desde el Selector (OP_READ)
     */
    public void handleReadEvent() throws IOException {
        if (useEncryption && !handshakeComplete) {
            doHandshake();
            return;
        }

        if (useEncryption) {
            // --- FLUJO CIFRADO ---
            int bytesRead = socketChannel.read(peerNetData);
            if (bytesRead == -1) {
                closeInbound();
                return;
            }
            processIncomingData();
        } else {
            // --- FLUJO TEXTO PLANO (Ideal para capturar en Wireshark) ---
            peerNetData.clear();
            int bytesRead = socketChannel.read(peerNetData);
            if (bytesRead == -1) {
                socketChannel.close();
                return;
            }
            peerNetData.flip();
            if (peerNetData.hasRemaining()) {
                byte[] rawBytes = new byte[peerNetData.remaining()];
                peerNetData.get(rawBytes);
                onPlainTextReceived(rawBytes);
            }
        }
    }

    /**
     * Orquesta el Handshake TLS reactivo (Solo corre si useEncryption es true).
     */
    private synchronized void doHandshake() throws IOException {
        ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
        boolean progreso = true;

        while (progreso) {
            SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();

            switch (handshakeStatus) {
                case NEED_WRAP:
                    myNetData.clear();
                    SSLEngineResult wrapResult = sslEngine.wrap(emptyBuffer, myNetData);

                    if (wrapResult.getStatus() == SSLEngineResult.Status.OK) {
                        myNetData.flip();
                        while (myNetData.hasRemaining()) {
                            socketChannel.write(myNetData);
                        }
                        selectionKey.interestOps(SelectionKey.OP_READ);
                    } else {
                        throw new IOException("Fallo en Wrap durante Handshake: " + wrapResult.getStatus());
                    }
                    break;

                case NEED_UNWRAP:
                    if (peerNetData.position() == 0) {
                        int read = socketChannel.read(peerNetData);
                        if (read == -1) {
                            throw new IOException("El extremo remoto cerró el canal durante el handshake.");
                        }
                        if (read == 0) {
                            selectionKey.interestOps(SelectionKey.OP_READ);
                            progreso = false;
                            break;
                        }
                    }

                    peerNetData.flip();
                    SSLEngineResult unwrapResult = sslEngine.unwrap(peerNetData, peerAppData);
                    peerNetData.compact();

                    if (unwrapResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        selectionKey.interestOps(SelectionKey.OP_READ);
                        progreso = false;
                    } else if (unwrapResult.getStatus() != SSLEngineResult.Status.OK) {
                        throw new IOException("Fallo en Unwrap durante Handshake: " + unwrapResult.getStatus());
                    }
                    break;

                case NEED_TASK:
                    selectionKey.interestOps(0);
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        Runnable finalTask = task;
                        taskExecutor.submit(() -> {
                            try {
                                finalTask.run();
                                reiniciarHandshakePostTarea();
                            } catch (Exception e) {
                                System.err.println("🚨 Error ejecutando tarea delegada TLS: " + e.getMessage());
                            }
                        });
                    }
                    progreso = false;
                    break;

                case FINISHED:
                case NOT_HANDSHAKING:
                    this.handshakeComplete = true;
                    selectionKey.interestOps(SelectionKey.OP_READ);
                    selectionKey.selector().wakeup();
                    System.out.println("🔒 [TLS] Handshake consolidado. Canal seguro operativo.");
                    progreso = false;
                    break;

                default:
                    throw new IllegalStateException("Estado TLS inesperado: " + handshakeStatus);
            }
        }
    }

    private void reiniciarHandshakePostTarea() throws IOException {
        selectionKey.interestOps(SelectionKey.OP_READ);
        selectionKey.selector().wakeup();
        doHandshake();
    }

    private void processIncomingData() throws IOException {
        peerNetData.flip();
        while (peerNetData.hasRemaining()) {
            int posBefore = peerNetData.position();
            peerAppData.clear();

            SSLEngineResult result = sslEngine.unwrap(peerNetData, peerAppData);

            if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) break;
            if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                throw new IOException("Buffer de aplicación rebasado.");
            }
            if (peerNetData.position() == posBefore && result.getStatus() != SSLEngineResult.Status.OK) break;

            peerAppData.flip();
            if (peerAppData.hasRemaining()) {
                byte[] planoBytes = new byte[peerAppData.remaining()];
                peerAppData.get(planoBytes);
                onPlainTextReceived(planoBytes);
            }
        }
        peerNetData.compact();
    }

    /**
     * Envía datos aplicando cifrado dinámico o directo según la configuración.
     */
    public synchronized void sendSecureMessage(byte[] plainMessage) throws IOException {
        if (!handshakeComplete) throw new IOException("Imposible transmitir: Handshake incompleto.");

        ByteBuffer appOut = ByteBuffer.wrap(plainMessage);

        if (useEncryption) {
            // Cifrado TLS al vuelo
            while (appOut.hasRemaining()) {
                myNetData.clear();
                SSLEngineResult result = sslEngine.wrap(appOut, myNetData);

                if (result.getStatus() == SSLEngineResult.Status.OK) {
                    myNetData.flip();
                    while (myNetData.hasRemaining()) {
                        socketChannel.write(myNetData);
                    }
                } else {
                    throw new IOException("Error cifrando payload: " + result.getStatus());
                }
            }
        } else {
            // Escritura directa sin cifrado (Capturable en Wireshark)
            while (appOut.hasRemaining()) {
                socketChannel.write(appOut);
            }
        }
    }

    private void onPlainTextReceived(byte[] data) {
        System.out.println("📥 Datos recibidos (" + (useEncryption ? "DESCIFRADOS" : "TEXTO PLANO") + "): [" + new String(data).trim() + "] (" + data.length + " bytes)");
    }

    private void closeInbound() throws IOException {
        if (sslEngine != null) {
            try { sslEngine.closeInbound(); } catch (Exception ignored) {}
        }
        socketChannel.close();
    }

    public boolean isHandshakeComplete() {
        return this.handshakeComplete;
    }
}
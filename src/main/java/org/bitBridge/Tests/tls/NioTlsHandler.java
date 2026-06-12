package org.bitBridge.Tests.tls;


import javax.net.ssl.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioTlsHandler {

    private final SocketChannel socketChannel;
    private final SSLEngine sslEngine;
    private final SelectionKey selectionKey;

    // Buffers requeridos por la especificación de la API
    private final ByteBuffer myNetData;
    private final ByteBuffer peerNetData;
    private final ByteBuffer peerAppData;

    // Pool para delegar operaciones matemáticas pesadas (claves asimétricas)
    private final ExecutorService taskExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private boolean handshakeComplete = false;

    public NioTlsHandler(SocketChannel socketChannel, SSLContext sslContext, SelectionKey selectionKey, boolean isClient) throws IOException {
        this.socketChannel = socketChannel;
        this.selectionKey = selectionKey;

        // 1. Crear el motor criptográfico
        this.sslEngine = sslContext.createSSLEngine();
        this.sslEngine.setUseClientMode(isClient);

        // Ajustar tamaños dinámicamente según la sesión TLS
        SSLSession session = sslEngine.getSession();
        this.myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());

        // 2. Indicar al motor que comience la negociación
        this.sslEngine.beginHandshake();
    }

    /**
     * Punto de entrada principal cuando el Selector de NIO gatilla un OP_READ.
     */
    public void handleReadEvent() throws IOException {
        // Si el protocolo está en negociación, derivar al sub-bucle asíncrono
        if (!handshakeComplete) {
            doHandshake();
            return;
        }

        // --- FLUJO DE LECTURA DE APLICACIÓN ORDINARIA ---
        int bytesRead = socketChannel.read(peerNetData);
        if (bytesRead == -1) {
            closeInbound();
            return;
        }

        processIncomingData();
    }

    /**
     * Orquesta el bucle de estados del Handshake TLS de forma reactiva.
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
                        // Después de enviar, lo normal es que necesitemos leer la respuesta (ServerHello / Finished)
                        selectionKey.interestOps(SelectionKey.OP_READ);
                    } else {
                        throw new IOException("Fallo en Wrap durante Handshake: " + wrapResult.getStatus());
                    }
                    break;

                case NEED_UNWRAP:
                    // Intentamos leer del socket solo si nuestro buffer está vacío
                    if (peerNetData.position() == 0) {
                        int read = socketChannel.read(peerNetData);
                        if (read == -1) {
                            throw new IOException("El extremo remoto cerró el canal durante el handshake.");
                        }
                        if (read == 0) {
                            // No hay más bytes en el canal de red de la tarjeta.
                            // Detener el bucle y esperar a que el Selector gatille otro OP_READ.
                            selectionKey.interestOps(SelectionKey.OP_READ);
                            progreso = false;
                            break;
                        }
                    }

                    peerNetData.flip();
                    SSLEngineResult unwrapResult = sslEngine.unwrap(peerNetData, peerAppData);
                    peerNetData.compact();

                    if (unwrapResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        // El paquete está incompleto en red, requerimos explícitamente más bytes.
                        selectionKey.interestOps(SelectionKey.OP_READ);
                        progreso = false;
                    } else if (unwrapResult.getStatus() != SSLEngineResult.Status.OK) {
                        throw new IOException("Fallo en Unwrap durante Handshake: " + unwrapResult.getStatus());
                    }
                    break;

                case NEED_TASK:
                    // Desactivar temporalmente los intereses del Selector para que no genere loops de CPU
                    selectionKey.interestOps(0);

                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        Runnable finalTask = task;
                        taskExecutor.submit(() -> {
                            try {
                                finalTask.run();
                                // Re-invocar el proceso de handshake de forma segura desde el hilo virtual al terminar
                                reiniciarHandshakePostTarea();
                            } catch (Exception e) {
                                System.err.println("🚨 Error ejecutando tarea delegada TLS: " + e.getMessage());
                            }
                        });
                    }
                    // Rompemos el bucle actual, el hilo virtual se encargará de despertar al selector.
                    progreso = false;
                    break;

                case FINISHED:
                case NOT_HANDSHAKING:
                    this.handshakeComplete = true;
                    // ¡Handshake consolidado! Volvemos a escuchar lecturas ordinarias de aplicación
                    selectionKey.interestOps(SelectionKey.OP_READ);
                    selectionKey.selector().wakeup();
                    System.out.println("🔒 [TLS] Handshake consolidado de extremo a extremo. Canal seguro operativo.");
                    progreso = false;
                    break;

                default:
                    throw new IllegalStateException("Estado TLS inesperado: " + handshakeStatus);
            }
        }
    }

    /**
     * Método puente para reconectar el hilo asíncronico de la tarea criptográfica con el Selector.
     */
    private void reiniciarHandshakePostTarea() throws IOException {
        // Devolvemos el interés de lectura al canal
        selectionKey.interestOps(SelectionKey.OP_READ);
        // Despertamos al selector e iteramos el handshake manualmente para evaluar el siguiente paso
        selectionKey.selector().wakeup();
        doHandshake();
    }


    /**
     * Descifra de forma iterativa y devora múltiples paquetes acoplados en ráfagas de red.
     */
    private void processIncomingData() throws IOException {
        peerNetData.flip();

        while (peerNetData.hasRemaining()) {
            int posBefore = peerNetData.position();
            peerAppData.clear();

            SSLEngineResult result = sslEngine.unwrap(peerNetData, peerAppData);

            if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                break; // Esperar más paquetes del cable
            }
            if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                throw new IOException("Buffer de aplicación rebasado catastróficamente.");
            }

            if (peerNetData.position() == posBefore && result.getStatus() != SSLEngineResult.Status.OK) {
                break; // Evitar ciclos infinitos si el motor se estanca
            }

            peerAppData.flip();
            if (peerAppData.hasRemaining()) {
                byte[] planoBytes = new byte[peerAppData.remaining()];
                peerAppData.get(planoBytes);

                // 🚀 AQUÍ LE ENTREGAS LOS BYTES DE TEXTO PLANO A TU PROTOCOLSERVICE
                onPlainTextReceived(planoBytes);
            }
        }

        peerNetData.compact();
    }

    /**
     * Envía de forma segura un mensaje plano cifrándolo al vuelo.
     */
    public synchronized void sendSecureMessage(byte[] plainMessage) throws IOException {
        if (!handshakeComplete) throw new IOException("Imposible transmitir: Handshake incompleto.");

        ByteBuffer appOut = ByteBuffer.wrap(plainMessage);

        while (appOut.hasRemaining()) {
            myNetData.clear();
            SSLEngineResult result = sslEngine.wrap(appOut, myNetData);

            if (result.getStatus() == SSLEngineResult.Status.OK) {
                myNetData.flip();
                while (myNetData.hasRemaining()) {
                    socketChannel.write(myNetData); // Escritura física directa
                }
            } else {
                throw new IOException("Error cifrando payload saliente: " + result.getStatus());
            }
        }
    }

    private void onPlainTextReceived(byte[] data) {
        System.out.println("📥 Datos descifrados listos para procesar: " + data.length + " bytes.");
    }

    private void closeInbound() throws IOException {
        try {
            sslEngine.closeInbound();
        } finally {
            socketChannel.close();
        }
    }

    public boolean isHandshakeComplete() {
        if (this.sslEngine == null) return false;

        javax.net.ssl.SSLEngineResult.HandshakeStatus status = this.sslEngine.getHandshakeStatus();

        // En TLS 1.3, NOT_HANDSHAKING significa que el canal seguro ya está operativo para enviar datos
        return status == javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING ||
                status == javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
    }

}
package org.bitBridge.shared.network.tls;

import org.bitBridge.shared.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
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

    private SSLEngine sslEngine;
    private ByteBuffer myNetData;
    private ByteBuffer peerNetData;
    private ByteBuffer peerAppData;
    private ExecutorService taskExecutor;

    private volatile boolean handshakeComplete = false;

    public NioTlsHandler(SocketChannel socketChannel, SSLContext sslContext, SelectionKey selectionKey, boolean isClient, boolean useEncryption) throws IOException {
        this.socketChannel = socketChannel;
        this.selectionKey = selectionKey;
        this.useEncryption = useEncryption;

        if (useEncryption) {
            this.sslEngine = sslContext.createSSLEngine();
            this.sslEngine.setUseClientMode(isClient);

            SSLSession session = sslEngine.getSession();
            // Usamos buffers directos para evitar overhead de copias en el espacio de usuario/kernel de Linux
            this.myNetData = ByteBuffer.allocateDirect(session.getPacketBufferSize());
            this.peerNetData = ByteBuffer.allocateDirect(session.getPacketBufferSize());
            this.peerAppData = ByteBuffer.allocateDirect(session.getApplicationBufferSize());
            this.taskExecutor = Executors.newVirtualThreadPerTaskExecutor();

            this.sslEngine.beginHandshake();
        } else {
            this.peerNetData = ByteBuffer.allocateDirect(8192);
            this.handshakeComplete = true;
            Logger.logInfo("🔓 [⚠️ ENCRYPTION OFF] Canal establecido en TEXTO PLANO.");
        }
    }

    /**
     * Punto de entrada principal desde el Selector (OP_READ)
     */
    public ByteBuffer handleReadEvent() throws IOException {
        if (useEncryption && !handshakeComplete) {
            doHandshake();

            // 🚨 TRANSICIÓN EN CALIENTE: Si doHandshake() acaba de consolidar el canal,
            // no retornamos null inmediatamente; verificamos si ya hay datos de aplicación en el buffer.
            if (handshakeComplete) {
                if (peerNetData.position() > 0) {
                    return processIncomingData();
                }
            }
            return null;
        }

        if (useEncryption) {
            // --- FLUJO CIFRADO ---
            int bytesRead = socketChannel.read(peerNetData);
            if (bytesRead == -1) {
                closeInbound();
                return null;
            }
            return processIncomingData();
        } else {
            // --- FLUJO TEXTO PLANO ---
            peerNetData.clear();
            int bytesRead = socketChannel.read(peerNetData);
            if (bytesRead == -1) {
                socketChannel.close();
                return null;
            }
            peerNetData.flip();
            if (peerNetData.hasRemaining()) {
                byte[] rawBytes = new byte[peerNetData.remaining()];
                peerNetData.get(rawBytes);
                return ByteBuffer.wrap(rawBytes);
            }
        }
        return null;
    }

    /**
     * Orquesta el Handshake TLS reactivo.
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
                    selectionKey.interestOps(0); // Pausa defensiva en el selector del canal
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        Runnable finalTask = task;
                        taskExecutor.submit(() -> {
                            try {
                                finalTask.run();
                                reiniciarHandshakePostTarea();
                            } catch (Exception e) {
                                Logger.logError("🚨 Error ejecutando tarea delegada TLS: " + e.getMessage());
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
                    Logger.logInfo("🔒 [TLS] Handshake consolidado. Canal seguro operativo.");
                    progreso = false;
                    break;

                default:
                    throw new IllegalStateException("Estado TLS inesperado: " + handshakeStatus);
            }
        }
    }

    private void reiniciarHandshakePostTarea() throws IOException {
        if (selectionKey.isValid()) {
            selectionKey.interestOps(SelectionKey.OP_READ);
            selectionKey.selector().wakeup();
            doHandshake();
        }
    }

    private ByteBuffer processIncomingData() throws IOException {
        peerNetData.flip();
        ByteBuffer resultadoAcumulado = null;

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

                // Si la fragmentación TCP agrupó varios mensajes, los unificamos en un solo buffer plano limpio
                if (resultadoAcumulado == null) {
                    resultadoAcumulado = ByteBuffer.wrap(planoBytes);
                } else {
                    ByteBuffer nuevoBuffer = ByteBuffer.allocate(resultadoAcumulado.remaining() + planoBytes.length);
                    nuevoBuffer.put(resultadoAcumulado);
                    nuevoBuffer.put(planoBytes);
                    nuevoBuffer.flip();
                    resultadoAcumulado = nuevoBuffer;
                }
            }
        }
        peerNetData.compact();
        return resultadoAcumulado;
    }

    public synchronized void sendSecureMessage(byte[] plainMessage) throws IOException {
        if (!handshakeComplete) throw new IOException("Imposible transmitir: Handshake incompleto.");

        ByteBuffer appOut = ByteBuffer.wrap(plainMessage);

        if (useEncryption) {
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
            while (appOut.hasRemaining()) {
                socketChannel.write(appOut);
            }
        }
    }

    public synchronized ByteBuffer cifrar(ByteBuffer appBuffer) throws IOException {
        if (!useEncryption) return appBuffer;
        if (!handshakeComplete) throw new IOException("❌ Imposible cifrar: El handshake TLS no ha culminado.");

        myNetData.clear();
        SSLEngineResult result = sslEngine.wrap(appBuffer, myNetData);
        myNetData.flip();

        if (result.getStatus() == SSLEngineResult.Status.OK) {
            return myNetData;
        } else {
            throw new IOException("❌ Error en el proceso de cifrado (Wrap): " + result.getStatus());
        }
    }

    public synchronized ByteBuffer descifrar(ByteBuffer netBuffer) throws IOException {
        if (!useEncryption) return netBuffer;

        peerAppData.clear();
        SSLEngineResult result = sslEngine.unwrap(netBuffer, peerAppData);
        peerAppData.flip();

        if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) return null;

        if (result.getStatus() == SSLEngineResult.Status.OK) {
            return peerAppData;
        } else {
            throw new IOException("❌ Error en el proceso de descifrado (Unwrap): " + result.getStatus());
        }
    }

    private void closeInbound() throws IOException {
        if (sslEngine != null) {
            try { sslEngine.closeInbound(); } catch (Exception ignored) {}
        }
        if (socketChannel != null && socketChannel.isOpen()) {
            socketChannel.close();
        }
    }

    public boolean isHandshakeComplete() {
        return this.handshakeComplete;
    }

    public SSLEngine getSslEngine() {
        return sslEngine;
    }
}
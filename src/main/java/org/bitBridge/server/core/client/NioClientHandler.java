/**
 * Copyright 2026 Cristobal Roman Zamora
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitBridge.server.core.client;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.NioServerEngine;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.server.core.auth.AuthStrategy;
import org.bitBridge.server.core.auth.AuthStrategyFactory;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.HandshakeMessage;
import org.bitBridge.shared.memory.DirectBufferPool;
import org.bitBridge.shared.network.ProtocolService;
import org.bitBridge.shared.network.tls.NioTlsHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_DATA;
import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_REQUEST;

public class NioClientHandler implements BitBridgeClient {
    private final SocketChannel channel;
    private final ServerContext context;
    private SelectionKey selectionKey;

    private ByteBuffer payloadBuffer = null;
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(8);
    private boolean readingHeader = true;

    private ClientInfo info;
    private volatile boolean isShuttingDown = false;
    public String nick;
    private volatile boolean authenticated = false;

    // La cola ahora almacena bytes puros de aplicación (Texto plano).
    // El cifrado ocurre secuencialmente al momento de inyectar al socket channel.
    private final ConcurrentLinkedQueue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isWriting = new AtomicBoolean(false);
    private NioTlsHandler tlsHandler;

    public NioClientHandler(SocketChannel channel, ServerContext context) {
        this.channel = channel;
        this.context = context;
        if (context.getSslContext() != null) {
            Logger.logInfo("[INIT] Detectado SSLContext en servidor. Esperando asignación de NioTlsHandler...");
        } else {
            Logger.logWarn("[INIT] Sin contexto SSL configurado. Operando en modo texto plano (Fallback).");
        }
    }

    public void setSelectionKey(SelectionKey key) {
        this.selectionKey = key;
    }

    public void setTlsHandler(NioTlsHandler tlsHandler) {
        this.tlsHandler = tlsHandler;
        // Al inyectar el handler desde el motor del servidor, ejecutamos un disparo inicial reactivo
        // para consumir de inmediato el ClientHello si ya está disponible en el buffer del kernel.
        try {
            processRead();
        } catch (IOException e) {
            Logger.logError("❌ Error en disparo TLS inicial del cliente: " + e.getMessage());
            shutDown();
        }
    }

    public void processRead() throws IOException {
        if (isShuttingDown) return;

        String idCliente = (getNick() != null) ? getNick() : "Canal-" + channel.hashCode();
        Logger.logInfo("[NIO-READ] Trazando evento de lectura en selector para: " + idCliente);

        ByteBuffer datosDescifrados = this.tlsHandler.handleReadEvent();

        // Si handleReadEvent() procesó tramas de control de handshake o buffers parciales devolverá null
        if (datosDescifrados != null && datosDescifrados.hasRemaining()) {
            procesarBytesTextoPlano(datosDescifrados);
        }
    }

    private void procesarBytesTextoPlano(ByteBuffer appIn) throws IOException {
        Logger.logInfo("[PARSE-PLANO] Analizando flujo descifrado. Bytes disponibles: " + appIn.remaining());

        while (appIn.hasRemaining()) {
            if (readingHeader) {
                while (headerBuffer.hasRemaining() && appIn.hasRemaining()) {
                    headerBuffer.put(appIn.get());
                }

                if (headerBuffer.hasRemaining()) {
                    return;
                }

                headerBuffer.flip();
                int jsonSize = headerBuffer.getInt();
                int typeSize = headerBuffer.getInt();
                int fullPacketSize = 8 + typeSize + jsonSize;

                if (jsonSize < 0 || typeSize < 0 || typeSize > 128 || fullPacketSize > 50 * 1024 * 1024) {
                    headerBuffer.clear();
                    throw new IOException("Protocol Desync en TLS plano: Estructura inválida.");
                }

                DirectBufferPool.BufferType poolSugerido = null;
                if (fullPacketSize <= DirectBufferPool.getCapacityForType(DirectBufferPool.BufferType.MESSAGE)) {
                    poolSugerido = DirectBufferPool.BufferType.MESSAGE;
                } else if (fullPacketSize <= DirectBufferPool.getCapacityForType(DirectBufferPool.BufferType.DIRECTORY)) {
                    poolSugerido = DirectBufferPool.BufferType.DIRECTORY;
                } else if (fullPacketSize <= DirectBufferPool.getCapacityForType(DirectBufferPool.BufferType.TRANSFER)) {
                    poolSugerido = DirectBufferPool.BufferType.TRANSFER;
                }

                if (poolSugerido != null) {
                    payloadBuffer = DirectBufferPool.acquire(poolSugerido, 50);
                }

                if (payloadBuffer == null) {
                    payloadBuffer = ByteBuffer.allocateDirect(fullPacketSize);
                } else {
                    payloadBuffer.clear().limit(fullPacketSize);
                }

                headerBuffer.rewind();
                payloadBuffer.put(headerBuffer);
                readingHeader = false;
            }

            while (payloadBuffer.hasRemaining() && appIn.hasRemaining()) {
                payloadBuffer.put(appIn.get());
            }

            if (payloadBuffer.hasRemaining()) {
                return;
            }

            payloadBuffer.flip();
            byte[] data = new byte[payloadBuffer.remaining()];
            payloadBuffer.get(data);

            if (payloadBuffer.isDirect()) {
                DirectBufferPool.release(payloadBuffer);
            }

            payloadBuffer = null;
            headerBuffer.clear();
            readingHeader = true;

            evaluarYProcesarPayload(data);
        }
    }

    private boolean evaluarYProcesarPayload(byte[] data) {
        if (!authenticated) {
            onMessageComplete(data);
            return true;
        } else {
            Thread.ofVirtual().start(() -> onMessageComplete(data));
            return false;
        }
    }

    private void onMessageComplete(byte[] data) {
        try {
            Communication comm = ProtocolService.fromBytes(data);
            Logger.logInfo("[BUSINESS-LOGIC] Tipo de comunicación mapeada: " + comm.getClass().getSimpleName());

            if (!authenticated) {
                handleAuthentication(comm);
            } else {
                context.getDispatcher().dispatch(this, comm, context);
            }
        } catch (Exception e) {
            Logger.logError("❌ Error crítico procesando payload serializado: " + e.getMessage());
        }
    }

    private void handleAuthentication(Communication comm) throws Exception {
        if (!(comm instanceof HandshakeMessage handshake)) {
            throw new IOException("Protocol Desync: El primer paquete debe ser obligatoriamente HandshakeMessage.");
        }

        AuthStrategy strategy = AuthStrategyFactory.getStrategy(handshake.getPurpose());
        strategy.authenticate(this, handshake, this.context);
        this.authenticated = true;
        Logger.logInfo("[AUTH] Autenticación completada con éxito. Nick asignado: " + getNick());
    }

    public void sendSharedBuffer(ByteBuffer buffer, AtomicInteger refCount) {
        drainWriteQueue();
    }

    public void sendComunicacion(Communication comm) {
        if (isShuttingDown) return;

        Logger.logInfo("[WRITE-PIPELINE] Solicitado envío de paquete: " + comm.getClass().getSimpleName() + " hacia " + getNick());
        try {
            ByteBuffer appOut = ProtocolService.toNioBuffer(comm, 50);
            if (appOut == null) {
                Logger.logError("[WRITE-PIPELINE] ProtocolService devolvió un buffer nulo.");
                return;
            }

            // 🎯 CORREGIDO: Encolamos la trama de aplicación limpia. El cifrado se delegará
            // de forma atómica y lineal dentro del hilo único de drainWriteQueue().
            writeQueue.offer(appOut);
            drainWriteQueue();
        } catch (IOException e) {
            Logger.logError("❌ Error crítico encolando paquete saliente: " + e.getMessage());
        }
    }

    private void drainWriteQueue() {
        if (isWriting.compareAndSet(false, true)) {
            Logger.logInfo("[DRAIN-QUEUE] Levantando ejecutor de vaciado asíncrono (Hilo Virtual)...");
            Thread.ofVirtual().start(() -> {
                try {
                    ByteBuffer appBuf;
                    int packCount = 0;
                    while ((appBuf = writeQueue.poll()) != null) {
                        packCount++;

                        ByteBuffer bufAEnviar;
                        // 🎯 CORREGIDO: Evaluamos dinámicamente el estado real del túnel TLS del handler.
                        if (context.getSslContext() != null && tlsHandler != null && tlsHandler.isHandshakeComplete()) {
                            Logger.logInfo(String.format("[DRAIN-QUEUE] [Paquete %d] Cifrando payload plano con SSLEngine...", packCount));
                            bufAEnviar = tlsHandler.cifrar(appBuf);
                        } else {
                            bufAEnviar = appBuf;
                        }

                        int bytesAEnviar = bufAEnviar.remaining();
                        Logger.logInfo(String.format("[DRAIN-QUEUE] [Paquete %d] Escribiendo %d bytes en el canal físico...", packCount, bytesAEnviar));

                        while (bufAEnviar.hasRemaining()) {
                            int written = channel.write(bufAEnviar);
                            if (written > 0) {
                                context.getServer().getStats().recordBytes(written);
                            }
                            if (written == 0) {
                                Thread.yield();
                            }
                        }
                        Logger.logInfo(String.format("[DRAIN-QUEUE] [Paquete %d] Transferencia física exitosa.", packCount));
                    }
                } catch (IOException e) {
                    Logger.logError("❌ Error de escritura de red en canal físico. Forzando desmantelamiento: " + e.getMessage());
                    shutDown();
                } finally {
                    isWriting.set(false);
                    if (!writeQueue.isEmpty()) drainWriteQueue();
                }
            });
        }
    }

    public void closeConnection() {
        shutDown();
    }

    public void shutDown() {
        synchronized (this) {
            if (isShuttingDown) return;
            isShuttingDown = true;
        }

        String currentNick = (nick != null) ? nick : "Sesión-Desconocida";
        Logger.logWarn("⚠️ [SHUTDOWN] Iniciando desmantelamiento atómico de recursos de red para: " + currentNick);

        try {
            boolean esSesionDeDatos = isSessionId(currentNick);

            context.registry().removeClient(this);

            if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                engine.unregisterChannel(channel);
            }

            if (payloadBuffer != null && payloadBuffer.isDirect()) {
                DirectBufferPool.release(payloadBuffer);
                payloadBuffer = null;
            }

            int purgados = 0;
            ByteBuffer pendingWriteBuf;
            while ((pendingWriteBuf = writeQueue.poll()) != null) {
                purgados++;
                if (pendingWriteBuf.isDirect()) {
                    DirectBufferPool.release(pendingWriteBuf);
                }
            }

            if (channel != null && channel.isOpen()) {
                channel.close();
            }

            if (!esSesionDeDatos && authenticated) {
                context.getServer().broadcastMessage(currentNick + " se ha desconectado del servidor.", this);
            }

            if (esSesionDeDatos) {
                context.transferManager().removeSession(currentNick);
            }

            context.getServer().updateClient();

        } catch (IOException e) {
            Logger.logError("❌ Fallo crítico durante el desmantelamiento final de recursos: " + e.getMessage());
        }
    }

    @Override public String getRemoteAddress() throws IOException { return channel.getRemoteAddress().toString(); }
    @Override public ClientInfo getInfo() { return this.info; }
    @Override public void setInfo(ClientInfo info) { this.info = info; }
    @Override public SocketChannel getSocketChannel() { return channel; }
    @Override public String getNick() { return (info != null) ? info.getNick() : this.nick; }
    @Override public ReadableByteChannel getReadableChannel() { return this.channel; }
    @Override public WritableByteChannel getWritableChannel() { return this.channel; }

    private boolean isSessionId(String nick) {
        return nick != null && (nick.startsWith(PREFIX_REQUEST) || nick.startsWith(PREFIX_DATA));
    }
}
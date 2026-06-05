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
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;
import org.bitBridge.shared.core.comunication.model.basic.MessageAck;
import org.bitBridge.shared.memory.DirectBufferPool;
import org.bitBridge.shared.network.ProtocolService;

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

    private final ConcurrentLinkedQueue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isWriting = new AtomicBoolean(false);

    public NioClientHandler(SocketChannel channel, ServerContext context) {
        this.channel = channel;
        this.context = context;
    }

    public void setSelectionKey(SelectionKey key) {
        this.selectionKey = key;
    }

    public void processRead() {
        if (isShuttingDown) return;

        try {
            int bytesRead;
            while ((bytesRead = channel.read(readingHeader ? headerBuffer : payloadBuffer)) > 0) {

                if (readingHeader) {
                    if (headerBuffer.hasRemaining()) {
                        return; // Faltan bytes del header, esperamos al siguiente evento de selección
                    }

                    headerBuffer.flip();
                    int jsonSize = headerBuffer.getInt();
                    int typeSize = headerBuffer.getInt();
                    int fullPacketSize = 8 + typeSize + jsonSize;

                    // Sanity Check contra paquetes corruptos o ataques maliciosos
                    if (jsonSize < 0 || typeSize < 0 || typeSize > 128 || fullPacketSize > 50 * 1024 * 1024) {
                        throw new IOException("Protocol Desync: Estructura de paquete inválida. Tamaño: " + fullPacketSize);
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
                    }

                    payloadBuffer.limit(fullPacketSize);
                    headerBuffer.rewind();
                    payloadBuffer.put(headerBuffer); // Conservamos los bytes de control
                    readingHeader = false;

                    continue; // Continuamos para aprovechar el resto del stream
                }

                if (payloadBuffer.hasRemaining()) {
                    return; // Cuerpo incompleto, esperar más datos de red
                }

                // --- PROCESAMIENTO DE PAQUETE COMPLETO ---
                payloadBuffer.flip();
                byte[] data = new byte[payloadBuffer.remaining()];
                payloadBuffer.get(data);

                if (payloadBuffer.isDirect()) {
                    DirectBufferPool.release(payloadBuffer);
                }

                // Reset estructural antes de la bifurcación lógica
                payloadBuffer = null;
                headerBuffer.clear();
                readingHeader = true;

                // 🚨 MITIGACIÓN DE RACE CONDITION: Decisión atómica de hilos según autenticación
                boolean abortarCicloInmediato = evaluarYProcesarPayload(data);

                if (abortarCicloInmediato) {
                    return; // Forzamos la salida del bucle de red para asentar el Handshake.
                }
            }

            if (bytesRead == -1) {
                throw new IOException("End of Stream (EOF) alcanzado.");
            }

        } catch (IOException e) {
            shutDown();
        }
    }

    /**
     * Evalúa el estado del canal. Si no está autenticado, procesa sincrónicamente en el hilo de red
     * y solicita congelar el buffer TCP para evitar absorber tramas posteriores prematuramente.
     *
     * @return true si se debe abortar el bucle inmediato de lectura, false para seguir iterando de forma asíncrona.
     */
    private boolean evaluarYProcesarPayload(byte[] data) {
        if (!authenticated) {
            // Ejecución síncrona: El hilo del sub-reactor procesa el HandshakeMessage mutando el estado inmediatamente
            onMessageComplete(data);
            return true; // Abortar el bucle `while`. Los bytes residuales se procesarán en el siguiente ciclo del Selector.
        } else {
            // Flujo normal optimizado: Despacho asíncrono y ultra-rápido usando Hilos Virtuales
            Thread.ofVirtual().start(() -> onMessageComplete(data));
            return false; // Continuar leyendo del canal en este mismo tick de red
        }
    }

    private void onMessageComplete(byte[] data) {
        try {
            Communication comm = ProtocolService.fromBytes(data);
            if (!authenticated) {
                handleAuthentication(comm);
            } else {
                context.getDispatcher().dispatch(this, comm, context);
            }
        } catch (Exception e) {
            Logger.logError("Error procesando mensaje de " + getNick() + ": " + e.getMessage());
        }
    }

    private void handleAuthentication(Communication comm) throws Exception {
        if (!(comm instanceof HandshakeMessage handshake)) {
            throw new IOException("Protocol Desync: El primer paquete debe ser obligatoriamente HandshakeMessage.");
        }

        AuthStrategy strategy = AuthStrategyFactory.getStrategy(handshake.getPurpose());
        strategy.authenticate(this, handshake, this.context);

        this.authenticated = true;
    }

    public void sendSharedBuffer(ByteBuffer buffer, AtomicInteger refCount) {
        drainWriteQueue();
    }

    public void sendComunicacion(Communication comm) {
        if (isShuttingDown) return;

        ByteBuffer buffer = null;
        try {
            buffer = ProtocolService.toNioBuffer(comm, 50);
            if (buffer == null) {
                if (!(comm instanceof MessageAck)) return;
                buffer = ProtocolService.toNioBuffer(comm, 500);
                if (buffer == null) return;
            }

            writeQueue.offer(buffer);
            drainWriteQueue();
        } catch (IOException e) {
            Logger.logError("Error serializando paquete saliente: " + e.getMessage());
        }
    }

    private void drainWriteQueue() {
        if (isWriting.compareAndSet(false, true)) {
            Thread.ofVirtual().start(() -> {
                try {
                    ByteBuffer buf;
                    while ((buf = writeQueue.poll()) != null) {
                        try {
                            while (buf.hasRemaining()) {
                                int written = channel.write(buf);
                                if (written == 0) {
                                    Thread.yield();
                                }
                            }
                        } finally {
                            if (buf.isDirect()) {
                                DirectBufferPool.release(buf);
                            }
                        }
                    }
                } catch (IOException e) {
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

        try {
            String currentNick = (nick != null) ? nick : "Unknown";
            boolean esSesionDeDatos = isSessionId(currentNick);

            context.registry().removeClient(this);

            if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                engine.unregisterChannel(channel);
            }

            if (payloadBuffer != null && payloadBuffer.isDirect()) {
                DirectBufferPool.release(payloadBuffer);
                payloadBuffer = null;
            }

            ByteBuffer pendingWriteBuf;
            while ((pendingWriteBuf = writeQueue.poll()) != null) {
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
            Logger.logError("Fallo durante el desmantelamiento de recursos de " + nick + ": " + e.getMessage());
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
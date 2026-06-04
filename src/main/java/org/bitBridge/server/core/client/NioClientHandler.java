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
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.Logger;
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

    // Estado de lectura (Controlado estrictamente por el hilo del SubReactor)
    private ByteBuffer payloadBuffer = null;
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(8);
    private boolean readingHeader = true;

    private ClientInfo info;
    private volatile boolean isShuttingDown = false; // 🚨 Corregido: Volatile para visibilidad entre hilos
    public String nick;
    private boolean authenticated = false;

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
        // 🚨 SEGURIDAD: Evitar lecturas concurrentes si el socket está en proceso de cierre
        if (isShuttingDown) return;

        try {
            // 🚨 CORREGIDO: Eliminamos el while(true) ciego.
            // Leemos del canal en un bucle controlado por la existencia real de bytes.
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

                    // Fallback si el pool está saturado o el tamaño excede los límites estándar
                    if (payloadBuffer == null) {
                        payloadBuffer = ByteBuffer.allocateDirect(fullPacketSize);
                    }

                    payloadBuffer.limit(fullPacketSize);
                    headerBuffer.rewind();
                    payloadBuffer.put(headerBuffer); // Conservamos los bytes de control
                    readingHeader = false;

                    // Continuamos el bucle inmediato para aprovechar si el OS ya tiene los bytes del cuerpo en buffer
                    continue;
                }

                // Verificación del cuerpo del mensaje
                if (payloadBuffer.hasRemaining()) {
                    return; // El cuerpo está incompleto, conservamos estado y esperamos más datos de red
                }

                // --- PROCESAMIENTO DE PAQUETE COMPLETO ---
                payloadBuffer.flip();
                byte[] data = new byte[payloadBuffer.remaining()];
                payloadBuffer.get(data);

                // Liberación preventiva e inmediata de la memoria Off-Heap antes de delegar la lógica
                if (payloadBuffer.isDirect()) {
                    DirectBufferPool.release(payloadBuffer);
                }

                // Reset de estados para la siguiente trama TCP antes de instanciar el hilo asíncrono
                payloadBuffer = null;
                headerBuffer.clear();
                readingHeader = true;

                // Delegación limpia a la arquitectura de Hilos Virtuales
                final byte[] finalData = data;
                Thread.ofVirtual().start(() -> onMessageComplete(finalData));
            }

            // Si el canal retorna -1, el cliente cerró el socket limpiamente (EOF)
            if (bytesRead == -1) {
                throw new IOException("End of Stream (EOF) alcanzado.");
            }

        } catch (IOException e) {
            shutDown();
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
        if (!(comm instanceof Mensaje mensaje)) throw new IOException("Protocolo de autenticación inválido");

        String contenido = mensaje.getContenido();

        if (isSessionId(contenido)) {
            this.nick = contenido;
            this.authenticated = true;
            context.getTransferManager().registerReceptor(this.nick, this);
            Logger.logInfo("[AUTH] Canal de datos verificado y acoplado: " + this.nick);
        } else {
            this.nick = context.getServer().getUniqueNick(contenido);
            this.authenticated = true;
            context.getServer().registerClient(this, 8080);
            sendComunicacion(new Mensaje("Conectado como: " + nick));
            context.getServer().broadcastMessage("[ " + nick + "] Se ha unido al sistema.", this);
        }
    }

    public void sendSharedBuffer(ByteBuffer buffer, AtomicInteger refCount) {
        // Reservado para streaming masivo / optimizaciones futuras
        drainWriteQueue();
    }

    public void sendComunicacion(Communication comm) {
        if (isShuttingDown) return;

        ByteBuffer buffer = null;
        try {
            buffer = ProtocolService.toNioBuffer(comm, 50);
            if (buffer == null) {
                if (!(comm instanceof MessageAck)) return; // Descarte seguro por saturación
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
                                    // El buffer TCP del OS está lleno, cedemos cpu momentáneamente
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
                    // Doble verificación atómica por si entraron elementos en la ventana de cierre del ciclo
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

            // 1. Remover del registro global antes de desmantelar canales
            context.registry().removeClient(this);

            if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                engine.unregisterChannel(channel);
            }

            // 2. Liberación obligatoria de buffers de lectura huerfanos en memoria nativa
            if (payloadBuffer != null && payloadBuffer.isDirect()) {
                DirectBufferPool.release(payloadBuffer);
                payloadBuffer = null;
            }

            // 🚨 CORREGIDO: Drenar y liberar todos los buffers del pool que quedaron atrapados en la cola de salida
            ByteBuffer pendingWriteBuf;
            while ((pendingWriteBuf = writeQueue.poll()) != null) {
                if (pendingWriteBuf.isDirect()) {
                    DirectBufferPool.release(pendingWriteBuf);
                }
            }

            // 3. Destrucción física del descriptor de archivo (Socket TCP)
            if (channel != null && channel.isOpen()) {
                channel.close();
            }

            // 4. Notificaciones de Ciclo de Vida del Clúster
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
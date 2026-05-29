package org.bitBridge.server.core.client;



import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.NioServerEngine;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.CommunicationType;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.Mensaje;
import org.bitBridge.shared.memory.DirectBufferPool;
import org.bitBridge.shared.memory.SharedBufferWrapper;
import org.bitBridge.shared.network.ProtocolService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class NioClientHandler implements BitBridgeClient {
    private final SocketChannel channel;
    private final ServerContext context;
    private SelectionKey selectionKey;

    // Buffers de lectura de estado
    private ByteBuffer payloadBuffer = null;
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(8);
    private boolean readingHeader = true;
    private boolean readingLength = true;
    private ClientInfo info;
    private boolean isShuttingDown = false;
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
        try {
            while (true) {
                if (readingHeader) {
                    int read = channel.read(headerBuffer);
                    if (read == -1) throw new IOException("EOF");
                    if (headerBuffer.hasRemaining()) return; // No hay header completo aún

                    headerBuffer.flip();
                    int jsonSize = headerBuffer.getInt();
                    int typeSize = headerBuffer.getInt();
                    int fullPacketSize = 8 + typeSize + jsonSize;

                    // Validación de locura (Sanity Check)
                    if (jsonSize < 0 || typeSize < 0 || typeSize > 128) {
                        throw new IOException("Protocol Desync: Header inválido. TypeSize: " + typeSize);
                    }

                    //Logger.logInfo(String.valueOf(fullPacketSize));
                    DirectBufferPool.BufferType poolSugerido;

                    if (fullPacketSize <= DirectBufferPool.getCapacityForType(DirectBufferPool.BufferType.MESSAGE)) {
                        //Logger.logInfo("Buffer Mensaje");
                        poolSugerido = DirectBufferPool.BufferType.MESSAGE;
                    }
                    else if (fullPacketSize <= DirectBufferPool.getCapacityForType(DirectBufferPool.BufferType.DIRECTORY)) {
                        Logger.logInfo("Buffer Direcotrio");
                        poolSugerido = DirectBufferPool.BufferType.DIRECTORY;
                    }
                    else if (fullPacketSize <= DirectBufferPool.getCapacityForType(DirectBufferPool.BufferType.TRANSFER)) {
                        poolSugerido = DirectBufferPool.BufferType.TRANSFER;
                    } else {
                        // Si es más grande que nuestro pool máximo (ej: 1MB+)
                        payloadBuffer = ByteBuffer.allocateDirect(fullPacketSize);
                        poolSugerido = null;
                    }

                    if (poolSugerido != null) {
                        payloadBuffer = DirectBufferPool.acquire(poolSugerido, 50);
                        // Si el pool está saturado, fallback a allocateDirect
                        if (payloadBuffer == null) {
                            payloadBuffer = ByteBuffer.allocateDirect(fullPacketSize);
                        }
                    }

                    // Usar allocateDirect si es grande, pool si es pequeño
                    /*if (fullPacketSize > 8192) {
                        payloadBuffer = ByteBuffer.allocateDirect(fullPacketSize);
                    } else {
                        payloadBuffer = DirectBufferPool.acquire(50);
                        if (payloadBuffer == null) {
                            payloadBuffer = ByteBuffer.allocateDirect(fullPacketSize);
                        }
                    }*/

                    payloadBuffer.limit(fullPacketSize);
                    headerBuffer.rewind();
                    payloadBuffer.put(headerBuffer); // Metemos los 8 bytes del header
                    readingHeader = false;
                }

                // Lectura del cuerpo
                int readBody = channel.read(payloadBuffer);
                if (readBody == -1) throw new IOException("EOF");

                if (payloadBuffer.hasRemaining()) return; // Cuerpo incompleto

                // --- PROCESAMIENTO ---
                payloadBuffer.flip();
                byte[] data = new byte[payloadBuffer.remaining()];
                payloadBuffer.get(data);

                // Liberar el buffer ANTES de seguir el loop si es del pool
                if (payloadBuffer.isDirect()) {
                    DirectBufferPool.release(payloadBuffer);
                }

                // IMPORTANTE: Reset de variables ANTES de lanzar el hilo
                payloadBuffer = null;
                headerBuffer.clear();
                readingHeader = true;

                // Procesar de forma asíncrona
                final byte[] finalData = data;
                Thread.ofVirtual().start(() -> onMessageComplete(finalData));

                // El loop continúa: si hay más bytes en el socket,
                // channel.read(headerBuffer) los tomará limpiamente.
            }
        } catch (IOException e) {
            if (payloadBuffer != null) DirectBufferPool.release(payloadBuffer);
            shutDown();
        }
    }

    /*public void processRead() {
        //Logger.logInfo(Thread.currentThread().getName());
        try {
            if (readingHeader) {
                int read = channel.read(headerBuffer);
                if (read == -1) throw new IOException("Cliente desconectado");

                if (!headerBuffer.hasRemaining()) {
                    headerBuffer.flip();
                    int jsonSize = headerBuffer.getInt();
                    short typeSize = headerBuffer.getShort();

                    // VALIDACIÓN DE SEGURIDAD
                    // Un JSON de control no debería pesar más de, por ejemplo, 1MB.
                    if (jsonSize <= 0 || jsonSize > 1024 * 1024) {
                        throw new IOException("Paquete corrupto o flujo de datos detectado en canal de control. Tamaño: " + jsonSize);
                    }
                    payloadBuffer = ByteBuffer.allocate(6 + typeSize + jsonSize);
                    // Re-insertamos el header para que fromBytes sea autónomo
                    headerBuffer.flip();
                    payloadBuffer.put(headerBuffer);

                    readingHeader = false;
                }
            }

            if (!readingHeader) {
                int read = channel.read(payloadBuffer);
                if (read == -1) throw new IOException("Cliente desconectado");
// LOG DE DEBUG CRÍTICO

                if (!payloadBuffer.hasRemaining()) {
                    payloadBuffer.flip();
                    byte[] allData = payloadBuffer.array();
                    onMessageComplete(allData);
                    resetBuffers();
                }
            }
        } catch (IOException e) {
            Logger.logError("Error en lectura: " + e.getMessage());
            shutDown();
        }
    }*/

    private void resetBuffers() {
        headerBuffer.clear();
        payloadBuffer = null;
        readingHeader = true;
    }

    private void resumeSelection() {
        if (!isShuttingDown && selectionKey != null && selectionKey.isValid()) {
            selectionKey.interestOps(SelectionKey.OP_READ);
            selectionKey.selector().wakeup(); // Despierta al SubReactor para que vea el cambio
        }
    }

    private void onMessageComplete(byte[] data) {
        //Logger.logInfo(Thread.currentThread().getName());
        try {
            Communication comm = ProtocolService.fromBytes(data);
            if (!authenticated) {
                // FASE 1: Autenticación (Tu lógica original)
                handleAuthentication(comm);
            } else {
                // FASE 2: Ciclo de vida (Dispatcher)

                context.getDispatcher().dispatch(this, comm, context);
            }
        } catch (Exception e) {
            Logger.logError("Error procesando mensaje de " + nick + ": " + e.getMessage());
        }
    }
    private void handleAuthentication(Communication comm) throws Exception {
        if (!(comm instanceof Mensaje mensaje)) throw new IOException("Protocolo inválido");

        String contenido = mensaje.getContenido();
        //Logger.logError(contenido);
        if (isSessionId(contenido)) {
            this.nick = contenido;
            this.authenticated = true;
            context.getServer().registerClient(this, 8080);
        } else {
            this.nick = context.getServer().getUniqueNick(contenido);
            context.getServer().registerClient(this, 8080);
            this.authenticated = true;

            sendComunicacion(new Mensaje("Conectado como: " + nick, CommunicationType.MESSAGE));
            context.getServer().broadcastMessage("[ " + nick + "] Se ha unido al Chat", this);
        }
    }

    public void sendSharedBuffer(ByteBuffer buffer, AtomicInteger refCount) {
        // Añadimos a la cola el buffer duplicado
        //writeQueue.offer(new SharedBufferWrapper(buffer, refCount));
        drainWriteQueue();
    }

    public void sendComunicacion(Communication comm) {

        if (isShuttingDown)return;

        ByteBuffer buffer = null;
        try {  // 1. Preparamos el buffer (fuera del lock)


         buffer = ProtocolService.toNioBuffer(comm,50); // Crea un método que devuelva el ByteBuffer

        if (buffer==null){
            if (comm.getType()!=CommunicationType.ACK){
                //Logger.logWarn("Carga excesiva: Mensaje descartado para " + nick);
                return;
            }
            buffer = ProtocolService.toNioBuffer(comm, 500);
            if (buffer == null) return;
        }


        writeQueue.offer(buffer);

        // 2. Intentamos disparar el proceso de vaciado de cola
        drainWriteQueue();


            //ProtocolService.writeNIO(channel,comm);
        } catch (IOException e) {
            //Logger.logError("Error en envío: " + e.getMessage());
        }
    }

    private void drainWriteQueue() {
        if (isWriting.compareAndSet(false, true)) {
            Thread.ofVirtual().start(() -> {
                try {
                    ByteBuffer buf;
                    while ((buf = writeQueue.poll()) != null) {
                        try {
                            // isWriting garantiza que este es el único hilo escribiendo en este channel
                            while (buf.hasRemaining()) {
                                int written = channel.write(buf);
                                if (written == 0) {
                                    Thread.yield();
                                }
                            }
                        } finally {
                            // LIBERACIÓN GARANTIZADA
                            //DirectBufferPool.release(buf);
                            if (buf.isDirect()) {
                                // Si tu pool tiene lógica para ignorar buffers que no creó, úsalo.
                                // Si no, asegúrate de que el pool pueda manejar esto.
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

    // En NioClientHandler.java al cerrar
    public void closeConnection() {
        try {
            if (context.getNetworkEngine() instanceof NioServerEngine engine) {

                engine.unregisterChannel(channel);
            }
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {}
    }

    public void shutDown() {
        synchronized (this) {
            if (isShuttingDown) return;
            isShuttingDown = true;
        }

        try {
            String currentNick = (nick != null) ? nick : "Unknown";
            boolean esSesionDeDatos = isSessionId(currentNick);

            // 1. ELIMINAR PRIMERO (Fundamental para que updateClient vea la lista real)
            context.registry().removeClient(this);

            // 2. CERRAR CANAL (Para que no entren más processRead)
            if (channel != null && channel.isOpen()) {
                channel.close();
            }

            // 3. NOTIFICAR DESCONEXIÓN (Solo chat)
            if (!esSesionDeDatos && authenticated) {
                context.getServer().broadcastMessage(currentNick + " se ha desconectado", this);
            }

            if (esSesionDeDatos) {
                context.transferManager().removeSession(currentNick);
            }

            // 4. ACTUALIZAR LISTA GLOBAL (Esto ahora enviará la lista sin 'this')
            context.getServer().updateClient();

        } catch (IOException e) {
            Logger.logError("Error en shutDown de " + nick + ": " + e.getMessage());
        }
    }

    @Override
    public String getRemoteAddress() throws IOException {
        return channel.getRemoteAddress().toString();
    }

    @Override
    public ClientInfo getInfo() { return this.info; }

    @Override
    public void setInfo(ClientInfo info) { this.info = info; }

    @Override
    public SocketChannel getSocketChannel() {
        return channel;
    }

    // Ahora getNick() puede venir de la info si quieres
    @Override
    public String getNick() {
        return (info != null) ? info.getNick() : this.nick;
    }

    @Override
    public ReadableByteChannel getReadableChannel() {
        return this.channel; // SocketChannel implementa ReadableByteChannel
    }

    @Override
    public WritableByteChannel getWritableChannel() {
        return this.channel; // SocketChannel implementa WritableByteChannel
    }

    private boolean isSessionId(String nick) {
        return nick != null && (nick.startsWith("SENDER_") || nick.startsWith("FILE_") || nick.startsWith("DIR_"));
    }
}

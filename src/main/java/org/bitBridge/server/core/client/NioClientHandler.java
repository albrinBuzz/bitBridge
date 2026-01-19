package org.bitBridge.server.core.client;



import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.Communication;
import org.bitBridge.shared.CommunicationType;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.Mensaje;
import org.bitBridge.shared.network.ProtocolService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class NioClientHandler implements BitBridgeClient {
    private final SocketChannel channel;
    private final ServerContext context;

    // Buffers de lectura de estado
    private ByteBuffer payloadBuffer = null;
    private ByteBuffer headerBuffer = ByteBuffer.allocate(6);
    private boolean readingHeader = true;
    private boolean readingLength = true;
    private ClientInfo info;
    private boolean isShuttingDown = false;

    public String nick;
    private boolean authenticated = false;
    public NioClientHandler(SocketChannel channel, ServerContext context) {
        this.channel = channel;
        this.context = context;
    }

    public void processRead() {
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
    }

    private void resetBuffers() {
        headerBuffer.clear();
        payloadBuffer = null;
        readingHeader = true;
    }

    private void handleIncomingPacket(byte[] data) {
        try {
            // Aquí usamos tu lógica de GSON pero desde bytes
            Communication comm = ProtocolService.fromBytes(data);
            //context.dispatcher().dispatch(this, comm, context);
        } catch (Exception e) {
            System.err.println("Error procesando paquete: " + e.getMessage());
        }
    }

    private void onMessageComplete(byte[] data) {

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



    public synchronized void sendComunicacion(Communication comm) {
        if (isShuttingDown) return;
        try {
            ProtocolService.writeNIO(channel, comm);
        } catch (IOException e) {
            // Importante: No llamar a shutDown() directamente aquí si ya estamos en ello
            //Logger.logError("Error enviando a " + nick + ": " + e.getMessage());

            shutDown();
        }
    }



    public void send(Communication comm) {
        try {
            // ProtocolService debe devolver un ByteBuffer con [Longitud][Tipo][JSON]
            ProtocolService.writeNIO(channel,comm);
            /*ByteBuffer buffer = ProtocolService.toNioBuffer(comm);

            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }*/
        } catch (IOException e) {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            channel.close();
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

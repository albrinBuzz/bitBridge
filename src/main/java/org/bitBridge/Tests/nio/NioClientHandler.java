package org.bitBridge.Tests.nio;



import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.Communication;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class NioClientHandler {
    private final SocketChannel channel;
    private final ServerContext context;

    // Buffers de lectura de estado
    private ByteBuffer lengthBuffer = ByteBuffer.allocate(4); // Para el Int de longitud
    private ByteBuffer payloadBuffer = null;
    private boolean readingLength = true;

    public NioClientHandler(SocketChannel channel, ServerContext context) {
        this.channel = channel;
        this.context = context;
    }

    public void processRead() {
        try {
            if (readingLength) {
                // Intentar leer los 4 bytes del Int
                int read = channel.read(lengthBuffer);
                if (read == -1) throw new IOException("Cliente desconectado");

                if (!lengthBuffer.hasRemaining()) { // Tenemos el Int completo
                    lengthBuffer.flip();
                    int length = lengthBuffer.getInt();

                    // Preparar el buffer para el JSON (payload)
                    payloadBuffer = ByteBuffer.allocate(length);
                    readingLength = false;
                }
            }

            if (!readingLength) {
                // Intentar leer el cuerpo del JSON
                channel.read(payloadBuffer);

                if (!payloadBuffer.hasRemaining()) { // ¡JSON completo recibido!
                    payloadBuffer.flip();
                    byte[] data = new byte[payloadBuffer.limit()];
                    payloadBuffer.get(data);

                    // Convertir bytes a Objeto y despachar
                    handleIncomingPacket(data);

                    // Resetear para el siguiente mensaje
                    resetBuffers();
                }
            }
        } catch (IOException e) {
            closeConnection();
        }
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

    private void resetBuffers() {
        lengthBuffer.clear();
        payloadBuffer = null;
        readingLength = true;
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
}
package org.bitBridge.server.transfer;


import org.bitBridge.server.client.ClientHandler;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.*;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class FileTransferService {

    private final ServerContext context;
    private static final int BUFFER_SIZE = 128 * 1024; // 64KB

    public FileTransferService(ServerContext context) {
        this.context = context;
    }

    /**
     * Coordina el envío de un archivo individual entre un emisor y un receptor.
     */
    public void handleForwardFile(ClientHandler sender, DataInputStream entrada,FileDirectoryCommunication communication) {
        try {

                String recipientNick = communication.getRecipient();
                long fileSize = communication.getSize();

                Logger.logInfo("Buscando destinatario: " + recipientNick);
                ClientHandler recipient = context.registry().findByNick(recipientNick);

                if (recipient == null) {
                    Logger.logError("No se encontró el destinatario: " + recipientNick);
                    return;
                }

                String sessionId = "FILE_" + new Random().nextInt(1000, 9999);
                FileHandshakeCommunication request = new FileHandshakeCommunication(
                        FileHandshakeAction.SEND_REQUEST, sessionId, communication);

                recipient.sendComunicacion(request);

                Logger.logInfo("Esperando al receptor para: " + sessionId);
                ClientHandler receptor = context.transferManager().waitForReceptor(sessionId, 15);

                if (receptor == null) {
                    Logger.logError("No se encontró conexión de datos del receptor.");
                    return;
                }
            //FileHandshakeAction action=context.transferManager().responseAction(sessionId);
            FileHandshakeAction action=context.transferManager().waitForResponseAction(sessionId, 7);
            ///FileHandshakeAction action = manager.waitForResponseAction(sessionId, 30);

            if (action==FileHandshakeAction.ACCEPT_REQUEST) {

                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.START_TRANSFER, sessionId, communication);

                receptor.sendComunicacion(start);
                sender.sendComunicacion(start);

                long startNIO = System.nanoTime();
                // Transferencia de bytes
                streamBytes(entrada, receptor.getOutputStream(), fileSize);
                long endNIO = System.nanoTime();

                // Cálculo de resultados

                double segundosNIO = (endNIO - startNIO) / 1_000_000_000.0;
                double mbSize =fileSize / (1024.0 * 1024.0);

                Logger.logInfo("NIO Zero-Copy: "+(mbSize / segundosNIO)+"MB/s"+"("+segundosNIO+" seg)");

                // FINALIZACIÓN
                //Object fin = entrada.readObject();
                Object fin = ProtocolService.readFormattedPayload(entrada);
                if (fin instanceof FileHandshakeCommunication com) {
                    receptor.sendComunicacion(com);
                }

                TimeUnit.MILLISECONDS.sleep(250);
                receptor.shutDown();
                sender.shutDown();

            }else {
                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.DECLINE_REQUEST, sessionId, communication);

                sender.sendComunicacion(start);
            }

        } catch (Exception e) {
            Logger.logError("Error en FileTransferService (forwardFile): " + e.getMessage());
        }
    }

    /**
     * Lógica original adaptada para relay de directorios
     */
    public void relayDirectory(FileDirectoryCommunication com, ClientHandler sender, DataInputStream entrada) {
        String recipientNick = com.getRecipient();
        String dirName = com.getName();
        String logId = "[DIR-RELAY-" + new Random().nextInt(1000) + "]";

        ClientHandler recipient = context.registry().findByNick(recipientNick);
        if (recipient == null) {
            Logger.logError(logId + " Receptor '" + recipientNick + "' no encontrado.");
            return;
        }

        try {
            String sessionId = "DIR_" + new Random().nextInt(1000, 9999);

            // 1. Notificar al receptor sobre la solicitud (JSON vía ProtocolService dentro de sendComunicacion)
            recipient.sendComunicacion(new FileHandshakeCommunication(
                    FileHandshakeAction.SEND_REQUEST, sessionId, com));

            // 2. Esperar al canal de datos del receptor
            ClientHandler dataReceiver = context.transferManager().waitForReceptor(sessionId, 20);
            if (dataReceiver == null) {
                Logger.logError(logId + " Timeout esperando conexión de datos del receptor.");
                return;
            }

            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 7);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {
                // Confirmación de inicio a ambos (JSON)
                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.START_TRANSFER, sessionId, com);

                dataReceiver.sendComunicacion(start);
                sender.sendComunicacion(start);

                DataOutputStream outDestino = new DataOutputStream(dataReceiver.getOutputStream());
                boolean transferenciaActiva = true;

                // --- BUCLE DE RELAY HÍBRIDO ---
                while (transferenciaActiva && !sender.getClientSocket().isClosed()) {
                    // Leer el siguiente comando del emisor (JSON)
                    Communication object = ProtocolService.readFormattedPayload(entrada);

                    if (object instanceof FileDirectoryCommunication archivoMeta) {
                        // Re-enviar metadatos al destinatario (JSON)
                        ProtocolService.writeFormattedPayload(outDestino, archivoMeta);

                        // Leer y re-enviar el String UTF de la ruta relativa
                        String rutaRelativa = entrada.readUTF();
                        outDestino.writeUTF(rutaRelativa);
                        outDestino.flush();

                        if (archivoMeta.isDirectory()) {
                            continue; // Es solo una carpeta, no hay bytes de contenido
                        }

                        // PUENTE DE BYTES: Transferencia cruda de archivo a archivo
                        long fileSize = archivoMeta.getSize();
                        long totalRead = 0;
                        byte[] buffer = new byte[BUFFER_SIZE];

                        while (totalRead < fileSize) {
                            int toRead = (int) Math.min(buffer.length, fileSize - totalRead);
                            int bytesRead = entrada.read(buffer, 0, toRead);
                            if (bytesRead == -1) break;

                            outDestino.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                        }
                        outDestino.flush();

                    } else if (object instanceof FileHandshakeCommunication respuesta) {
                        if (respuesta.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                            // Notificar fin al receptor y cerrar bucle
                            ProtocolService.writeFormattedPayload(outDestino, respuesta);
                            outDestino.flush();
                            transferenciaActiva = false;
                        }
                    }
                }
                Logger.logInfo(logId + " Retransmisión de directorio finalizada.");

            } else {
                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
            }

        } catch (Exception e) {
            Logger.logError(logId + " Error en relay: " + e.getMessage());
        }
    }

    private void streamBytes(DataInputStream in, DataOutputStream out, long totalSize) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long totalRead = 0;
        while (totalRead < totalSize) {
            int toRead = (int) Math.min(buffer.length, totalSize - totalRead);
            int read = in.read(buffer, 0, toRead);
            if (read == -1) break;
            out.write(buffer, 0, read);
            totalRead += read;
            context.stats().recordBytes(read);
        }
        out.flush();
    }

    /*private void streamBytes(DataInputStream entrada, OutputStream salida, long fileSize) throws IOException {
        // Convertimos los streams existentes a canales de NIO
        ReadableByteChannel source = Channels.newChannel(entrada);
        WritableByteChannel dest = Channels.newChannel(salida);

        // Para puentear dos sockets, usamos un ByteBuffer directo (fuera del Heap de Java)
        // 128KB o 256KB es un tamaño excelente para transferencia entre sockets
        ByteBuffer buffer = ByteBuffer.allocateDirect(256 * 1024);

        long totalTransferred = 0;
        while (totalTransferred < fileSize) {
            buffer.clear();
            int read = source.read(buffer);
            if (read == -1) break;

            buffer.flip(); // Prepara el buffer para ser leído y escrito al destino
            while (buffer.hasRemaining()) {
                dest.write(buffer);
            }
            totalTransferred += read;
        }
    }*/
}
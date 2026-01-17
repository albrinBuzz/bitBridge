package org.bitBridge.server.transfer;


import org.bitBridge.server.client.ClientHandler;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.FileDirectoryCommunication;
import org.bitBridge.shared.FileHandshakeAction;
import org.bitBridge.shared.FileHandshakeCommunication;
import org.bitBridge.shared.Logger;

import java.io.*;
import java.net.SocketException;
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
    public void handleForwardFile(ClientHandler sender, ObjectInputStream entrada,FileDirectoryCommunication communication) {
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

                // Transferencia de bytes
                streamBytes(entrada, receptor.getOutputStream(), fileSize);

                // FINALIZACIÓN
                Object fin = entrada.readObject();
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
    public void relayDirectory(FileDirectoryCommunication com, ClientHandler sender, ObjectInputStream entrada) {
        String recipientNick = com.getRecipient();
        String dirName = com.getName();

        // Log con ID para rastreo
        String logId = "[DIR-TRANSFER-" + new Random().nextInt(1000) + "]";
        Logger.logInfo(logId + " Solicitud de directorio: '" + dirName + "' para " + recipientNick);

        ClientHandler recipient = context.registry().findByNick(recipientNick);
        if (recipient == null) {
            Logger.logError(logId + " Receptor no encontrado.");
            return;
        }

        try {
            String sessionId = "DIR_" + new Random().nextInt(1000, 9999);

            // 1. Handshake inicial
            recipient.sendComunicacion(new FileHandshakeCommunication(
                    FileHandshakeAction.SEND_REQUEST, sessionId, com));

            // 2. Espera al canal de datos
            ClientHandler dataReceiver = context.transferManager().waitForReceptor(sessionId, 20);
            if (dataReceiver == null) {
                Logger.logError(logId + " Timeout esperando canal de datos.");
                return;
            }

            FileHandshakeAction action=context.transferManager().waitForResponseAction(sessionId, 7);
            ///FileHandshakeAction action = manager.waitForResponseAction(sessionId, 30);

            if (action==FileHandshakeAction.ACCEPT_REQUEST) {

                // 3. Notificar inicio
                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.START_TRANSFER, sessionId, com);

                dataReceiver.sendComunicacion(start);
                sender.sendComunicacion(start);

                byte[] buffer = new byte[BUFFER_SIZE];
                long totalBytesSentTotal = 0;

                // --- TU LÓGICA ORIGINAL MANTENIDA ---
                while (sender.getClientSocket().isConnected()) {
                    Object object = entrada.readObject();

                    if (object instanceof FileDirectoryCommunication archivo) {
                        // Orden de lectura igual al envío del cliente
                        String nombreArchivo = entrada.readUTF();

                        dataReceiver.sendComunicacion(archivo);
                        dataReceiver.getOutputStream().writeUTF(nombreArchivo);
                        dataReceiver.getOutputStream().flush();

                        if (archivo.isDirectory() && archivo.getSize() == 0) {
                            continue;
                        }

                        long fileSize = archivo.getSize();
                        long totalBytesRead = 0;
                        int bytesRead;

                        // Puente de bytes exacto para evitar OptionalDataException

                        streamBytes(entrada, dataReceiver.getOutputStream(), fileSize);


                    } else if (object instanceof FileHandshakeCommunication respuesta) {
                        if (respuesta.getAction().equals(FileHandshakeAction.TRANSFER_DONE)) {
                            FileHandshakeCommunication requestCom = new FileHandshakeCommunication(
                                    FileHandshakeAction.TRANSFER_DONE
                            );
                            dataReceiver.sendComunicacion(requestCom);
                            break;
                        }
                    }
                }
                // --- FIN DE LÓGICA ORIGINAL ---

                context.stats().recordBytes(totalBytesSentTotal);
                Logger.logInfo(logId + " ¡Éxito! Directorio enviado correctamente.");

                dataReceiver.shutDown();
                sender.shutDown();
            }else {
                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.DECLINE_REQUEST, sessionId, com);

                sender.sendComunicacion(start);
            }

        } catch (SocketException e) {
            Logger.logError(logId + " Conexión perdida: " + e.getMessage());
        } catch (Exception e) {
            Logger.logError(logId + " Error crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void streamBytes(ObjectInputStream in, ObjectOutputStream out, long totalSize) throws IOException {
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
}
package org.bitBridge.server.transfer;

import org.bitBridge.server.core.NioServerEngine;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.*;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.model.basic.FileDirectoryCommunication;
import org.bitBridge.shared.core.comunication.FileHandshakeAction;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;
import org.bitBridge.shared.memory.BufferPool;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_DATA;

public class FileTransferService {

    private final ServerContext context;
    private static final int BRIDGE_BUFFER_SIZE = 128 * 1024;

    public FileTransferService(ServerContext context) {
        this.context = context;
    }


    public void handleForwardFile(FileDirectoryCommunication com, BitBridgeClient sender, String sessionId) {
        String logId = "[FILE-RELAY-" + sessionId + "]";
        BitBridgeClient recipient = context.registry().findByNick(com.getRecipient());
        if (recipient == null) {
            Logger.logError(logId + " Receptor no encontrado en el registro: " + com.getRecipient());
            return;
        }

        try {
            // 1. Notificar al hilo de control del receptor que hay un archivo entrante
            recipient.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SEND_REQUEST, sessionId, com));

            // 2. Esperar que el socket secundario de datos del RECEPTOR se conecte al pool
            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 30);
            if (dataReceiver == null) {
                Logger.logError(logId + " Abortando: El socket secundario del receptor nunca llegó.");
                return;
            }

            // 3. Esperar la confirmación de aceptación del hilo de control del receptor
            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 7);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {
                // Construir el handshake de inicio oficial con el token unificado
                FileHandshakeCommunication start = new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId, com);

                // Desbloquear los canales de datos de ambos extremos
                dataReceiver.sendComunicacion(start);
                sender.sendComunicacion(start);

                // Pasar canales NIO a modo Bloqueante para la transferencia lineal segura en el Relay
                if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                    engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                    engine.unregisterChannel((SocketChannel) dataReceiver.getReadableChannel());
                    ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                    ((SocketChannel) dataReceiver.getReadableChannel()).configureBlocking(true);
                }

                // ====================================================================
                // 🚨 LOGICA EXTRÁIDA DIRECTAMENTE DE RELAY_DIRECTORY
                // ====================================================================

                // El emisor (Sender) enviará inmediatamente el objeto FileDirectoryCommunication específico
                byte[] packetData = ProtocolService.readHandshakePacket(sender);
                Communication object = ProtocolService.fromBytes(packetData);

                if (object instanceof FileDirectoryCommunication meta) {
                    // Reenviar los metadatos al receptor por el canal de datos
                    dataReceiver.sendComunicacion(meta);

                    // Leer la decisión del receptor basada en sus metadatos locales (SKIP, PROCESS_DELTAS o START_TRANSFER)
                    byte[] receptorAckRaw = ProtocolService.readHandshakePacket(dataReceiver);
                    Communication receptorResponse = ProtocolService.fromBytes(receptorAckRaw);

                    if (receptorResponse instanceof FileHandshakeCommunication resp) {
                        FileHandshakeAction accionReceptor = resp.getAction();

                        // --- CASO 1: OMITIR ARCHIVO (SKIP) ---
                        if (accionReceptor == FileHandshakeAction.SKIP_FILE) {
                            Logger.logInfo(logId + " [RSYNC] Archivo idéntico en destino. Omitiendo transmisión.");
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                        }

                        // --- CASO 2: MODO DIFERENCIAL RSYNC ---
                        else if (accionReceptor == FileHandshakeAction.PROCESS_DELTAS) {
                            Logger.logWarn(logId + " [RSYNC-RELAY] -> Entrando en modo diferencial para archivo único: " + meta.getName());
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                            // 1. Firmas: Receptor -> Servidor -> Emisor
                            byte[] signaturesRaw = ProtocolService.readHandshakePacket(dataReceiver);
                            sender.getWritableChannel().write(ByteBuffer.wrap(signaturesRaw));

                            // 2. Deltas: Emisor -> Servidor -> Receptor
                            byte[] deltasRaw = ProtocolService.readHandshakePacket(sender);
                            dataReceiver.getWritableChannel().write(ByteBuffer.wrap(deltasRaw));

                            // 3. Confirmación de reconstrucción exitosa: Receptor -> Servidor -> Emisor
                            byte[] finalAck = ProtocolService.readHandshakePacket(dataReceiver);
                            sender.getWritableChannel().write(ByteBuffer.wrap(finalAck));
                        }

                        // --- CASO 3: TRASPASO COMPLETO (ZERO-COPY) ---
                        else if (accionReceptor == FileHandshakeAction.START_TRANSFER) {
                            Logger.logInfo(logId + " [TRANSFERENCIA LIMPIA] Enviando flujo completo...");
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                            // Transferencia masiva cruda de bytes
                            bridgeSocketChannelsNoShutdown(sender, dataReceiver, meta.getSize());

                            // Reenviar el ACK final de confirmación de escritura física en disco del receptor al emisor
                            byte[] finalAckFromReceptor = ProtocolService.readHandshakePacket(dataReceiver);
                            sender.getWritableChannel().write(ByteBuffer.wrap(finalAckFromReceptor));
                        }
                    }
                }

                // ====================================================================
                // 🚨 CIERRE ESTRUCTURAL DE LA SESIÓN INDIVIDUAL
                // ====================================================================
                // El emisor enviará un TRANSFER_DONE para cerrar formalmente el pipeline
                byte[] finalPacket = ProtocolService.readHandshakePacket(sender);
                Communication finalComm = ProtocolService.fromBytes(finalPacket);

                if (finalComm instanceof FileHandshakeCommunication handshake && handshake.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                    dataReceiver.sendComunicacion(handshake);
                }

                // Desconexión física e higiénica de los sockets temporales de datos
                //dataReceiver.shutDown();
                //sender.shutDown();
                Logger.logInfo(logId + " Sesión de relay para archivo individual finalizada correctamente.");

            } else {
                Logger.logWarn(logId + " Solicitud individual rechazada por el receptor o expirada.");
            }
        } catch (Exception e) {
            Logger.logError(logId + " FATAL: Error en relay de archivo individual: " + e.getMessage());
        }
    }

    private void bridgeSocketChannels(BitBridgeClient source, BitBridgeClient dest, long totalSize) throws IOException, InterruptedException {
        ByteBuffer buffer = null;
        ReadableByteChannel sChannel = source.getReadableChannel();
        WritableByteChannel dChannel = dest.getWritableChannel();

        try {
            buffer = BufferPool.borrow();
            long totalTransferred = 0;

            while (totalTransferred < totalSize) {
                buffer.clear();
                int read = sChannel.read(buffer);
                if (read == -1) break;

                buffer.flip();
                while (buffer.hasRemaining()) {
                    dChannel.write(buffer);
                }
                totalTransferred += read;
            }
            Logger.logInfo("Bytes movidos. Manteniendo canales abiertos para confirmación final...");

        } catch (Exception e) {
            Logger.logError("Error en bridge: " + e.getMessage());
            throw e;
        } finally {
            if (buffer != null) BufferPool.giveBack(buffer);
        }
    }

    private void bridgeSocketChannelsNoShutdown(BitBridgeClient source, BitBridgeClient dest, long totalSize) throws IOException, InterruptedException {
        ByteBuffer buffer = null;
        try {
            ReadableByteChannel sChannel = source.getReadableChannel();
            WritableByteChannel dChannel = dest.getWritableChannel();
            buffer = BufferPool.borrow();
            long totalTransferred = 0;

            while (totalTransferred < totalSize) {
                buffer.clear();
                long remainingInFile = totalSize - totalTransferred;
                if (remainingInFile < buffer.capacity()) buffer.limit((int) remainingInFile);

                int read = sChannel.read(buffer);
                if (read == -1) throw new IOException("Desconexión prematura emisor");
                if (read == 0) { Thread.sleep(1); continue; }

                buffer.flip();
                while (buffer.hasRemaining()) {
                    if (dChannel.write(buffer) == 0) Thread.sleep(1);
                }
                totalTransferred += read;
            }
        } finally {
            if (buffer != null) BufferPool.giveBack(buffer);
        }
    }

    public void relayDirectory(FileDirectoryCommunication com, BitBridgeClient sender, String sessionId) {
        String logId = "[DIR-RELAY-" + sessionId + "]";
        BitBridgeClient recipient = context.registry().findByNick(com.getRecipient());
        if (recipient == null) return;

        try {
            recipient.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SEND_REQUEST, sessionId, com));
            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 30);
            if (dataReceiver == null) return;

            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 7);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {
                FileHandshakeCommunication start = new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId, com);
                dataReceiver.sendComunicacion(start);
                sender.sendComunicacion(start);

                if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                    engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                    engine.unregisterChannel((SocketChannel) dataReceiver.getReadableChannel());
                    ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                    ((SocketChannel) dataReceiver.getReadableChannel()).configureBlocking(true);
                }

                boolean transferenciaActiva = true;

                while (transferenciaActiva) {
                    byte[] packetData = ProtocolService.readHandshakePacket(sender);
                    Communication object = ProtocolService.fromBytes(packetData);

                    if (object instanceof FileDirectoryCommunication meta) {
                        dataReceiver.sendComunicacion(meta);

                        byte[] receptorAckRaw = ProtocolService.readHandshakePacket(dataReceiver);
                        Communication receptorResponse = ProtocolService.fromBytes(receptorAckRaw);

                        if (receptorResponse instanceof FileHandshakeCommunication resp) {
                            FileHandshakeAction accionReceptor = resp.getAction();

                            if (accionReceptor == FileHandshakeAction.SKIP_FILE) {
                                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                continue;
                            }

                            // --- PUENTE DE CONTROL EXCLUSIVO RSYNC ---
                            if (accionReceptor == FileHandshakeAction.PROCESS_DELTAS) {
                                Logger.logInfo(logId + " [RSYNC-RELAY] -> Entrando en modo diferencial para: " + meta.getRelativePath());
                                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                                // 1. Mover Firmas: Receptor -> Servidor -> Emisor
                                byte[] signaturesRaw = ProtocolService.readHandshakePacket(dataReceiver);
                                sender.getWritableChannel().write(ByteBuffer.wrap(signaturesRaw));

                                // 2. Mover Paquete de Deltas: Emisor -> Servidor -> Receptor
                                byte[] deltasRaw = ProtocolService.readHandshakePacket(sender);
                                dataReceiver.getWritableChannel().write(ByteBuffer.wrap(deltasRaw));

                                // 3. Esperar confirmación del receptor sobre el ensamble final
                                byte[] finalAck = ProtocolService.readHandshakePacket(dataReceiver);
                                sender.getWritableChannel().write(ByteBuffer.wrap(finalAck));
                                continue;
                            }

                            if (accionReceptor == FileHandshakeAction.START_TRANSFER) {
                                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                if (!meta.isDirectory()) {
                                    bridgeSocketChannelsNoShutdown(sender, dataReceiver, meta.getSize());
                                    byte[] finalAckFromReceptor = ProtocolService.readHandshakePacket(dataReceiver);
                                    sender.getWritableChannel().write(ByteBuffer.wrap(finalAckFromReceptor));
                                }
                                continue;
                            }
                        }
                    } else if (object instanceof FileHandshakeCommunication handshake && handshake.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                        transferenciaActiva = false;
                        dataReceiver.sendComunicacion(handshake);
                    }
                }
                dataReceiver.shutDown();
                sender.shutDown();
            }
        } catch (Exception e) {
            Logger.logError(logId + " FATAL: Error en relay Rsync: " + e.getMessage());
        }
    }
}
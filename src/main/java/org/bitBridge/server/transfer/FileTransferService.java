package org.bitBridge.server.transfer;

import org.bitBridge.server.core.NioServerEngine;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.*;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.FileDirectoryCommunication;
import org.bitBridge.shared.core.comunication.FileHandshakeAction;
import org.bitBridge.shared.core.comunication.FileHandshakeCommunication;
import org.bitBridge.shared.memory.BufferPool;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

public class FileTransferService {

    private final ServerContext context;
    private static final int BRIDGE_BUFFER_SIZE = 128 * 1024;

    public FileTransferService(ServerContext context) {
        this.context = context;
    }

    public void handleForwardFile(BitBridgeClient sender, FileDirectoryCommunication communication) {
        String logId = "[SERV-FILE-RSYNC-" + new Random().nextInt(100) + "]";
        try {
            String recipientNick = communication.getRecipient();
            BitBridgeClient recipient = context.registry().findByNick(recipientNick);

            if (recipient == null) {
                Logger.logError(logId + " Destinatario offline: " + recipientNick);
                return;
            }

            String sessionId = "FILE_" + new Random().nextInt(1000, 9999);

            // 1. Enviar solicitud de handshake al receptor con la metadata del archivo
            FileHandshakeCommunication request = new FileHandshakeCommunication(
                    FileHandshakeAction.SEND_REQUEST, sessionId, communication);
            recipient.sendComunicacion(request);

            // 2. Esperar a que el receptor asocie su socket de datos dedicado
            BitBridgeClient receptorData = context.transferManager().waitForReceptor(sessionId, 30);
            if (receptorData == null) {
                Logger.logError(logId + " TIMEOUT: El receptor no conectó su socket.");
                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.ERROR_TIMEOUT, sessionId));
                return;
            }

            // 3. Esperar la acción de respuesta del receptor (Accept / Decline)
            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 30);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {
                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.START_TRANSFER, sessionId, communication);

                // Despachar señal de tubería lista a ambos clientes
                receptorData.sendComunicacion(start);
                sender.sendComunicacion(start);

                // Cambiar canales a modo bloqueante para maximizar la estabilidad en transferencias masivas
                SocketChannel senderChannel = (SocketChannel) sender.getReadableChannel();
                SocketChannel receptorChannel = (SocketChannel) receptorData.getReadableChannel();

                if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                    engine.unregisterChannel(senderChannel);
                    engine.unregisterChannel(receptorChannel);
                    senderChannel.configureBlocking(true);
                    receptorChannel.configureBlocking(true);
                }

                // --- ORQUESTACIÓN DEL PROTOCOLO DE NEGOCIACIÓN RSYNC (Modo Estructurado) ---
                byte[] receptorActionRaw = ProtocolService.readHandshakePacket(receptorData);
                Communication receptorResponse = ProtocolService.fromBytes(receptorActionRaw);

                if (receptorResponse instanceof FileHandshakeCommunication resp) {
                    FileHandshakeAction accionReceptor = resp.getAction();

                    // CASO A: Archivo idéntico (Quick Check existoso) -> Abortar y omitir de inmediato
                    if (accionReceptor == FileHandshakeAction.SKIP_FILE) {
                        Logger.logInfo(logId + " -> Omitiendo archivo individual (Idéntico en destino).");
                        sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                        return;
                    }

                    // CASO B: Archivo modificado/diferente -> Flujo delta por bloques rolling-hash
                    if (accionReceptor == FileHandshakeAction.PROCESS_DELTAS) {
                        Logger.logInfo(logId + " -> Entrando en modo diferencial (Rsync Delta).");

                        // Notificar al emisor que configure su ventana móvil para deltas
                        sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                        // Fase 1: Mover Firmas (Receptor -> Servidor -> Emisor)
                        byte[] signaturesRaw = ProtocolService.readHandshakePacket(receptorData);
                        Communication signaturesObj = ProtocolService.fromBytes(signaturesRaw);
                        ProtocolService.writeNIO(senderChannel, signaturesObj); // Reinyecta los 4 bytes de tamaño al inicio

                        // Fase 2: Mover Paquete de Deltas (Emisor -> Servidor -> Receptor)
                        byte[] deltasRaw = ProtocolService.readHandshakePacket(sender);
                        Communication deltasObj = ProtocolService.fromBytes(deltasRaw);
                        ProtocolService.writeNIO(receptorChannel, deltasObj);

                        // Fase 3: Confirmación Final de Ensamblado (Receptor -> Servidor -> Emisor)
                        byte[] finalAckRaw = ProtocolService.readHandshakePacket(receptorData);
                        Communication finalAckObj = ProtocolService.fromBytes(finalAckRaw);
                        ProtocolService.writeNIO(senderChannel, finalAckObj);
                        return;
                    }

                    // CASO C: Archivo nuevo -> Transferencia tradicional Zero-Copy de alta velocidad
                    if (accionReceptor == FileHandshakeAction.START_TRANSFER) {
                        sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                        // Tubería directa controlada por el Pool de memoria Off-Heap
                        bridgeSocketChannelsNoShutdown(sender, receptorData, communication.getSize());

                        // Reenviar ACK final del receptor hacia el emisor para cerrar de forma limpia
                        byte[] finalAckFromReceptorRaw = ProtocolService.readHandshakePacket(receptorData);
                        Communication finalAckObj = ProtocolService.fromBytes(finalAckFromReceptorRaw);
                        ProtocolService.writeNIO(senderChannel, finalAckObj);
                    }
                }
            } else {
                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
            }
        } catch (Exception e) {
            Logger.logError(logId + " Error interno en rsync individual: " + e.getMessage());
            e.printStackTrace();
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
            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 20);
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
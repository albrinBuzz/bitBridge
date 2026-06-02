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
        String logId = "[SERV-TRANSFER-" + new Random().nextInt(100) + "]";
        try {
            String recipientNick = communication.getRecipient();
            long fileSize = communication.getSize();
            BitBridgeClient recipient = context.registry().findByNick(recipientNick);

            if (recipient == null) {
                Logger.logError(logId + " Destinatario offline: " + recipientNick);
                return;
            }

            String sessionId = "FILE_" + new Random().nextInt(1000, 9999);

            FileHandshakeCommunication request = new FileHandshakeCommunication(
                    FileHandshakeAction.SEND_REQUEST, sessionId, communication);

            recipient.sendComunicacion(request);

            BitBridgeClient receptorData = context.transferManager().waitForReceptor(sessionId, 30);

            if (receptorData == null) {
                Logger.logError(logId + " TIMEOUT (Fase 1): El receptor no conectó su socket para la sesión " + sessionId);
                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.ERROR_TIMEOUT, sessionId));
                return;
            }

            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 30);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {
                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.START_TRANSFER, sessionId, communication);

                receptorData.sendComunicacion(start);
                sender.sendComunicacion(start);

                if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                    engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                    engine.unregisterChannel((SocketChannel) receptorData.getReadableChannel());

                    try {
                        ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                        ((SocketChannel) receptorData.getReadableChannel()).configureBlocking(true);
                    } catch (IOException e) {
                        Logger.logError("Error configurando canales en modo bloqueante");
                    }
                    if (sender.getReadableChannel() instanceof SocketChannel sc) {
                        sc.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        sc.setOption(java.net.StandardSocketOptions.SO_RCVBUF, BRIDGE_BUFFER_SIZE);
                    }
                    if (receptorData.getWritableChannel() instanceof SocketChannel sc) {
                        sc.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        sc.setOption(java.net.StandardSocketOptions.SO_SNDBUF, BRIDGE_BUFFER_SIZE);
                    }
                }

                bridgeSocketChannels(sender, receptorData, fileSize);
            } else {
                Logger.logWarn(logId + " Transferencia cancelada: El receptor respondió: " + action);
                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
            }
        } catch (Exception e) {
            Logger.logError(logId + " Error interno: " + e.getMessage());
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
                if (remainingInFile < buffer.capacity()) {
                    buffer.limit((int) remainingInFile);
                }

                int read = sChannel.read(buffer);
                if (read == -1) throw new IOException("Emisor desconectó prematuramente");
                if (read == 0) {
                    Thread.sleep(1);
                    continue;
                }

                buffer.flip();
                while (buffer.hasRemaining()) {
                    int written = dChannel.write(buffer);
                    if (written == 0) {
                        Thread.sleep(1);
                    }
                }
                totalTransferred += read;
            }
        } catch (Exception e) {
            Logger.logError("Error en bridge: " + e.getMessage());
            throw e;
        } finally {
            if (buffer != null) BufferPool.giveBack(buffer);
        }
    }

    public void relayDirectory(FileDirectoryCommunication com, BitBridgeClient sender, String sessionId) {
        String logId = "[DIR-RELAY-" + sessionId + "]";
        BitBridgeClient recipient = context.registry().findByNick(com.getRecipient());
        if (recipient == null) {
            Logger.logError(logId + " Receptor no encontrado.");
            return;
        }

        try {
            recipient.sendComunicacion(new FileHandshakeCommunication(
                    FileHandshakeAction.SEND_REQUEST, sessionId, com));

            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 20);
            if (dataReceiver == null) {
                Logger.logError(logId + " Timeout esperando al receptor.");
                return;
            }

            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 7);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {
                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.START_TRANSFER, sessionId, com);

                dataReceiver.sendComunicacion(start);
                sender.sendComunicacion(start);

                boolean transferenciaActiva = true;
                int archivosProcesados = 0;

                if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                    engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                    engine.unregisterChannel((SocketChannel) dataReceiver.getReadableChannel());

                    try {
                        ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                        ((SocketChannel) dataReceiver.getReadableChannel()).configureBlocking(true);
                    } catch (IOException e) {
                        Logger.logError("Error configurando canales bloqueantes");
                    }
                    if (sender.getReadableChannel() instanceof SocketChannel sc) {
                        sc.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        sc.setOption(java.net.StandardSocketOptions.SO_RCVBUF, BRIDGE_BUFFER_SIZE);
                    }
                    if (dataReceiver.getWritableChannel() instanceof SocketChannel sc) {
                        sc.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        sc.setOption(java.net.StandardSocketOptions.SO_SNDBUF, BRIDGE_BUFFER_SIZE);
                    }
                }

                while (transferenciaActiva) {
                    byte[] packetData = ProtocolService.readHandshakePacket(sender);
                    Communication object = ProtocolService.fromBytes(packetData);

                    if (object instanceof FileDirectoryCommunication meta) {
                        archivosProcesados++;

                        dataReceiver.sendComunicacion(meta);

                        byte[] receptorAckRaw = ProtocolService.readHandshakePacket(dataReceiver);
                        Communication receptorResponse = ProtocolService.fromBytes(receptorAckRaw);

                        if (receptorResponse instanceof FileHandshakeCommunication resp) {
                            FileHandshakeAction accionReceptor = resp.getAction();

                            // --- LOG SERVER: ORDEN DE OMISIÓN RELAYED ---
                            if (accionReceptor == FileHandshakeAction.SKIP_FILE) {
                                Logger.logInfo(logId + " Relay decision -> SKIP para archivo: " + meta.getRelativePath());
                                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                continue;
                            }

                            // --- LOG SERVER: ORDEN DE TRANSFERENCIA RELAYED ---
                            if (accionReceptor == FileHandshakeAction.START_TRANSFER) {
                                if (!meta.isDirectory()) {
                                    Logger.logInfo(logId + " Relay decision -> TRANSFER para archivo: " + meta.getRelativePath());
                                }
                                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                                if (!meta.isDirectory()) {
                                    bridgeSocketChannelsNoShutdown(sender, dataReceiver, meta.getSize());
                                    byte[] finalAckFromReceptor = ProtocolService.readHandshakePacket(dataReceiver);
                                    sender.getWritableChannel().write(java.nio.ByteBuffer.wrap(finalAckFromReceptor));
                                }
                                continue;
                            }
                        } else {
                            throw new IOException("Control packet inválido devuelto por el receptor.");
                        }
                    } else if (object instanceof FileHandshakeCommunication handshake) {
                        if (handshake.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                            transferenciaActiva = false;
                            Logger.logInfo(logId + " Recibido TRANSFER_DONE. Finalizando relay.");
                            dataReceiver.sendComunicacion(handshake);
                        }
                    }
                }

                dataReceiver.shutDown();
                sender.shutDown();

            } else {
                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
            }

        } catch (Exception e) {
            Logger.logError(logId + " FATAL: Error en el flujo de relay: " + e.getMessage());
        }
    }
}
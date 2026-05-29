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
    // Buffer directo (fuera del Heap) para máxima velocidad de puente entre sockets
    private static final int BRIDGE_BUFFER_SIZE = 128 * 1024;

    public FileTransferService(ServerContext context) {
        this.context = context;
    }

    public void handleForwardFile(BitBridgeClient sender, FileDirectoryCommunication communication) {
        String logId = "[SERV-TRANSFER-" + new Random().nextInt(100) + "]";
        try {
            String recipientNick = communication.getRecipient();
            long fileSize=communication.getSize();
            BitBridgeClient recipient = context.registry().findByNick(recipientNick);

            if (recipient == null) {
                Logger.logError(logId + " Destinatario offline: " + recipientNick);
                return;
            }

            String sessionId = "FILE_" + new Random().nextInt(1000, 9999);

            FileHandshakeCommunication request = new FileHandshakeCommunication(
                    FileHandshakeAction.SEND_REQUEST, sessionId, communication);

            recipient.sendComunicacion(request);

            // --- PUNTO CRÍTICO 1: Espera del Socket ---
            BitBridgeClient receptorData = context.transferManager().waitForReceptor(sessionId, 30); // Subido a 10s

            if (receptorData == null) {
                Logger.logError(logId + " TIMEOUT (Fase 1): El receptor no conectó su socket para la sesión " + sessionId);
                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.ERROR_TIMEOUT, sessionId));
                return;
            }


            // --- PUNTO CRÍTICO 2: Espera de la Acción (ACCEPT) ---
            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 30);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {

                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.START_TRANSFER, sessionId, communication);

                receptorData.sendComunicacion(start);
                sender.sendComunicacion(start);

                if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                    // 1. Desregistrar del Selector para que NioClientHandler no intente leer más
                    engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                    engine.unregisterChannel((SocketChannel) receptorData.getReadableChannel());

                    // 2. Importante: Forzar modo bloqueante para que el hilo de relay tenga el control total
                    try {
                        ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                        ((SocketChannel) receptorData.getReadableChannel()).configureBlocking(true);
                    } catch (IOException e) {
                        Logger.logError("Error configurando canales en modo bloqueante");
                    }
                    if (sender.getReadableChannel() instanceof SocketChannel sc) {
                        sc.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        sc.setOption(java.net.StandardSocketOptions.SO_RCVBUF, BRIDGE_BUFFER_SIZE); // 1MB
                    }
                    if (receptorData.getWritableChannel() instanceof SocketChannel sc) {
                        sc.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        sc.setOption(java.net.StandardSocketOptions.SO_SNDBUF, BRIDGE_BUFFER_SIZE); // 1MB
                    }
                }


                bridgeSocketChannels(sender,receptorData,fileSize);
                //sender.shutDown();
                //receptorData.shutDown();
            } else {
                Logger.logWarn(logId + " Transferencia cancelada: El receptor respondió: " + action);
                sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
            }
        } catch (Exception e) {
            Logger.logError(logId + " Error interno: " + e.getMessage());
        }
        finally {

        }
    }

    /**
     * Mueve bytes directamente de un SocketChannel a otro sin usar el Heap.
     */
    private void bridgeSocketChannels(BitBridgeClient source, BitBridgeClient dest, long totalSize) throws IOException, InterruptedException {
        ByteBuffer buffer = null;
        // QUITAMOS el try-with-resources de los canales para que NO se cierren al terminar
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

            // --- ADICIÓN CRÍTICA ---
            // No salgas de aquí hasta que el receptor envíe el OK final o el emisor confirme
            Logger.logInfo("Bytes movidos. Manteniendo canales abiertos para confirmación final...");

        } catch (Exception e) {
            Logger.logError("Error en bridge: " + e.getMessage());
            throw e;
        } finally {
            if (buffer != null) BufferPool.giveBack(buffer);
            // NO cerramos sChannel ni dChannel aquí.
            // El cierre debe ser gestionado por el ciclo de vida del BitBridgeClient
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
                // Limitamos la lectura al tamaño restante para no pasarnos del archivo actual
                long remainingInFile = totalSize - totalTransferred;
                if (remainingInFile < buffer.capacity()) {
                    buffer.limit((int) remainingInFile);
                }

                int read = sChannel.read(buffer);
                if (read == -1) throw new IOException("Emisor desconectó prematuramente");
                if (read == 0) {
                    Thread.sleep(1); // Esperar a que lleguen datos por red física
                    continue;
                }

                buffer.flip();
                while (buffer.hasRemaining()) {
                    int written = dChannel.write(buffer);
                    if (written == 0) {
                        // El buffer del RECEPTOR está lleno.
                        // Obligatorio ceder tiempo para que la red física procese.
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
    /**
     * Auxiliar para leer paquetes de control (Handshake) durante la transferencia de archivos.
     */
    private byte[] readHandshakePacket(BitBridgeClient client) throws IOException {
        ReadableByteChannel channel = client.getReadableChannel();
        ByteBuffer header = ByteBuffer.allocate(6);
        while(header.hasRemaining()) channel.read(header);
        header.flip();

        int jsonSize = header.getInt();
        short typeSize = header.getShort();

        ByteBuffer payload = ByteBuffer.allocate(6 + typeSize + jsonSize);
        header.rewind();
        payload.put(header);

        while(payload.hasRemaining()) channel.read(payload);
        return payload.array();
    }



    public void relayDirectory(FileDirectoryCommunication com, BitBridgeClient sender, String sessionId) {
        String logId = "[DIR-RELAY-" + sessionId + "]";

        //Logger.logInfo(logId + " Iniciando relay para: " + com.getName() + " -> Receptor: " + com.getRecipient());
        BitBridgeClient recipient = context.registry().findByNick(com.getRecipient());
        if (recipient == null) {
            Logger.logError(logId + " Receptor no encontrado.");
            return;
        }

        try {
            // 1. Notificar al receptor sobre la solicitud de carpeta
            recipient.sendComunicacion(new FileHandshakeCommunication(
                    FileHandshakeAction.SEND_REQUEST, sessionId, com));

            // 2. Esperar al canal de datos del receptor
            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 20);
            if (dataReceiver == null) {
                Logger.logError(logId + " Timeout esperando al receptor.");
                return;
            }

            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 7);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {
                // Confirmación de inicio a ambos
                FileHandshakeCommunication start = new FileHandshakeCommunication(
                        FileHandshakeAction.START_TRANSFER, sessionId, com);

                dataReceiver.sendComunicacion(start);
                sender.sendComunicacion(start);

                boolean transferenciaActiva = true;
                int archivosProcesados = 0;
                //Logger.logInfo(logId + " Iniciando flujo de relay NIO...");
                if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                    // 1. Desregistrar del Selector para que NioClientHandler no intente leer más
                    engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                    engine.unregisterChannel((SocketChannel) dataReceiver.getReadableChannel());

                    // 2. Importante: Forzar modo bloqueante para que el hilo de relay tenga el control total
                    try {
                        ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                        ((SocketChannel) dataReceiver.getReadableChannel()).configureBlocking(true);
                    } catch (IOException e) {
                        Logger.logError("Error configurando canales en modo bloqueante");
                    }
                    if (sender.getReadableChannel() instanceof SocketChannel sc) {
                        sc.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        sc.setOption(java.net.StandardSocketOptions.SO_RCVBUF, BRIDGE_BUFFER_SIZE); // 1MB
                    }
                    if (dataReceiver.getWritableChannel() instanceof SocketChannel sc) {
                        sc.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        sc.setOption(java.net.StandardSocketOptions.SO_SNDBUF, BRIDGE_BUFFER_SIZE); // 1MB
                    }
                }
                // --- BUCLE DE RELAY NIO ---
                while (transferenciaActiva) {
                    // A. Leer el siguiente paquete de control (6 bytes header + JSON)
                    //Logger.logInfo(logId + " Esperando siguiente paquete de control del Emisor...");
                    byte[] packetData = ProtocolService.readHandshakePacket(sender);
                    Communication object = ProtocolService.fromBytes(packetData);

                    if (object instanceof FileDirectoryCommunication meta) {

                        archivosProcesados++;
                        //Logger.logInfo(logId + " Relaying #" + archivosProcesados + ": " + meta.getRelativePath() + " (" + meta.getSize() + " bytes)");

                        dataReceiver.sendComunicacion(meta);

                        byte[] receptorAckRaw = ProtocolService.readHandshakePacket(dataReceiver);
                        Communication receptorResponse = ProtocolService.fromBytes(receptorAckRaw);

                        if (receptorResponse instanceof FileHandshakeCommunication resp && resp.getAction() == FileHandshakeAction.START_TRANSFER) {

                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                        if (!meta.isDirectory() ) {
                            //Logger.logInfo(logId + " Abriendo puente de bytes para: " + meta.getName());
                            // C. Si es un archivo, puenteamos sus bytes de contenido
                            // Usamos el método bridgeSocketChannels que definimos antes
                            bridgeSocketChannelsNoShutdown(sender, dataReceiver, meta.getSize());
                            //Logger.logInfo(logId + " Puente de bytes cerrado exitosamente.");
                            byte[] finalAckFromReceptor = ProtocolService.readHandshakePacket(dataReceiver);

                            // 4. Solo después de que el receptor terminó, le avisamos al emisor
                            sender.getWritableChannel().write(java.nio.ByteBuffer.wrap(finalAckFromReceptor));
                            //Logger.logInfo(logId + " Sincronización final enviada al emisor.");
                        }
                        else {
                            // Caso DIRECTORIO: No hay bytes que puentear
                            //Logger.logInfo(logId + " Procesado directorio: " + meta.getRelativePath());
                            // Opcional: Pequeño yield para dejar que el receptor procese el JSON
                            //Thread.yield();
                            //sender.sendComunicacion(receptorResponse);
                            continue;
                            //sender.sendComunicacion(receptorResponse);
                            //sender.sendComunicacion(receptorResponse);
                        }
                        } else {
                            throw new IOException("El receptor rechazó el archivo o envió un paquete inválido");
                        }
                        // Si es directorio, no hay bytes que puentear, volvemos al inicio del bucle
                    }
                    else if (object instanceof FileHandshakeCommunication handshake) {
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
            Logger.logError(logId + " FATAL: Error en el flujo de relay: " + e.getClass().getSimpleName() + " -> " + e.getMessage());
            e.printStackTrace();
        }
    }
}
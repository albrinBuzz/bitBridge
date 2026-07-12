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

/**
 * ============================================================================
 *  OPTIMIZACIONES APLICADAS (ver PLAN_REFACTORIZACION.md para detalle)
 * ----------------------------------------------------------------------------
 *  1. Se eliminó el bloque de logging ASCII decorativo por-archivo dentro del
 *     loop caliente de relayDirectory. Esos String.format() se evaluaban en
 *     cada iteración sin importar el nivel de log configurado; en directorios
 *     con miles de archivos esto era CPU y presión de GC innecesarias. Se
 *     conservan los logs de inicio/fin de sesión y los de eventos poco
 *     frecuentes (rechazos, timeouts, errores).
 *  2. Se eliminó bridgeSocketChannels() (código muerto — solo se usaba la
 *     variante NoShutdown).
 *  3. BRIDGE_BUFFER_SIZE queda documentado y fácil de subir si el enlace de
 *     red lo justifica (con BufferPool ya se evita GC por-transferencia).
 * ============================================================================
 */
public class FileTransferService {

    private final ServerContext context;
    // Tamaño de buffer del puente NIO. Si el ancho de banda disponible es alto
    // y la latencia también, subir este valor (p.ej. 256KB-1MB) puede reducir
    // el número de vueltas de syscalls por archivo grande. BufferPool reutiliza
    // los buffers entre transferencias, así que subir el tamaño no implica GC extra.
    private static final int BRIDGE_BUFFER_SIZE = 128 * 1024;

    public FileTransferService(ServerContext context) {
        this.context = context;
    }

    public void handleForwardFile(FileDirectoryCommunication com, BitBridgeClient sender, String sessionId) {
        String logId = "[FILE-RELAY-" + sessionId + "]";
        BitBridgeClient recipient = context.registry().findByNick(com.getRecipient());

        Logger.logInfo(String.format("%s [RELAY] %s -> %s | archivo: %s", logId, sender.getNick(), com.getRecipient(), com.getName()));

        if (recipient == null) {
            Logger.logError(logId + " ❌ Ruteo fallido: Receptor no registrado en el cluster: " + com.getRecipient());
            return;
        }

        try {
            recipient.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SEND_REQUEST, sessionId, com));

            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 30);
            if (dataReceiver == null) {
                Logger.logError(logId + " ❌ Abortando: El socket secundario de datos del receptor caducó o nunca se enlazó.");
                return;
            }

            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 30);

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

                byte[] packetData = ProtocolService.readHandshakePacket(sender);
                Communication object = ProtocolService.fromBytes(packetData);

                if (object instanceof FileDirectoryCommunication meta) {
                    dataReceiver.sendComunicacion(meta);

                    byte[] receptorAckRaw = ProtocolService.readHandshakePacket(dataReceiver);
                    Communication receptorResponse = ProtocolService.fromBytes(receptorAckRaw);

                    if (receptorResponse instanceof FileHandshakeCommunication resp) {
                        FileHandshakeAction actionReceptor = resp.getAction();

                        if (actionReceptor == FileHandshakeAction.SKIP_FILE) {
                            Logger.logInfo(logId + " ⏩ Archivos idénticos detectados por hash. Notificando SKIP_FILE al emisor.");
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                        } else if (actionReceptor == FileHandshakeAction.PROCESS_DELTAS) {
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                            byte[] signaturesRaw = ProtocolService.readHandshakePacket(dataReceiver);
                            sender.sendComunicacion(ProtocolService.fromBytes(signaturesRaw));

                            byte[] deltasRaw = ProtocolService.readHandshakePacket(sender);
                            ProtocolService.writeNIO(dataReceiver.getSocketChannel(), ProtocolService.fromBytes(deltasRaw));

                            byte[] finalAck = ProtocolService.readHandshakePacket(dataReceiver);
                            sender.sendComunicacion(ProtocolService.fromBytes(finalAck));
                        } else if (actionReceptor == FileHandshakeAction.START_TRANSFER) {
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                            bridgeSocketChannelsNoShutdown(sender, dataReceiver, meta.getSize());

                            byte[] finalAckFromReceptor = ProtocolService.readHandshakePacket(dataReceiver);
                            sender.sendComunicacion(ProtocolService.fromBytes(finalAckFromReceptor));
                        }
                    }
                }

                byte[] finalPacket = ProtocolService.readHandshakePacket(sender);
                Communication finalComm = ProtocolService.fromBytes(finalPacket);

                if (finalComm instanceof FileHandshakeCommunication handshake && handshake.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                    dataReceiver.sendComunicacion(handshake);
                }

                Logger.logInfo(logId + " ✅ Relay individual finalizado con éxito.");
            } else {
                Logger.logWarn(logId + " ⚠️ Solicitud individual rechazada por el receptor o expirada por timeout en canal de control.");
            }
        } catch (Exception e) {
            Logger.logError(logId + " ❌ [FATAL-RELAY] Excepción crítica durante el intercambio de datos: " + e.getMessage());
            e.printStackTrace();
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
            context.getServer().addBytes(totalTransferred);
        } finally {
            if (buffer != null) BufferPool.giveBack(buffer);
        }
    }

    public void relayDirectory(FileDirectoryCommunication com, BitBridgeClient sender, String sessionId) {
        String logId = "[DIR-RELAY-" + sessionId + "]";

        Logger.logInfo(String.format("%s ── INICIO ── %s -> %s | raíz: %s | vol: %s",
                logId, sender.getNick(), com.getRecipient(), com.getName(), formatSize(com.getSize())));

        BitBridgeClient recipient = context.registry().findByNick(com.getRecipient());
        if (recipient == null) {
            Logger.logError(logId + " ❌ [ERROR] El cliente receptor '" + com.getRecipient() + "' no se encuentra registrado en el contexto actual.");
            return;
        }

        try {
            recipient.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SEND_REQUEST, sessionId, com));

            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 30);
            if (dataReceiver == null) {
                Logger.logError(logId + " ❌ [TIMEOUT] Se excedió el tiempo límite de 30 segundos. El receptor no levantó el canal de datos.");
                return;
            }

            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 7);

            if (action != FileHandshakeAction.ACCEPT_REQUEST) {
                Logger.logWarn(logId + " ⚠️ [RECHAZADO] El receptor canceló la transferencia o devolvió una acción no soportada: " + action);
                return;
            }

            FileHandshakeCommunication start = new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId, com);
            dataReceiver.sendComunicacion(start);
            sender.sendComunicacion(start);

            // Desacoplar del Selector NIO para evitar concurrencia de lectura destructiva
            if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                engine.unregisterChannel((SocketChannel) dataReceiver.getReadableChannel());
                ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                ((SocketChannel) dataReceiver.getReadableChannel()).configureBlocking(true);
            }

            boolean transferenciaActiva = true;
            int fileCounter = 0;

            while (transferenciaActiva) {
                byte[] packetData = ProtocolService.readHandshakePacket(sender);
                Communication object = ProtocolService.fromBytes(packetData);

                if (object instanceof FileDirectoryCommunication meta) {
                    fileCounter++;
                    // Nota: se retiró el log por-archivo (era una caja ASCII con String.format
                    // evaluada en cada iteración). Si se necesita trazabilidad detallada para
                    // debug, reactivar detrás de un flag de verbosidad, no incondicionalmente.

                    dataReceiver.sendComunicacion(meta);

                    byte[] receptorAckRaw = ProtocolService.readHandshakePacket(dataReceiver);
                    Communication receptorResponse = ProtocolService.fromBytes(receptorAckRaw);

                    if (receptorResponse instanceof FileHandshakeCommunication resp) {
                        FileHandshakeAction accionReceptor = resp.getAction();

                        if (accionReceptor == FileHandshakeAction.SKIP_FILE) {
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                            continue;
                        }

                        if (accionReceptor == FileHandshakeAction.PROCESS_DELTAS) {
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                            byte[] signaturesRaw = ProtocolService.readHandshakePacket(dataReceiver);
                            sender.sendComunicacion(ProtocolService.fromBytes(signaturesRaw));

                            byte[] deltasRaw = ProtocolService.readHandshakePacket(sender);
                            dataReceiver.sendComunicacion(ProtocolService.fromBytes(deltasRaw));

                            byte[] finalAck = ProtocolService.readHandshakePacket(dataReceiver);
                            sender.sendComunicacion(ProtocolService.fromBytes(finalAck));
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
                    Logger.logInfo(logId + " 🏁 [FINISH] Directiva global TRANSFER_DONE recibida del emisor. Archivos procesados: " + fileCounter);
                    transferenciaActiva = false;
                    dataReceiver.sendComunicacion(handshake);
                }
            }

            dataReceiver.shutDown();
            sender.shutDown();
            Logger.logInfo(logId + " ── RELAY DE DIRECTORIO FINALIZADO SIN ERRORES ──");

        } catch (Exception e) {
            Logger.logError(logId + " ❌ [FATAL-ERROR] Excepción crítica en el hilo del relay Rsync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.2f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
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

        Logger.logInfo(String.format("%s ┌──────────────────────────────────────────────────────────────────┐", logId));
        Logger.logInfo(String.format("%s │  [SERVER RELAY] Evaluando Orquestación de Sincronización         │", logId));
        Logger.logInfo(String.format("%s ├──────────────────────────────────────────────────────────────────┤", logId));
        Logger.logInfo(String.format("%s │  ├── Origen (Sender):    %-38s │", logId, sender.getNick()));
        Logger.logInfo(String.format("%s │  ├── Destino (Target):   %-38s │", logId, com.getRecipient()));
        Logger.logInfo(String.format("%s │  └── Archivo Solicitado: %-38s │", logId, com.getName()));
        Logger.logInfo(String.format("%s └──────────────────────────────────────────────────────────────────┘", logId));

        if (recipient == null) {
            Logger.logError(logId + " ❌ Ruteo fallido: Receptor no registrado en el cluster: " + com.getRecipient());
            return;
        }

        try {
            // 1. Notificar al hilo de control del receptor que hay un archivo entrante
            Logger.logInfo(logId + " [CONTROL] Notificando SEND_REQUEST al hilo de eventos del receptor...");
            recipient.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SEND_REQUEST, sessionId, com));

            // 2. Esperar que el socket secundario de datos del RECEPTOR se conecte al pool
            Logger.logInfo(logId + " [POOL-NIO] Esperando la inicialización del SocketChannel de datos del Receptor (Timeout: 30s)...");
            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 30);
            if (dataReceiver == null) {
                Logger.logError(logId + " ❌ Abortando: El socket secundario de datos del receptor caducó o nunca se enlazó.");
                return;
            }
            Logger.logInfo(logId + " [POOL-NIO] Socket de datos del Receptor enlazado con éxito al pool asíncrono.");

            // 3. Esperar la confirmación de aceptación del hilo de control del receptor
            Logger.logInfo(logId + " [CONTROL] Esperando ACCEPT_REQUEST lúdico del receptor (Timeout: 7s)...");
            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 30);

            if (action == FileHandshakeAction.ACCEPT_REQUEST) {
                Logger.logInfo(logId + " [HANDSHAKE] Solicitud ACEPTADA por el receptor. Coordinando tokens unificados...");

                // Construir el handshake de inicio oficial con el token unificado
                FileHandshakeCommunication start = new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId, com);

                // Desbloquear los canales de datos de ambos extremos
                Logger.logInfo(logId + " [NIO-WRITE] Despachando START_TRANSFER simétrico a Emisor y Receptor...");
                dataReceiver.sendComunicacion(start);
                sender.sendComunicacion(start);

                // Pasar canales NIO a modo Bloqueante para la transferencia lineal segura en el Relay
                if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                    Logger.logInfo(logId + " [KERNEL-NIO] Removiendo canales del Selector asíncrono. Pasando a modo bloqueante dedicado.");
                    engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                    engine.unregisterChannel((SocketChannel) dataReceiver.getReadableChannel());
                    ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                    ((SocketChannel) dataReceiver.getReadableChannel()).configureBlocking(true);
                }

                // ====================================================================
                // 🚨 LOGICA EXTRÁIDA DIRECTAMENTE DE RELAY_DIRECTORY
                // ====================================================================
                Logger.logInfo(logId + " [NIO-READ] Esperando paquete de metadatos del emisor...");
                byte[] packetData = ProtocolService.readHandshakePacket(sender);
                Communication object = ProtocolService.fromBytes(packetData);

                if (object instanceof FileDirectoryCommunication meta) {
                    Logger.logInfo(String.format("%s [FORWARD] Reenviando metadatos individuales al receptor (%d bytes)...", logId, packetData.length));
                    dataReceiver.sendComunicacion(meta);

                    Logger.logInfo(logId + " [NIO-READ] Esperando resolución de redundancia del receptor...");
                    byte[] receptorAckRaw = ProtocolService.readHandshakePacket(dataReceiver);
                    Communication receptorResponse = ProtocolService.fromBytes(receptorAckRaw);

                    if (receptorResponse instanceof FileHandshakeCommunication resp) {
                        FileHandshakeAction actionReceptor = resp.getAction();

                        // --- CASO 1: OMITIR ARCHIVO (SKIP) ---
                        if (actionReceptor == FileHandshakeAction.SKIP_FILE) {
                            Logger.logInfo(String.format("%s ┌──────────────────────────────────────────────────────────────────┐", logId));
                            Logger.logInfo(String.format("%s │  ⏩ [OMISIÓN AUTOMÁTICA] Archivos idénticos detectados por hash   │", logId));
                            Logger.logInfo(String.format("%s └──────────────────────────────────────────────────────────────────┘", logId));

                            Logger.logInfo(logId + " [FORWARD] Notificando SKIP_FILE de vuelta al emisor.");
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                        }

                        // --- CASO 2: MODO DIFERENCIAL RSYNC ---
                        else if (actionReceptor == FileHandshakeAction.PROCESS_DELTAS) {
                            Logger.logWarn(String.format("%s ┌──────────────────────────────────────────────────────────────────┐", logId));
                            Logger.logWarn(String.format("%s │  ⚡ [RSYNC-TUNNEL] Iniciando canalización de deltas lógicos      │", logId));
                            Logger.logWarn(String.format("%s ├──────────────────────────────────────────────────────────────────┤", logId));

                            Logger.logInfo(logId + " [FORWARD] Notificando PROCESS_DELTAS de vuelta al emisor.");
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                            // 1. Firmas: Receptor -> Servidor -> Emisor
                            Logger.logInfo(logId + " │  ├── [FASE 1] Leyendo firmas Adler32/MD5 del receptor...");
                            byte[] signaturesRaw = ProtocolService.readHandshakePacket(dataReceiver);
                            Logger.logInfo(String.format("%s │  │   ↳ Descargadas: %s bytes. Reenviando al emisor...", logId,formatSize( signaturesRaw.length)));
                            //sender.getWritableChannel().write(ByteBuffer.wrap(signaturesRaw));

                            sender.sendComunicacion(ProtocolService.fromBytes(signaturesRaw));
                            //ProtocolService.writeNIO(sender.getSocketChannel(),ProtocolService.fromBytes(signaturesRaw));
                            // 2. Deltas: Emisor -> Servidor -> Receptor
                            Logger.logInfo(logId + " │  ├── [FASE 2] Esperando paquete de deltas calculado por el emisor...");
                            byte[] deltasRaw = ProtocolService.readHandshakePacket(sender);
                            Logger.logInfo(String.format("%s │  │   ↳ Recibidos: %s bytes. Reenviando al receptor...", logId, formatSize( deltasRaw.length)));
                            //dataReceiver.getWritableChannel().write(ByteBuffer.wrap(deltasRaw));

                            //dataReceiver.sendComunicacion(ProtocolService.fromBytes(deltasRaw));

                            ProtocolService.writeNIO(dataReceiver.getSocketChannel(),ProtocolService.fromBytes(deltasRaw));

                            // 3. Confirmación de reconstrucción exitosa: Receptor -> Servidor -> Emisor
                            Logger.logInfo(logId + " │  └── [FASE 3] Esperando confirmación de escritura (Disk Flush) del receptor...");
                            byte[] finalAck = ProtocolService.readHandshakePacket(dataReceiver);
                            Logger.logInfo(logId + " [FORWARD] Reenviando confirmación estructural al emisor. Sincronización Delta OK.");
                            //sender.getWritableChannel().write(ByteBuffer.wrap(finalAck));

                            sender.sendComunicacion(ProtocolService.fromBytes(finalAck));

                            Logger.logWarn(String.format("%s └──────────────────────────────────────────────────────────────────┘", logId));
                        }

                        // --- CASO 3: TRASPASO COMPLETO (ZERO-COPY) ---
                        else if (actionReceptor == FileHandshakeAction.START_TRANSFER) {
                            Logger.logInfo(String.format("%s ┌──────────────────────────────────────────────────────────────────┐", logId));
                            Logger.logInfo(String.format("%s │  📥 [PIPE COMPLETO] Activando tunelización masiva de bytes       │", logId));
                            Logger.logInfo(String.format("%s └──────────────────────────────────────────────────────────────────┘", logId));

                            Logger.logInfo(logId + " [FORWARD] Notificando START_TRANSFER de vuelta al emisor.");
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                            Logger.logInfo(String.format("%s [KERNEL-BRIDGE] Encauzando streams NIO crudos (Tamaño payload: %s bytes)...", logId, formatSize( meta.getSize())));
                            bridgeSocketChannelsNoShutdown(sender, dataReceiver, meta.getSize());

                            Logger.logInfo(logId + " [NIO-READ] Esperando ACK físico final del receptor en disco...");
                            byte[] finalAckFromReceptor = ProtocolService.readHandshakePacket(dataReceiver);

                            Logger.logInfo(logId + " [FORWARD] Reenviando ACK final de disco al emisor.");
                            //sender.getWritableChannel().write(ByteBuffer.wrap(finalAckFromReceptor));
                            sender.sendComunicacion(ProtocolService.fromBytes(finalAckFromReceptor));

                        }
                    }
                }

                // ====================================================================
                // 🚨 CIERRE ESTRUCTURAL DE LA SESIÓN INDIVIDUAL
                // ====================================================================
                Logger.logInfo(logId + " [NIO-READ] Esperando instrucción de cierre de sesión (TRANSFER_DONE) del emisor...");
                byte[] finalPacket = ProtocolService.readHandshakePacket(sender);
                Communication finalComm = ProtocolService.fromBytes(finalPacket);

                if (finalComm instanceof FileHandshakeCommunication handshake && handshake.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                    Logger.logInfo(logId + " [FORWARD] Transmitiendo orden TRANSFER_DONE hacia el receptor...");
                    dataReceiver.sendComunicacion(handshake);
                }

                Logger.logInfo(String.format("%s ┌──────────────────────────────────────────────────────────────────┐", logId));
                Logger.logInfo(String.format("%s │  ✅ [RELAY COMPLETO] Sesión de tunelización finalizada con éxito │", logId));
                Logger.logInfo(String.format("%s └──────────────────────────────────────────────────────────────────┘", logId));

            } else {
                Logger.logWarn(logId + " ⚠️ Solicitud individual rechazada por el receptor o expirada por timeout en canal de control.");
            }
        } catch (Exception e) {
            Logger.logError(logId + " ❌ [FATAL-RELAY] Excepción crítica durante el intercambio de datos: " + e.getMessage());
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
            context.getServer().addBytes(totalTransferred);
        } finally {
            if (buffer != null) BufferPool.giveBack(buffer);
        }

    }

    public void relayDirectory(FileDirectoryCommunication com, BitBridgeClient sender, String sessionId) {
        String logId = "[DIR-RELAY-" + sessionId + "]";

        Logger.logInfo(logId + " ── INICIANDO RELAY DE DIRECTORIO ──");
        Logger.logInfo(logId + String.format(" ├── Origen (Sender): %s", sender.getNick()));
        Logger.logInfo(logId + String.format(" ├── Destino (Target): %s", com.getRecipient()));
        Logger.logInfo(logId + String.format(" └── Nodo Raíz: %s | Volumen Total: %s", com.getName(), formatSize(com.getSize())));

        BitBridgeClient recipient = context.registry().findByNick(com.getRecipient());
        if (recipient == null) {
            Logger.logError(logId + " ❌ [ERROR] El cliente receptor '" + com.getRecipient() + "' no se encuentra registrado en el contexto actual.");
            return;
        }

        try {
            Logger.logInfo(logId + " [HANDSHAKE] Despachando SEND_REQUEST hacia el nodo receptor...");
            recipient.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SEND_REQUEST, sessionId, com));

            Logger.logInfo(logId + " [STAGE-1] Esperando acoplamiento de hilos (waitForReceptor) en puerto pasivo... (Timeout: 30s)");
            BitBridgeClient dataReceiver = context.transferManager().waitForReceptor(sessionId, 30);
            if (dataReceiver == null) {
                Logger.logError(logId + " ❌ [TIMEOUT] Se excedió el tiempo límite de 30 segundos. El receptor no levantó el canal de datos.");
                return;
            }
            Logger.logInfo(logId + " [STAGE-1] Sincronización exitosa. Canal de datos enlazado con el Receptor.");

            Logger.logInfo(logId + " [STAGE-2] Esperando resolución de la acción de handshake (waitForResponseAction)... (Timeout: 7s)");
            FileHandshakeAction action = context.transferManager().waitForResponseAction(sessionId, 7);

            if (action != FileHandshakeAction.ACCEPT_REQUEST) {
                Logger.logWarn(logId + " ⚠️ [RECHAZADO] El receptor canceló la transferencia o devolvió una acción no soportada: " + action);
                return;
            }

            Logger.logInfo(logId + " [STAGE-2] Petición ACEPTADA por el receptor. Transmitiendo START_TRANSFER a ambos extremos.");
            FileHandshakeCommunication start = new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId, com);
            dataReceiver.sendComunicacion(start);
            sender.sendComunicacion(start);

            // Desacoplar del Selector NIO para evitar concurrencia de lectura destructiva
            if (context.getNetworkEngine() instanceof NioServerEngine engine) {
                Logger.logInfo(logId + " [NIO-ENGINE] Dando de baja canales del Selector Loop para conmutar a Modo Bloqueante Puro (Pure Stream).");
                engine.unregisterChannel((SocketChannel) sender.getReadableChannel());
                engine.unregisterChannel((SocketChannel) dataReceiver.getReadableChannel());
                ((SocketChannel) sender.getReadableChannel()).configureBlocking(true);
                ((SocketChannel) dataReceiver.getReadableChannel()).configureBlocking(true);
            }

            boolean transferenciaActiva = true;
            int fileCounter = 0;

            Logger.logInfo(logId + " 🚀 [PIPELINE] Bucle de conmutación activado de forma atómica.");

            while (transferenciaActiva) {
                // Leer instrucción del emisor
                byte[] packetData = ProtocolService.readHandshakePacket(sender);
                Communication object = ProtocolService.fromBytes(packetData);

                if (object instanceof FileDirectoryCommunication meta) {
                    fileCounter++;
                    String tipoNodo = meta.isDirectory() ? "DIR" : "FILE";

                    Logger.logInfo(logId + String.format(" [NODE-%03d] [%s] Evaluando metadato entrante: %s (%s)",
                            fileCounter, tipoNodo, meta.getRelativePath(), formatSize(meta.getSize())));

                    // Reenviar metadato al receptor para su análisis
                    dataReceiver.sendComunicacion(meta);

                    // Escuchar decisión de redundancia del receptor
                    byte[] receptorAckRaw = ProtocolService.readHandshakePacket(dataReceiver);
                    Communication receptorResponse = ProtocolService.fromBytes(receptorAckRaw);

                    if (receptorResponse instanceof FileHandshakeCommunication resp) {
                        FileHandshakeAction accionReceptor = resp.getAction();

                        if (accionReceptor == FileHandshakeAction.SKIP_FILE) {
                            Logger.logInfo(logId + String.format("  ├── ⏩ [SKIP] Receptor indica archivo idéntico. Notificando al emisor para saltar."));
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                            continue;
                        }

                        // --- PUENTE DE CONTROL EXCLUSIVO RSYNC ---
                        if (accionReceptor == FileHandshakeAction.PROCESS_DELTAS) {
                            Logger.logWarn(logId + String.format("  ├── ⚡ [RSYNC-TUNNEL] Abriendo bypass diferencial para: %s", meta.getRelativePath()));
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                            // 1. Mover Firmas: Receptor -> Servidor -> Emisor
                            Logger.logInfo(logId + "  │   ├── [FASE 1] Extrayendo firmas Adler32/MD5 desde el receptor...");
                            byte[] signaturesRaw = ProtocolService.readHandshakePacket(dataReceiver);
                            Logger.logInfo(logId + "  │   │   └── Inyectando firmas (" + signaturesRaw.length + " bytes) al buffer del emisor.");
                            //sender.getWritableChannel().write(ByteBuffer.wrap(signaturesRaw));
                            sender.sendComunicacion(ProtocolService.fromBytes(signaturesRaw));

                            // 2. Mover Paquete de Deltas: Emisor -> Servidor -> Receptor
                            Logger.logInfo(logId + "  │   ├── [FASE 2] Capturando stream de deltas serializados del emisor...");
                            byte[] deltasRaw = ProtocolService.readHandshakePacket(sender);
                            Logger.logInfo(logId + "  │   │   └── Bombeando paquete delta (" + deltasRaw.length + " bytes) hacia el receptor.");
                            //dataReceiver.getWritableChannel().write(ByteBuffer.wrap(deltasRaw));
                            dataReceiver.sendComunicacion(ProtocolService.fromBytes(deltasRaw));

                            // 3. Esperar confirmación del receptor sobre el ensamble final
                            Logger.logInfo(logId + "  │   └── [FASE 3] Esperando verificación de Disk Flush del receptor...");
                            byte[] finalAck = ProtocolService.readHandshakePacket(dataReceiver);
                            //sender.getWritableChannel().write(ByteBuffer.wrap(finalAck));
                            sender.sendComunicacion(ProtocolService.fromBytes(finalAck));

                            Logger.logInfo(logId + "  │       └── Sincronización diferencial completada exitosamente.");
                            continue;
                        }

                        if (accionReceptor == FileHandshakeAction.START_TRANSFER) {
                            Logger.logInfo(logId + "  ├── 📥 [TRANSFER-STREAM] Modo tradicional activado. Solicitando payload crudo.");
                            sender.sendComunicacion(new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                            if (!meta.isDirectory()) {
                                Logger.logInfo(logId + "  │   ├── [KERNEL] Enlazando descriptores de socket (bridgeSocketChannels)...");
                                bridgeSocketChannelsNoShutdown(sender, dataReceiver, meta.getSize());

                                Logger.logInfo(logId + "  │   └── Descarga completa. Esperando ACK físico del receptor...");
                                byte[] finalAckFromReceptor = ProtocolService.readHandshakePacket(dataReceiver);
                                sender.getWritableChannel().write(ByteBuffer.wrap(finalAckFromReceptor));
                            } else {
                                Logger.logInfo(logId + "  │   └── Árbol de directorios creado en destino.");
                            }
                            continue;
                        }
                    }
                } else if (object instanceof FileHandshakeCommunication handshake && handshake.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                    Logger.logInfo(logId + " 🏁 [FINISH] Se interceptó la directiva global TRANSFER_DONE del emisor.");
                    transferenciaActiva = false;
                    dataReceiver.sendComunicacion(handshake);
                }
            }

            Logger.logInfo(logId + " 🔌 [SHUTDOWN] Cerrando sockets y liberando recursos de la sesión.");
            dataReceiver.shutDown();
            sender.shutDown();
            Logger.logInfo(logId + " ── RELAY DE DIRECTORIO FINALIZADO SIN ERRORES ──");

        } catch (Exception e) {
            Logger.logError(logId + " ❌ [FATAL-ERROR] Excepción crítica en el hilo del relay Rsync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método utilitario para formatear tamaños lógicos en el log del servidor
    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.2f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
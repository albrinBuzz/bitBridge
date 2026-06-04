package org.bitBridge.Client.managers;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.*;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.model.basic.FileDirectoryCommunication;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;
import org.bitBridge.shared.core.comunication.model.sync.BlockSignature;
import org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaInstruction;
import org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaPackage;
import org.bitBridge.shared.core.comunication.model.sync.RsyncSignatures;
import org.bitBridge.shared.network.NetworkConfig;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.Adler32;

import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_REQUEST;

public class FileTransferManager implements TransferManager {
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    private final TransferenciaController transferenciaController;
    private final String downloadDir;

    private static final int BLOCK_SIZE = 64 * 1024;
    private static final int TRANSFER_CHUNK_SIZE = 4 * 1024 * 1024;

    public FileTransferManager(TransferenciaController transferenciaController) {
        this.transferenciaController = transferenciaController;
        this.downloadDir = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR);
    }

    // ==========================================
    // --- LÓGICA DE ENVÍO (SENDER INDIVIDUAL) ---
    // ==========================================
    // --- LÓGICA DE ENVÍO PARA UN ARCHIVO ÚNICO (SENDER) ---
    public void sendFile(FileDirectoryCommunication com, File targetFile, String host, int port) {
        if (targetFile == null || !targetFile.isFile()) {
            Logger.logError("[SENDER] El archivo proporcionado no es válido o es un directorio.");
            return;
        }

        String sessionId = PREFIX_REQUEST + new Random().nextInt(10000);
        var totalSize = targetFile.length();
         var totalBytesProcessed = 0;

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(host, port));

            // 1. Handshake inicial de la sesión de transferencia
            ProtocolService.writeNIO(channel, new Mensaje(sessionId));


            FileDirectoryCommunication handshakeMeta = new FileDirectoryCommunication(
                    targetFile.getName(),
                    totalSize,
                    false,
                    targetFile.getName()
            );
            handshakeMeta.setRecipient(com.getRecipient());

            ProtocolService.writeNIO(channel, handshakeMeta);

            FileHandshakeCommunication respuesta = waitForHandshakeNIO(channel);

            if (respuesta != null && respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                long startTime = System.currentTimeMillis();
                String idTransfe = transferenciaController.addTransference(
                        FileTransferState.SENDING.name(), host, host, targetFile.getName(), this, totalSize
                );

                // ====================================================================
                // 🚨 LOGICAESPEJO DE ENVIAR_RECURSIVO (Para un solo archivo)
                // ====================================================================
                String relativePath = targetFile.getName(); // Al ser único, su ruta base es su propio nombre
                long lastModified = targetFile.lastModified();

                FileDirectoryCommunication meta = new FileDirectoryCommunication(
                        targetFile.getName(),
                        totalSize,
                        false, // isDirectory = false
                        relativePath
                );
                meta.setLastModified(lastModified);
                meta.setRecipient(com.getRecipient());

                // Enviar metadatos específicos del archivo individual
                ProtocolService.writeNIO(channel, meta);

                // Esperar la decisión del receptor procesada por el Relay del Servidor
                FileHandshakeCommunication ack = waitForHandshakeNIO(channel);

                if (ack.getAction() == FileHandshakeAction.SKIP_FILE) {
                    Logger.logInfo("[SENDER] -> [OMITIDO] (Idéntico en destino): " + relativePath);
                    totalBytesProcessed += totalSize;
                    transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
                }
                else if (ack.getAction() == FileHandshakeAction.PROCESS_DELTAS) {
                    // --- FASE 2 RSYNC: PROCESAR DELTAS ---
                    Logger.logWarn("[SENDER] -> [MODIFICADO] Ejecutando algoritmo Rsync para archivo único: " + relativePath);

                    RsyncSignatures signatures = (RsyncSignatures) ProtocolService.readNIO(channel);
                    procesarYEnviarDeltas(channel, targetFile, signatures, idTransfe);

                    ProtocolService.readNIO(channel); // ACK final de sincronización del servidor
                }
                else {
                    // Transferencia limpia tradicional Zero-Copy
                    Logger.logInfo("[SENDER] -> [NUEVO] Transfiriendo completo: " + relativePath);
                    enviarArchivoCompletoNIO(channel, targetFile, idTransfe);
                    ProtocolService.readNIO(channel); // Esperar confirmación de guardado físico del receptor
                }

                // Notificar el cierre estructural de la sesión individual
                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));
                mostrarEstadisticasFinales(System.currentTimeMillis() - startTime, totalSize);
            }
        } catch (Exception e) {
            Logger.logError("Error NIO Send File: " + e.getMessage());
        }
    }

    private void procesarYEnviarDeltas(SocketChannel socket, File file, RsyncSignatures signatures, String idTransfe) throws Exception {
        // Delegación atómica a tu analizador de rolling-hash O(1)
        RsyncDeltaPackage deltaPackage = calcularDeltasLocales(file, signatures);

        // Serializar e inyectar al canal físico de red para el relevo
        ProtocolService.writeNIO(socket, deltaPackage);

        // Actualizar métricas visuales basadas en el tamaño procesado completo del archivo
        transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, file.length(), file.length());
    }

    private RsyncDeltaPackage calcularDeltasLocales(File file, RsyncSignatures remoteSignatures) throws Exception {
        byte[] fileData = Files.readAllBytes(file.toPath());
        int n = fileData.length;

        Map<Long, List<BlockSignature>> adlerMap = new HashMap<>();
        for (BlockSignature sig : remoteSignatures.getSignatures()) {
            adlerMap.computeIfAbsent(sig.getAdler32(), k -> new ArrayList<>()).add(sig);
        }

        List<RsyncDeltaInstruction> instructions = new ArrayList<>();
        ByteArrayOutputStream literalBuffer = new ByteArrayOutputStream();

        Adler32 adler = new Adler32();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        int i = 0;
        int matchedBlocks = 0;
        int literalBytesCount = 0;

        while (i < n) {
            int remainingBytes = n - i;
            int currentBlockSize = Math.min(BLOCK_SIZE, remainingBytes);

            if (currentBlockSize < BLOCK_SIZE && remainingBytes == currentBlockSize) {
                for (int j = i; j < n; j++) {
                    literalBuffer.write(fileData[j]);
                    literalBytesCount++;
                }
                break;
            }

            adler.reset();
            adler.update(fileData, i, currentBlockSize);
            long currentAdler = adler.getValue();

            boolean matchFound = false;
            if (adlerMap.containsKey(currentAdler)) {
                md5.reset();
                md5.update(fileData, i, currentBlockSize);
                byte[] currentMd5 = md5.digest();

                for (BlockSignature sig : adlerMap.get(currentAdler)) {
                    if (Arrays.equals(sig.getMd5(), currentMd5)) {
                        if (literalBuffer.size() > 0) {
                            instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
                            literalBuffer.reset();
                        }
                        instructions.add(new RsyncDeltaInstruction(sig.getBlockIndex()));
                        i += currentBlockSize;
                        matchFound = true;
                        matchedBlocks++;
                        break;
                    }
                }
            }

            if (!matchFound) {
                literalBuffer.write(fileData[i]);
                literalBytesCount++;
                i++;
            }
        }

        if (literalBuffer.size() > 0) {
            instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
        }

        Logger.logInfo("[CLIENT-SENDER] Análisis Delta completado. Bloques coincidentes: " + matchedBlocks + " | Bytes literales: " + literalBytesCount);
        return new RsyncDeltaPackage(instructions);
    }

    private void enviarArchivoCompletoNIO(SocketChannel socketChannel, File file, String idTransfe) throws Exception {
        try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long position = 0;
            long size = file.length();
            while (position < size && running) {
                checkPaused();
                long transferred = fileChannel.transferTo(position, Math.min(TRANSFER_CHUNK_SIZE, size - position), socketChannel);
                if (transferred <= 0) {
                    Thread.sleep(10);
                    continue;
                }
                position += transferred;
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, position, size);
            }
        }
    }

    // ===============================================
    // --- LÓGICA DE RECEPCIÓN (RECEPTOR INDIVIDUAL) ---
    // ===============================================
    // --- LÓGICA DE RECEPCIÓN PARA UN ARCHIVO ÚNICO (RECEPTOR) ---
    public void receiveFile(String host, int port, FileHandshakeCommunication handshake) {
        String sessionId = handshake.getSessionId();
        var info = handshake.getFileInfo();

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);

            if (transferenciaController.notifyTranference(handshake)) {
                channel.connect(new InetSocketAddress(host, port));

                if (channel.isConnected()) {
                    ProtocolService.writeNIO(channel, new Mensaje(sessionId));
                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                    if (confirmarInicioNIO(channel, sessionId)) {
                        String idTransfe = transferenciaController.addTransference(
                                FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this, info.getSize()
                        );

                        // 1. LEER EL METADATO INDIVIDUAL (Obligatorio para vaciar el envío del Server Relay)
                        Communication meta = ProtocolService.readNIO(channel);

                        if (meta instanceof FileDirectoryCommunication fileMeta) {
                            Path destPath = Paths.get(downloadDir, fileMeta.getRelativePath());

                            if (Files.exists(destPath)) {
                                long localSize = Files.size(destPath);
                                long localLastModified = Files.getLastModifiedTime(destPath).toMillis();

                                if (localSize == fileMeta.getSize() && localLastModified >= fileMeta.getLastModified()) {
                                    Logger.logInfo("[RECEPTOR-FILE] Omitido por ser idéntico: " + fileMeta.getRelativePath());
                                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                    return;
                                }

                                // --- RSYNC ARCHIVO ÚNICO ---
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));
                                RsyncSignatures signatures = generarFirmasLocales(destPath);
                                ProtocolService.writeNIO(channel, signatures);

                                RsyncDeltaPackage deltaPkg = (RsyncDeltaPackage) ProtocolService.readNIO(channel);
                                reconstruirArchivoRsync(destPath, deltaPkg);

                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                            } else {
                                // --- TRANSMISIÓN COMPLETA ---
                                if (destPath.getParent() != null) Files.createDirectories(destPath.getParent());

                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                recibirArchivoCompletoNIO(channel, destPath, fileMeta.getSize(), idTransfe);
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                            }
                        }

                        // 2. CONSUMIR EL TRANSFER_DONE PARA CERRAR ATÓMICAMENTE
                        Communication finalAck = ProtocolService.readNIO(channel);
                        if (finalAck instanceof FileHandshakeCommunication h && h.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                            Logger.logInfo("[RECEPTOR-FILE] Flujo completado y cerrado limpiamente.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.logError("Error crítico en FileTransferManager: " + e.getMessage());
        }
    }

    private RsyncSignatures generarFirmasLocales(Path path) throws Exception {
        List<BlockSignature> list = new ArrayList<>();
        byte[] fileData = Files.readAllBytes(path);
        int totalBytes = fileData.length;
        int index = 0;
        int blockIdx = 0;

        Adler32 adler = new Adler32();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        while (index < totalBytes) {
            int length = Math.min(BLOCK_SIZE, totalBytes - index);

            adler.reset();
            adler.update(fileData, index, length);
            long adlerHash = adler.getValue();

            md5.reset();
            md5.update(fileData, index, length);
            byte[] md5Hash = md5.digest();

            list.add(new BlockSignature(blockIdx++, adlerHash, md5Hash));
            index += length;
        }
        return new RsyncSignatures(list);
    }

    private void reconstruirArchivoRsync(Path targetPath, RsyncDeltaPackage packageDeltas) throws Exception {
        Path tempFile = Paths.get(targetPath.toString() + ".tmp");
        byte[] originalData = Files.readAllBytes(targetPath);

        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            for (RsyncDeltaInstruction inst : packageDeltas.getInstructions()) {
                if (inst.isLiteral()) {
                    fos.write(inst.getLiteralData());
                } else {
                    int blockIdx = inst.getBlockIndex();
                    int startOffset = blockIdx * BLOCK_SIZE;
                    int length = Math.min(BLOCK_SIZE, originalData.length - startOffset);
                    fos.write(originalData, startOffset, length);
                }
            }
        }
        Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void recibirArchivoCompletoNIO(SocketChannel channel, Path dest, long size, String idTrans) throws Exception {
        try (FileChannel fileChannel = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long readTotal = 0;
            while (readTotal < size) {
                long read = fileChannel.transferFrom(channel, readTotal, Math.min(size - readTotal, TRANSFER_CHUNK_SIZE));
                if (read <= 0) {
                    Thread.sleep(1);
                    if (read == -1) throw new IOException("Desconexión prematura del flujo de datos");
                    continue;
                }
                readTotal += read;
                transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, idTrans, readTotal, size);
            }
            fileChannel.force(true);
        }
    }

    private boolean confirmarInicioNIO(SocketChannel channel, String sessionId) throws Exception {
        Communication comm = ProtocolService.readNIO(channel);
        return comm instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER;
    }

    private FileHandshakeCommunication waitForHandshakeNIO(SocketChannel channel) throws Exception {
        while (running) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm instanceof FileHandshakeCommunication handshake) {
                return handshake;
            }
        }
        throw new IOException("Se cerró la interfaz de lectura.");
    }

    private void checkPaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused) pauseLock.wait();
        }
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private void mostrarEstadisticasFinales(long millis, long bytes) {
        Logger.logInfo("========================================");
        Logger.logInfo("   TRANSFERENCIA DE ARCHIVO FINALIZADA");
        Logger.logInfo("   Tiempo total: " + (millis / 1000.0) + " seg");
        Logger.logInfo("   Tamaño de archivo: " + formatSize(bytes));
        Logger.logInfo("========================================");
    }

    public void stop() { running = false; resume(); }
    public void pause() { paused = true; }
    public void resume() { synchronized (pauseLock) { paused = false; pauseLock.notifyAll(); } }
    @Override public void cancel() { stop(); }
}
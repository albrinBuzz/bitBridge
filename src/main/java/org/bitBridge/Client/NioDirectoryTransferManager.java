package org.bitBridge.Client;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.*;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.sync.BlockSignature;
import org.bitBridge.shared.core.comunication.sync.RsyncDeltaInstruction;
import org.bitBridge.shared.core.comunication.sync.RsyncDeltaPackage;
import org.bitBridge.shared.core.comunication.sync.RsyncSignatures;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Adler32;

public class NioDirectoryTransferManager implements TransferManager {
    private volatile boolean running = true;
    private final TransferenciaController transferenciaController;
    private final String downloadDir;
    private long totalBytesProcessed = 0;
    private long totalSize = 0;

    private static final int BLOCK_SIZE = 64 * 1024; // Bloques de 64KB para el rolling hash

    public NioDirectoryTransferManager(TransferenciaController controller) {
        this.transferenciaController = controller;
        this.downloadDir = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR);
    }

    // --- LÓGICA DE ENVÍO (SENDER) ---
    public void sendDirectory(File rootDir, String host, int port, String recipient) {
        String sessionId = "SENDER_" + new Random().nextInt(10000);
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicLong sizeCount = new AtomicLong(0);
        analyzeDirectory(rootDir, fileCount, sizeCount);
        this.totalSize = sizeCount.get();

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(host, port));

            ProtocolService.writeNIO(channel, new Mensaje(sessionId, CommunicationType.MESSAGE));
            ProtocolService.writeNIO(channel, new FileDirectoryCommunication(rootDir.getName(), fileCount.get(), recipient, totalSize));

            FileHandshakeCommunication respuesta = waitForHandshakeNIO(channel);

            if (respuesta != null && respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                long startTime = System.currentTimeMillis();
                String idTransfe = transferenciaController.addTransference(
                        FileTransferState.SENDING.name(), host, host, rootDir.getName(), this, totalSize
                );

                enviarRecursivoNIO(channel, rootDir, rootDir.getName(), idTransfe, recipient);
                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));

                mostrarEstadisticasFinales(System.currentTimeMillis() - startTime, totalSize);
            }
        } catch (Exception e) {
            Logger.logError("Error NIO Send: " + e.getMessage());
        }
    }

    private void analyzeDirectory(File dir, AtomicInteger files, AtomicLong size) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isFile()) {
                files.incrementAndGet();
                size.addAndGet(f.length());
            } else {
                analyzeDirectory(f, files, size);
            }
        }
    }

    private void enviarRecursivoNIO(SocketChannel socket, File file, String rootName, String idTransfe, String recipient) throws Exception {
        File[] files = file.listFiles();
        if (files == null) return;

        for (File target : files) {
            String relativePath = target.getAbsolutePath().substring(target.getAbsolutePath().indexOf(rootName));
            boolean isDir = target.isDirectory();

            // AQUÍ OBTENEMOS LA FECHA (en milisegundos desde el epoch)
            long lastModified = target.lastModified();

            FileDirectoryCommunication meta = new FileDirectoryCommunication(
                    target.getName(),
                    isDir ? 0 : target.length(),
                    isDir,
                    relativePath
            );

            // Inyectamos la fecha
            meta.setLastModified(lastModified);
            meta.setRecipient(recipient);

            ProtocolService.writeNIO(socket, meta);

            FileHandshakeCommunication ack = waitForHandshakeNIO(socket);

            if (ack.getAction() == FileHandshakeAction.SKIP_FILE) {
                //Logger.logInfo("[SENDER] -> [OMITIDO] (Idéntico en destino): " + relativePath);
                totalBytesProcessed += target.length();
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
                continue;
            }

            if (isDir) {
                enviarRecursivoNIO(socket, target, rootName, idTransfe, recipient);
            } else if (ack.getAction() == FileHandshakeAction.PROCESS_DELTAS) {
                // --- RSYNC FASE 2: PROCESAR DELTAS EN EL EMISOR ---
                Logger.logWarn("[SENDER] -> [MODIFICADO] Ejecutando algoritmo Rsync para: " + relativePath);

                // Leer las firmas enviadas por el receptor
                RsyncSignatures signatures = (RsyncSignatures) ProtocolService.readNIO(socket);
                procesarYEnviarDeltas(socket, target, signatures, idTransfe);

                ProtocolService.readNIO(socket); // ACK final de sincronización del archivo
            } else {
                // Transferencia limpia y completa (Zero-Copy)
                Logger.logInfo("[SENDER] -> [NUEVO] Transfiriendo completo: " + relativePath);
                enviarArchivoCompletoNIO(socket, target, idTransfe);
                ProtocolService.readNIO(socket);
            }
        }
    }

    private void enviarArchivoCompletoNIO(SocketChannel socket, File file, String idTransfe) throws Exception {
        try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long pos = 0, size = file.length(), chunk = 4 * 1024 * 1024;
            while (pos < size) {
                long transferred = fc.transferTo(pos, Math.min(size - pos, chunk), socket);
                if (transferred <= 0) { Thread.sleep(10); continue; }
                pos += transferred;
                totalBytesProcessed += transferred;
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
            }
        }
    }

    private void procesarYEnviarDeltas(SocketChannel socket, File file, RsyncSignatures signatures, String idTransfe) throws Exception {
        // Mapear firmas para búsqueda O(1)
        Map<Long, List<BlockSignature>> adlerMap = new HashMap<>();
        for (BlockSignature sig : signatures.getSignatures()) {
            adlerMap.computeIfAbsent(sig.getAdler32(), k -> new ArrayList<>()).add(sig);
        }

        byte[] fileData = Files.readAllBytes(file.toPath()); // Para el rolling de ventanas dinámicas
        int n = fileData.length;

        List<RsyncDeltaInstruction> instructions = new ArrayList<>();
        ByteArrayOutputStream literalBuffer = new ByteArrayOutputStream();

        Adler32 adler = new Adler32();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        int i = 0;
        while (i < n) {
            int remainingBytes = n - i;
            int currentBlockSize = Math.min(BLOCK_SIZE, remainingBytes);

            if (currentBlockSize < BLOCK_SIZE && remainingBytes == currentBlockSize) {
                // Buffer muy pequeño restante, enviar remanente como literal directa
                for (int j = i; j < n; j++) literalBuffer.write(fileData[j]);
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
                        // Volcar literales acumulados antes de indexar bloque coincidente
                        if (literalBuffer.size() > 0) {
                            instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
                            literalBuffer.reset();
                        }
                        instructions.add(new RsyncDeltaInstruction(sig.getBlockIndex()));
                        i += currentBlockSize;
                        matchFound = true;
                        break;
                    }
                }
            }

            if (!matchFound) {
                literalBuffer.write(fileData[i]);
                i++; // Avanzar ventana rodante byte a byte
            }
        }

        if (literalBuffer.size() > 0) {
            instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
        }

        // Serializar y mandar todas las instrucciones al canal
        RsyncDeltaPackage deltaPackage = new RsyncDeltaPackage(instructions);
        ProtocolService.writeNIO(socket, deltaPackage);

        // Actualizar métricas estimadas basadas en la reducción física del delta
        totalBytesProcessed += file.length();
        transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
    }

    // --- LÓGICA DE RECEPCIÓN (RECEPTOR) ---
    public void receiveDirectory(String host, int port, FileHandshakeCommunication handshake) {
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
                    ProtocolService.writeNIO(channel, new Mensaje(sessionId, CommunicationType.MESSAGE));
                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                    if (confirmarInicioNIO(channel, sessionId)) {
                        String idTransfe = transferenciaController.addTransference(
                                FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this, info.getSize()
                        );

                        while (running) {
                            Communication meta = ProtocolService.readNIO(channel);

                            if (meta instanceof FileDirectoryCommunication fileMeta) {
                                Path destPath = Paths.get(downloadDir, fileMeta.getRelativePath());

                                if (Files.exists(destPath)) {
                                    if (fileMeta.isDirectory()) {
                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                        continue;
                                    } else {

                                        long localSize = Files.size(destPath);
                                        long localLastModified = Files.getLastModifiedTime(destPath).toMillis();

                                        if (localSize == fileMeta.getSize() && localLastModified >= fileMeta.getLastModified()) {
                                            //Logger.logInfo("[RECEPTOR] -> [OMITIDO] Archivo idéntico (Size + Time): " + fileMeta.getRelativePath());
                                            ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                            continue;
                                        }

                                        /*if (localSize == fileMeta.getSize()) {
                                            Logger.logInfo("[RECEPTOR] -> [OMITIDO] Tamaños idénticos: " + fileMeta.getRelativePath());
                                            ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                            continue;
                                        }*/

                                        // --- RSYNC FASE 1: GENERAR Y MANDAR SIGNATURES ---
                                        Logger.logInfo("[RECEPTOR] -> [DIFERENCIA DETECTADA] Analizando delta para: " + fileMeta.getRelativePath());
                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                                        RsyncSignatures signatures = generarFirmasLocales(destPath);
                                        ProtocolService.writeNIO(channel, signatures);

                                        // --- RSYNC FASE 3: RECIBIR DELTAS Y RECONSTRUIR ---
                                        RsyncDeltaPackage deltaPkg = (RsyncDeltaPackage) ProtocolService.readNIO(channel);
                                        reconstruirArchivoRsync(destPath, deltaPkg);

                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                                        /*CompletableFuture.runAsync(() -> {
                                            try {
                                                reconstruirArchivoRsync(destPath, deltaPkg);
                                                // Notificar al emisor que puede continuar con el siguiente archivo
                                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                            } catch (Exception e) {
                                                Logger.logError("Error en reconstrucción asíncrona: " + e.getMessage());
                                            }
                                        });*/
                                        continue;
                                    }
                                }

                                if (fileMeta.isDirectory()) {
                                    Files.createDirectories(destPath);
                                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                    continue;

                                }

                                // Recepción tradicional limpia si el archivo es nuevo
                                Files.createDirectories(destPath.getParent());
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                recibirArchivoCompletoNIO(channel, destPath, fileMeta.getSize(), idTransfe, info.getSize());
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                            } else if (meta instanceof FileHandshakeCommunication h && h.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.logError("Error NIO Receive: " + e.getMessage());
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
        Logger.logInfo("[RECEPTOR] -> [RSYNC EXITOSO] Archivo reconstruido y sincronizado: " + targetPath.getFileName());
    }

    private void recibirArchivoCompletoNIO(SocketChannel channel, Path dest, long size, String id, long total) throws Exception {
        try (FileChannel fileChannel = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long readTotal = 0, limit = 4 * 1024 * 1024;
            while (readTotal < size) {
                long read = fileChannel.transferFrom(channel, readTotal, Math.min(size - readTotal, limit));
                if (read <= 0) { Thread.sleep(1); if (read == -1) throw new IOException("Desconexión"); continue; }
                readTotal += read;
                totalBytesProcessed += read;
                transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, id, totalBytesProcessed, total);
            }
            fileChannel.force(true);
        }
    }

    private boolean confirmarInicioNIO(SocketChannel channel, String sessionId) throws Exception {
        while (true) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER) return true;
            return false;
        }
    }

    private FileHandshakeCommunication waitForHandshakeNIO(SocketChannel channel) throws Exception {
        while (running) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm instanceof FileHandshakeCommunication handshake) return handshake;
        }
        throw new IOException("Cerrado");
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private void mostrarEstadisticasFinales(long millis, long bytes) {
        Logger.logInfo("========================================");
        Logger.logInfo("   TRANSFERENCIA RSYNC FINALIZADA");
        Logger.logInfo("   Tiempo total: " + (millis / 1000.0) + " seg");
        Logger.logInfo("   Volumen total gestionado: " + formatSize(bytes));
        Logger.logInfo("========================================");
    }

    @Override public void stop() { running = false; }
    @Override public void cancel() { stop(); }
    @Override public void pause() {}
    @Override public void resume() {}
}
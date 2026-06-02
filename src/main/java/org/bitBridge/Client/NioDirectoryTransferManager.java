package org.bitBridge.Client;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.*;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class NioDirectoryTransferManager implements TransferManager {
    private volatile boolean running = true;
    private final TransferenciaController transferenciaController;
    private final String downloadDir;
    private long totalBytesProcessed = 0;
    private long totalSize = 0;

    public NioDirectoryTransferManager(TransferenciaController controller) {
        this.transferenciaController = controller;
        this.downloadDir = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR);
    }

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
                Logger.logInfo("Iniciando cronómetro de transferencia...");

                String idTransfe = transferenciaController.addTransference(
                        FileTransferState.SENDING.name(), host, host, rootDir.getName(), this, totalSize
                );

                enviarRecursivoNIO(channel, rootDir, rootDir.getName(), idTransfe, recipient);

                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));

                long endTime = System.currentTimeMillis();
                long durationMillis = endTime - startTime;

                mostrarEstadisticasFinales(durationMillis, totalSize);
            }
        } catch (Exception e) {
            Logger.logError("Error NIO Send: " + e.getMessage());
        }
    }

    private void enviarRecursivoNIO(SocketChannel socket, File current, String rootName, String idTransfe, String recipient) throws Exception {
        File[] files = current.listFiles();
        if (files == null) {
            Logger.logWarn("[SENDER-NIO] No se pudo acceder a: " + current.getAbsolutePath());
            return;
        }

        for (File file : files) {
            String relativePath = file.getAbsolutePath().substring(file.getAbsolutePath().indexOf(rootName));
            boolean isDir = file.isDirectory();

            FileDirectoryCommunication meta = new FileDirectoryCommunication(file.getName(), isDir ? 0 : file.length(), isDir, relativePath);
            meta.setRecipient(recipient);
            ProtocolService.writeNIO(socket, meta);

            FileHandshakeCommunication ack = waitForHandshakeNIO(socket);

            // --- LOG EMISOR: ARCHIVO OMITIDO ---
            if (ack.getAction() == FileHandshakeAction.SKIP_FILE) {
                //Logger.logInfo("[SENDER] -> [OMITIDO] (Ya existe en destino): " + relativePath + " (" + formatSize(file.length()) + ")");
                totalBytesProcessed += file.length();
                transferenciaController.updateProgressMetrics(
                        FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize
                );
                continue;
            }

            if (!(ack.getAction() == FileHandshakeAction.START_TRANSFER)) {
                throw new IOException("No se recibió ACK válido del servidor para: " + relativePath);
            }

            if (isDir) {
                enviarRecursivoNIO(socket, file, rootName, idTransfe, recipient);
            } else {
                // --- LOG EMISOR: TRANSFERENCIA EN CURSO ---
                Logger.logInfo("[SENDER] -> [ENVIANDO NUEVO]: " + relativePath + " (" + formatSize(file.length()) + ")");

                try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
                    long position = 0;
                    long size = file.length();
                    long maxChunkSize = 4 * 1024 * 1024;

                    while (position < size) {
                        long toTransfer = Math.min(size - position, maxChunkSize);
                        long transferred = fileChannel.transferTo(position, toTransfer, socket);

                        if (transferred <= 0) {
                            Thread.sleep(10);
                            continue;
                        }
                        position += transferred;
                        totalBytesProcessed += transferred;

                        transferenciaController.updateProgressMetrics(
                                FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize
                        );
                    }
                }
                ProtocolService.readNIO(socket);
            }
        }
    }

    public void receiveDirectory(String host, int port, FileHandshakeCommunication handshake) {
        String sessionId = handshake.getSessionId();
        var info = handshake.getFileInfo();
        Logger.logInfo("[RECEPTOR-NIO] Solicitud recibida. Sesión: " + sessionId + " | Archivo: " + info.getName()
                + "| Longitud: " + formatSize(info.getSize()));

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

                                // --- CONTROL DE REGISTRO EN RECEPTOR ---
                                if (Files.exists(destPath)) {
                                    if (fileMeta.isDirectory()) {
                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                        continue;
                                    } else {
                                        long localSize = Files.size(destPath);
                                        if (localSize == fileMeta.getSize()) {
                                            // --- LOG RECEPTOR: SALTO POR COINCIDENCIA ---
                                            Logger.logInfo("[RECEPTOR] -> [SALTADO] Coincide tamaño: " + fileMeta.getRelativePath() + " (" + formatSize(localSize) + ")");
                                            ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                            continue;
                                        }

                                        // Caso de tamaño diferente (Regla por defecto de omisión actual)
                                        Logger.logWarn("[RECEPTOR] -> [SALTADO] Existe pero difiere en tamaño (Local: " + formatSize(localSize) + " vs Red: " + formatSize(fileMeta.getSize()) + "): " + fileMeta.getRelativePath());
                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                        continue;
                                    }
                                }

                                if (fileMeta.isDirectory()) {
                                    Files.createDirectories(destPath);
                                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                    continue;
                                }

                                // --- LOG RECEPTOR: DESCARGANDO NUEVO ---
                                Logger.logInfo("[RECEPTOR] -> [DESCARGANDO NUEVO]: " + fileMeta.getRelativePath() + " (" + formatSize(fileMeta.getSize()) + ")");

                                Files.createDirectories(destPath.getParent());
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                                try (FileChannel fileChannel = FileChannel.open(destPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                                    long bytesToRead = fileMeta.getSize();
                                    long bytesReadTotal = 0;
                                    long chunkLimit = 4 * 1024 * 1024;

                                    while (bytesReadTotal < bytesToRead) {
                                        long remaining = bytesToRead - bytesReadTotal;
                                        long toRead = Math.min(remaining, chunkLimit);

                                        long read = fileChannel.transferFrom(channel, bytesReadTotal, toRead);

                                        if (read <= 0) {
                                            Thread.sleep(1);
                                            if (read == -1) throw new IOException("El emisor cerró la conexión inesperadamente.");
                                            continue;
                                        }

                                        bytesReadTotal += read;
                                        totalBytesProcessed += read;

                                        transferenciaController.updateProgressMetrics(
                                                FileTransferState.RECEIVING, idTransfe, totalBytesProcessed, info.getSize()
                                        );
                                    }
                                    fileChannel.force(true);
                                }
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

    private void analyzeDirectory(File dir, AtomicInteger files, AtomicLong size) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isFile()) {
                files.incrementAndGet();
                size.addAndGet(f.length());
            } else analyzeDirectory(f, files, size);
        }
    }

    private boolean confirmarInicioNIO(SocketChannel channel, String sessionId) throws Exception {
        while (true) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm instanceof FileHandshakeCommunication f) {
                if (f.getAction() == FileHandshakeAction.START_TRANSFER && sessionId.equals(f.getSessionId())) {
                    return true;
                }
            }
            Logger.logError("Handshake inválido recibido");
            return false;
        }
    }

    private FileHandshakeCommunication waitForHandshakeNIO(SocketChannel channel) throws Exception {
        while (running) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm == null) continue;
            if (comm instanceof FileHandshakeCommunication handshake) {
                return handshake;
            }
        }
        throw new IOException("Se detuvo la espera del handshake porque el manager ya no está activo.");
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private void mostrarEstadisticasFinales(long millis, long bytes) {
        double seconds = millis / 1000.0;
        String tiempoFormateado = seconds < 60 ? String.format("%.2f segundos", seconds) : String.format("%d min %.2f seg", (long)(seconds / 60), seconds % 60);
        double speedMBs = (bytes / 1024.0 / 1024.0) / (seconds > 0 ? seconds : 1);

        Logger.logInfo("========================================");
        Logger.logInfo("   TRANSFERENCIA COMPLETADA");
        Logger.logInfo("   Tiempo total: " + tiempoFormateado);
        Logger.logInfo("   Tamaño total analizado: " + formatSize(bytes));
        Logger.logInfo(String.format("   Velocidad media: %.2f MB/s", speedMBs));
        Logger.logInfo("========================================");
    }

    @Override public void stop() { running = false; }
    @Override public void cancel() { stop(); }
    @Override public void pause() {}
    @Override public void resume() {}
}
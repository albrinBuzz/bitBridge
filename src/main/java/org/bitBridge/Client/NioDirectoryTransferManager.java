package org.bitBridge.Client;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.shared.*;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.Arrays;
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
        ConfiguracionCliente config = new ConfiguracionCliente();
        this.downloadDir = config.obtener("cliente.directorio_descargas");
    }

    public void sendDirectory(File rootDir, String host, int port, String recipient) {
        String sessionId = "SENDER_" + new Random().nextInt(10000);

        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicLong sizeCount = new AtomicLong(0);
        analyzeDirectory(rootDir, fileCount, sizeCount);
        this.totalSize = sizeCount.get();

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true); // Envío inmediato sin esperar a llenar el paquete
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024); // Buffer de 1MB para suavizar picos de red
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(host, port));

            ProtocolService.writeNIO(channel, new Mensaje(sessionId, CommunicationType.MESSAGE));
            ProtocolService.writeNIO(channel, new FileDirectoryCommunication(rootDir.getName(), fileCount.get(), recipient, totalSize));

            FileHandshakeCommunication respuesta = waitForHandshakeNIO(channel);

            if (respuesta != null && respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {

                // --- INICIO DEL CRONÓMETRO ---
                long startTime = System.currentTimeMillis();
                Logger.logInfo("Iniciando cronómetro de transferencia...");

                String idTransfe = transferenciaController.addTransference(
                        FileTransferState.SENDING.name(), host, host, rootDir.getName(), this, totalSize
                );

                // Envío recursivo
                enviarRecursivoNIO(channel, rootDir, rootDir.getName(), idTransfe, recipient);

                // Enviamos fin y esperamos el ACK final del servidor para asegurar que el receptor cerró todo
                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));

                // --- FIN DEL CRONÓMETRO ---
                long endTime = System.currentTimeMillis();
                long durationMillis = endTime - startTime;


                mostrarEstadisticasFinales(durationMillis, totalSize);
            }
        } catch (Exception e) {
            Logger.logError("Error NIO Send: " + e.getMessage());
        }
    }

    private void enviarRecursivoNIO(SocketChannel socket, File current, String rootName, String idTransfe,String recipient) throws Exception {
        File[] files = current.listFiles();
        if (files == null) {
            Logger.logWarn("[SENDER-NIO] No se pudo acceder a: " + current.getAbsolutePath());
            return;
        }
        //Logger.logInfo(Arrays.toString(files));

        for (File file : files) {
            //if (!running) return;
            String relativePath = file.getAbsolutePath().substring(file.getAbsolutePath().indexOf(rootName));

            boolean isDir = file.isDirectory();

            // 1. ENVIAR METADATOS
            FileDirectoryCommunication meta = new FileDirectoryCommunication(file.getName(), isDir ? 0 : file.length(), isDir, relativePath);
            meta.setRecipient(recipient);
            ProtocolService.writeNIO(socket, meta);



            FileHandshakeCommunication ack = waitForHandshakeNIO(socket);
            if (!(ack.getAction() == FileHandshakeAction.START_TRANSFER)) {
                throw new IOException("No se recibió ACK del servidor para: " + relativePath);
            }


            if (file.isDirectory()) {
                //Logger.logInfo("[SENDER-NIO] [DIR] -> " + relativePath);
                enviarRecursivoNIO(socket, file, rootName, idTransfe,recipient);
                //ProtocolService.readNIO(socket);
            } else {

                //Logger.logInfo("[SENDER-NIO] [FILE] " + relativePath + " (" + formatSize(file.length()) + ")");
                // Transferencia Zero-Copy con logging de progreso interno
                try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
                    long position = 0;
                    long size = file.length();
                    // Fragmentar en pedazos de 8MB evita que el canal se "atragante" en redes físicas
                    long maxChunkSize = 4 * 1024 * 1024;

                    while (position < size) {
                        long toTransfer = Math.min(size - position, maxChunkSize);
                        long transferred = fileChannel.transferTo(position, toTransfer, socket);

                        if (transferred <= 0) {
                            // El buffer de red está lleno. Esperamos un poco para que el Receptor vacíe datos.
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
                Communication ackFin = ProtocolService.readNIO(socket);
                //Logger.logInfo("[SENDER] Sincronizado: " + file.getName());
            }
        }
    }

    // --- LÓGICA DE RECEPCIÓN NIO ---
    public void receiveDirectory(String host, int port, FileHandshakeCommunication handshake) {
        String sessionId = handshake.getSessionId();
        var info = handshake.getFileInfo();
        Logger.logInfo("[RECEPTOR-NIO] Solicitud recibida. Sesión: " + sessionId + " | Archivo: " + info.getName()
                +"| Longitud: "+ formatSize(info.getSize()));

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true); // Envío inmediato sin esperar a llenar el paquete
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024); // Buffer de 1MB para suavizar picos de red
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);

            if (transferenciaController.notifyTranference(handshake)) {

                channel.connect(new InetSocketAddress(host, port));

                if (channel.isConnected()) {
                    //Logger.logInfo("[RECEPTOR-NIO] Conectando a " + host + ":" + port + "... Enviando handshake de identificación");

                    // Handshake inicial
                    ProtocolService.writeNIO(channel, new Mensaje(sessionId, CommunicationType.MESSAGE));
                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                    if (confirmarInicioNIO(channel, sessionId)) {

                        /*Communication confirm = ProtocolService.readNIO(channel);
                        if (!(confirm instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER))
                            return;*/

                        String idTransfe = transferenciaController.addTransference(
                                FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this, info.getSize()
                        );

                        while (running) {
                            Communication meta = ProtocolService.readNIO(channel);

                            if (meta instanceof FileDirectoryCommunication fileMeta) {
                                Path destPath = Paths.get(downloadDir, fileMeta.getRelativePath());

                                if (fileMeta.isDirectory()) {
                                    Files.createDirectories(destPath);

                                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                    continue;
                                }

                                Files.createDirectories(destPath.getParent());
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                                // Recepción NIO
                                try (FileChannel fileChannel = FileChannel.open(destPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                                    long bytesToRead = fileMeta.getSize();
                                    long bytesReadTotal = 0;

                                    // Limitar la ráfaga a 8MB ayuda a mantener el control y actualizar la UI
                                    long chunkLimit = 4 * 1024 * 1024;

                                    while (bytesReadTotal < bytesToRead) {
                                        long remaining = bytesToRead - bytesReadTotal;
                                        long toRead = Math.min(remaining, chunkLimit);

                                        long read = fileChannel.transferFrom(channel, bytesReadTotal, toRead);

                                        if (read <= 0) {
                                            // ¡CRÍTICO! En red real, si no hay datos, espera 1-5ms
                                            // para que el buffer de red se llene de nuevo.
                                            Thread.sleep(1);

                                            // Si el socket se cerró realmente, lanzamos error para no quedar en bucle
                                            if (read == -1) throw new IOException("El emisor cerró la conexión inesperadamente.");

                                            continue;
                                        }

                                        bytesReadTotal += read;
                                        totalBytesProcessed += read;

                                        transferenciaController.updateProgressMetrics(
                                                FileTransferState.RECEIVING, idTransfe, totalBytesProcessed, info.getSize()
                                        );
                                    }
                                    // Aseguramos que los datos se escriban físicamente en el disco
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
            // Delegación total al protocolo
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


        // El bucle continuará hasta que recibamos el handshake o se corte la conexión
        while (running) {
            // Delegación total de la lectura física al ProtocolService
            Communication comm = ProtocolService.readNIO(channel);

            if (comm == null) continue;

            // Si es el objeto de Handshake que buscamos, lo devolvemos y rompemos el bucle
            if (comm instanceof FileHandshakeCommunication handshake) {

                return handshake;
            }

            // Si llega una notificación o un mensaje de texto, lo logueamos
            // pero seguimos esperando en el bucle (no cortamos la transferencia)
            if (comm instanceof Mensaje m) {

            } else {

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
        String tiempoFormateado;

        if (seconds < 60) {
            tiempoFormateado = String.format("%.2f segundos", seconds);
        } else {
            long minutes = (long) (seconds / 60);
            double remainingSeconds = seconds % 60;
            tiempoFormateado = String.format("%d min %.2f seg", minutes, remainingSeconds);
        }

        // Calcular Velocidad: (Bytes / 1024 / 1024) / segundos = MB/s
        double speedMBs = (bytes / 1024.0 / 1024.0) / (seconds > 0 ? seconds : 1);

        Logger.logInfo("========================================");
        Logger.logInfo("   TRANSFERENCIA COMPLETADA");
        Logger.logInfo("   Tiempo total: " + tiempoFormateado);
        Logger.logInfo("   Tamaño: " + formatSize(bytes));
        Logger.logInfo(String.format("   Velocidad media: %.2f MB/s", speedMBs));
        Logger.logInfo("========================================");
    }
    @Override
    public void stop() { running = false; }
    @Override public void cancel() { stop(); }
    @Override public void pause() {} // En NIO Zero-Copy el pause es más complejo; requiere detener el loop de transferencia
    @Override public void resume() {}

}
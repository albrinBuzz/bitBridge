package org.bitBridge.Client;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.shared.*;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class FileTransferManager implements TransferManager {
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();
    private final ConfiguracionCliente configCliente;
    private final TransferenciaController transferenciaController;

    // Buffer optimizado para balancear memoria y rendimiento (128KB)
    private static final int BUFFER_SIZE = 128 * 1024;
    //private static final int BUFFER_SIZE = 2 * 1024 * 1024;

    public FileTransferManager(TransferenciaController transferenciaController) {
        this.configCliente = new ConfiguracionCliente();
        this.transferenciaController = transferenciaController;
    }


    public void sendFile(FileDirectoryCommunication com, File file, String SERVER_ADDRESS, int port) {
        String sessionId = "SENDER_" + new Random().nextInt(10000);



        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.configureBlocking(true); // Bloqueante para transferencia de archivos es más simple y rápido
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 4*1024 * 1024);
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            socketChannel.connect(new InetSocketAddress(SERVER_ADDRESS, port));


            if (socketChannel.isConnected()) {
                Logger.logInfo("[RECEPTOR-NIO] Conectando a " + SERVER_ADDRESS + ":" + port + "...");

                // Configuración de alto rendimiento


                // 1. Enviar Identificación y Metadatos (Protocolo JSON con los 6 bytes de cabecera)
                ProtocolService.writeNIO(socketChannel, new Mensaje(sessionId, CommunicationType.MESSAGE));


                ProtocolService.writeNIO(socketChannel, com);

                // 2. Esperar Handshake (Usando la lógica de lectura NIO que ya tienes)
                FileHandshakeCommunication respuesta = waitForHandshakeNIO(socketChannel);

                if (respuesta != null && respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                    Logger.logInfo("¡CONEXIÓN EXITOSA! El receptor aceptó. (Envío de bytes Inciado)");

                String idTransfe = transferenciaController.addTransference(
                        FileTransferState.SENDING.name(), com.getRecipient(), com.getRecipient(),
                        file.getName(), this, file.length());

                    long startNIO = System.nanoTime();

                    // --- ZERO COPY SEND ---
                    transferDataNIO(file, socketChannel, idTransfe);

                    long endNIO = System.nanoTime();
                    double segundos = (endNIO - startNIO) / 1_000_000_000.0;
                    Logger.logInfo("Transferencia completada en " + segundos + " seg.");
                }

            }

        } catch (Exception e) {
            Logger.logError("Error en sendFile NIO: " + e.getMessage());
        }
    }

    public void receiveFiles(String SERVER_ADDRESS, String port, FileHandshakeCommunication handshakeCommunication) {
        String sessionId = handshakeCommunication.getSessionId();
        var info = handshakeCommunication.getFileInfo();
        long fileSize = info.getSize();

        Logger.logInfo("[RECEPTOR-NIO] Solicitud recibida. Sesión: " + sessionId + " | Archivo: " + info.getName()
        +"| Longitud: "+ formatSize(info.getSize()));

        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.configureBlocking(true); // Bloqueante para transferencia de archivos es más simple y rápido
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            if (transferenciaController.notifyTranference(handshakeCommunication)) {

            socketChannel.connect(new InetSocketAddress(SERVER_ADDRESS, Integer.parseInt(port)));

            if (socketChannel.isConnected()) {
                    Logger.logInfo("[RECEPTOR-NIO] Conectando a " + SERVER_ADDRESS + ":" + port + "... Enviando handshake de identificación");

                    // 1. Identificación
                    Mensaje idMsg = new Mensaje(sessionId, CommunicationType.MESSAGE);
                    ProtocolService.writeNIO(socketChannel, idMsg);
                    Logger.logInfo("[RECEPTOR-NIO] ID de sesión enviado: " + sessionId);

                    // 2. Enviar Aceptación
                    FileHandshakeCommunication accept = new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId);
                    ProtocolService.writeNIO(socketChannel, accept);
                    Logger.logInfo("[RECEPTOR-NIO] ACCEPT_REQUEST enviado satisfactoriamente.");


                    String rutaFull = configCliente.obtener("cliente.directorio_descargas") + info.getName();


                if (confirmarInicioNIO(socketChannel, sessionId)) {

                    String idTrans = transferenciaController.addTransference(
                            FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this,fileSize);

                    Logger.logInfo("[RECEPTOR-NIO] Se Confirma el incio de la transaferencia");
                    // --- ZERO COPY RECEIVE ---
                    try (FileChannel fileChannel = FileChannel.open(Path.of(rutaFull),
                            StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

                        long totalRead = 0;
                        while (totalRead < fileSize && running) {
                            // Transferimos de 2MB en 2MB para poder actualizar la UI y pausar
                            long bytesToRead = Math.min(4 * 1024 * 1024, fileSize - totalRead);
                            long read = fileChannel.transferFrom(socketChannel, totalRead, bytesToRead);

                            if (read <= 0) break;
                            totalRead += read;
                            transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, idTrans, totalRead, fileSize);
                        }
                    }
                    Logger.logInfo("Archivo Guardado en "+rutaFull);
                }
                //restaurarMetadatos(rutaFull, info);
            }
        }
        } catch (Exception e) {
            Logger.logError("Error en receiveFiles NIO: " + e.getMessage());
        }
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private boolean confirmarInicioNIO(SocketChannel channel, String sessionId) throws Exception {
        Logger.logInfo("Esperando confirmación mediante ProtocolService...");
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

    private void transferDataNIO(File file, SocketChannel socketChannel, String idTrans) throws IOException, InterruptedException {
        try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long size = fileChannel.size();
            long position = 0;

            while (position < size && running) {
                checkPaused();
                // Transferimos en trozos para actualizar la barra de progreso
                long transferred = fileChannel.transferTo(position, Math.min(4 * 1024 * 1024, size - position), socketChannel);
                if (transferred <= 0) break;

                position += transferred;
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTrans, position, size);
            }
        }
    }

    private FileHandshakeCommunication waitForHandshakeNIO(SocketChannel channel) throws Exception {
        Logger.logInfo("Iniciando bucle de espera de Handshake (NIO)...");

        // El bucle continuará hasta que recibamos el handshake o se corte la conexión
        while (running) {
            // Delegación total de la lectura física al ProtocolService
            Communication comm = ProtocolService.readNIO(channel);

            if (comm == null) continue;

            // Si es el objeto de Handshake que buscamos, lo devolvemos y rompemos el bucle
            if (comm instanceof FileHandshakeCommunication handshake) {
                Logger.logInfo("Handshake recibido: " + handshake.getAction());
                return handshake;
            }

            // Si llega una notificación o un mensaje de texto, lo logueamos
            // pero seguimos esperando en el bucle (no cortamos la transferencia)
            if (comm instanceof Mensaje m) {
                Logger.logInfo("Mensaje del servidor recibido durante la espera: " + m.getContenido());
            } else {
                Logger.logInfo("Paquete de tipo " + comm.getType() + " ignorado, esperando Handshake...");
            }
        }

        throw new IOException("Se detuvo la espera del handshake porque el manager ya no está activo.");
    }


    private void checkPaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused) pauseLock.wait();
        }
    }



    public void stop() { running = false; resume(); }
    public void pause() { paused = true; }
    public void resume() { synchronized (pauseLock) { paused = false; pauseLock.notifyAll(); } }

    @Override public void cancel() { stop(); }
}
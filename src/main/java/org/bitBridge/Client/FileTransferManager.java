package org.bitBridge.Client;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.shared.*;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
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



        // Uso de try-with-resources para asegurar que el socket y los streams se cierren SIEMPRE
        try (Socket socket = new Socket(SERVER_ADDRESS, port)) {

            configurarSocket(socket);

            try (DataOutputStream salida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
                salida.flush();

                // Ejemplo de cómo obtener los atributos en el origen
                Path path = file.toPath();
                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);

                // Estos valores (en milisegundos o FileTime) deben viajar en tu objeto 'com'
                com.setCreationTime(attr.creationTime().toMillis());
                com.setLastModified(attr.lastModifiedTime().toMillis());

                // 2. Identificación técnica inicial (Protocolo JSON)
                // Enviamos el ID de sesión para que el servidor sepa que es una conexión de datos
                ProtocolService.writeFormattedPayload(salida, new Mensaje(sessionId, CommunicationType.MESSAGE));

                // Enviamos los metadatos del archivo (JSON)
                ProtocolService.writeFormattedPayload(salida, com);
                salida.flush();

                FileHandshakeCommunication respuesta = waitForHandshake(entrada);
                Logger.logInfo("Handshake recibido: " + respuesta.getAction());

                if (respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                    // Registro en la interfaz
                    String idTransfe = transferenciaController.addTransference(
                            FileTransferState.SENDING.name(),
                            com.getRecipient(),
                            com.getRecipient(),
                            file.getName(),
                            this,
                            file.length()
                    );
                    long startNIO = System.nanoTime();
                    transferData(file, salida, idTransfe);
                    long endNIO = System.nanoTime();

                    double segundosNIO = (endNIO - startNIO) / 1_000_000_000.0;
                    double mbSize =file.length() / (1024.0 * 1024.0);


                    Logger.logInfo("NIO Zero-Copy: "+(mbSize / segundosNIO)+"MB/s"+"("+segundosNIO+" seg)");
                    //Logger.logInfo("IO Copy: "+(mbSize / segundosNIO)+"MB/s"+"("+segundosNIO+" seg)");

                } else if (respuesta.getAction() == FileHandshakeAction.DECLINE_REQUEST) {
                    transferenciaController.notifyTranference(respuesta.getAction());
                }

                // Pequeña espera para asegurar que el buffer se vacíe antes de cerrar
                TimeUnit.MILLISECONDS.sleep(200);
            }
        } catch (Exception e) {
            Logger.logError("Error en sendFile: " + e.getMessage());
        }
    }

    public void receiveFiles(String SERVER_ADDRESS, String port, FileHandshakeCommunication handshakeCommunication) {
        String sessionId = handshakeCommunication.getSessionId();
        var info = handshakeCommunication.getFileInfo();
        long size= info.getSize();;

        try (Socket socket = new Socket(SERVER_ADDRESS, Integer.parseInt(port))) {
            configurarSocket(socket);

            try (DataOutputStream salida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

                salida.flush();


                if (transferenciaController.notifyTranference(handshakeCommunication)) {

                    // 1. Identificación Inicial (JSON)
                    ProtocolService.writeFormattedPayload(salida, new Mensaje(sessionId, CommunicationType.MESSAGE));

                    String idTrans = transferenciaController.addTransference(
                            FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this,size);

                    // 2. Aceptar la petición (JSON)
                    ProtocolService.writeFormattedPayload(salida, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                    // Esperar confirmación START_TRANSFER
                    if (confirmarInicio(entrada, sessionId)) {
                        String rutaFull = configCliente.obtener("cliente.directorio_descargas") + info.getName();
                        long startNIO = System.nanoTime();
                        long fileSize = info.getSize();

                        try (FileOutputStream fos = new FileOutputStream(rutaFull);
                             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                            byte[] buffer = new byte[BUFFER_SIZE];
                            long totalRead = 0;

                            while (totalRead < fileSize && running) {
                                int read = entrada.read(buffer);
                                if (read == -1) break;
                                bos.write(buffer, 0, read);
                                totalRead += read;

                                transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, idTrans, totalRead, fileSize);
                            }
                            bos.flush();
                        }

                        /*try (FileOutputStream fos = new FileOutputStream(rutaFull);
                             FileChannel fileChannel = fos.getChannel();
                             ReadableByteChannel socketChannel = Channels.newChannel(entrada)) {

                            long totalRead = 0;

                            while (totalRead < fileSize && running) {
                                // transferFrom es altamente eficiente para escribir de socket a disco
                                long read = fileChannel.transferFrom(socketChannel, totalRead, fileSize - totalRead);
                                if (read <= 0) break;

                                totalRead += read;
                                transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, idTrans, totalRead, fileSize);
                            }
                        }*/



                        /*try (FileOutputStream fos = new FileOutputStream(rutaFull);
                             FileChannel fileChannel = fos.getChannel();
                             ReadableByteChannel socketChannel = Channels.newChannel(entrada)) {

                            long totalRead = 0;

                            // Definimos un tamaño de fragmento (ej: 512KB o 1MB) para forzar actualizaciones
                            //long chunkSize = 2 * 1024 * 1024;
                            long chunkSize = 2048 * 1024;

                            while (totalRead < fileSize && running) {
                                // Calculamos cuánto falta, pero no pedimos más del tamaño del fragmento
                                long remaining = fileSize - totalRead;
                                long bytesToTransfer = Math.min(chunkSize, remaining);

                                // transferFrom ahora leerá máximo 1MB por iteración
                                long read = fileChannel.transferFrom(socketChannel, totalRead, bytesToTransfer);

                                if (read <= 0) break;

                                totalRead += read;

                                // Ahora esto se ejecutará después de cada fragmento de 1MB
                                transferenciaController.updateProgressMetrics(
                                        FileTransferState.RECEIVING,
                                        idTrans,
                                        totalRead,
                                        fileSize
                                );
                            }
                        }*/

                        long endNIO = System.nanoTime();

                        double segundosNIO = (endNIO - startNIO) / 1_000_000_000.0;
                        double mbSize =fileSize / (1024.0 * 1024.0);

                        Logger.logInfo("NIO Zero-Copy: "+(mbSize / segundosNIO)+"MB/s"+"("+segundosNIO+" seg)");
                        // 2. SEGUNDO: Aplicar metadatos con el archivo ya cerrado
                        try {
                            Path destino = Path.of(rutaFull);
                            FileTime creationTime = FileTime.fromMillis(info.getCreationTime());
                            FileTime lastModifiedTime = FileTime.fromMillis(info.getLastModified());

                            // Cambiar los atributos en el sistema de archivos
                            //Files.setAttribute(destino, "basic:creationTime", creationTime);

                            //Files.setAttribute(destino, "creationTime", creationTime);
                            //Files.setLastModifiedTime(destino, lastModifiedTime);
                            BasicFileAttributeView attributes = Files.getFileAttributeView(destino, BasicFileAttributeView.class);

                            // setTimes(lastModifiedTime, lastAccessTime, createTime)
                            attributes.setTimes(lastModifiedTime, lastModifiedTime, creationTime);

                            Logger.logInfo(creationTime.toString()+" "+lastModifiedTime.toString());
                            Logger.logInfo("Fecha de creacion-> "+creationTime.toString()+" Fecha De Modificacion :"+lastModifiedTime.toString());
                            Logger.logInfo("Metadatos restaurados para: " + info.getName());

                            Logger.logInfo("Atributos aplicados: Modificado=" + lastModifiedTime + " Creado=" + creationTime);
                        } catch (IOException e) {
                            Logger.logError("Error al restaurar metadatos: " + e.getMessage());
                        }
                    }
                } else {
                    ProtocolService.writeFormattedPayload(salida, new Mensaje(sessionId, CommunicationType.MESSAGE));
                    ProtocolService.writeFormattedPayload(salida, new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
                }
            }
        } catch (Exception e) {
            Logger.logError("Error en receiveFiles: " + e.getMessage());
        }
    }

    private void transferData(File file, DataOutputStream salida, String idTrans) throws IOException, InterruptedException {
        long length = file.length();
        long totalSent = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            int bytesRead;
            while (running && (bytesRead = bis.read(buffer)) != -1) {
                checkPaused();
                salida.write(buffer, 0, bytesRead);
                totalSent += bytesRead;

                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTrans, totalSent, length);
            }
            salida.flush();
        }
    }

    /*private void transferData(File file, DataOutputStream salida, String idTrans) throws IOException {
        long length = file.length();
        long totalSent = 0;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel fileChannel = raf.getChannel();
             // Obtenemos el canal del socket subyacente
             WritableByteChannel socketChannel = Channels.newChannel(salida)) {

            while (totalSent < length && running) {
                checkPaused();
                // Transfiere hasta 8MB por iteración para no bloquear el hilo demasiado tiempo
                long transferred = fileChannel.transferTo(totalSent, Math.min(8 * 1024 * 1024, length - totalSent), socketChannel);
                totalSent += transferred;

                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTrans, totalSent, length);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }*/

    // Mejora: Centraliza la configuración del socket
    private void configurarSocket(Socket socket) throws IOException {
        socket.setTcpNoDelay(true); // Desactiva algoritmo de Nagle para mayor fluidez
        socket.setSendBufferSize(2 * 1024 * 1024);
        socket.setReceiveBufferSize(2 * 1024 * 1024);
    }

    // Mejora: Evita duplicidad de código en el bucle de lectura de objetos
    private boolean confirmarInicio(DataInputStream entrada, String sessionId) throws Exception {
        while (true) {
            // Leemos el mensaje formateado en JSON
            Communication comm = ProtocolService.readFormattedPayload(entrada);

            if (comm instanceof FileHandshakeCommunication f) {
                return f.getAction() == FileHandshakeAction.START_TRANSFER && f.getSessionId().equals(sessionId);
            }

            if (comm == null) return false;
            // Si llega un mensaje de texto (notificación), seguimos esperando el handshake
        }
    }

    private FileHandshakeCommunication waitForHandshake(DataInputStream entrada) throws Exception {
        while (true) {
            // Leemos usando el nuevo protocolo JSON
            Communication comm = ProtocolService.readFormattedPayload(entrada);

            if (comm == null) throw new IOException("Conexión cerrada inesperadamente por el servidor.");

            // Si llega un mensaje de texto plano, lo logueamos pero no cortamos la espera
            if (comm instanceof Mensaje m) {
                Logger.logInfo("Notificación del servidor durante handshake: " + m.getContenido());
                continue;
            }

            // Si es el objeto de Handshake que esperamos, lo devolvemos
            if (comm instanceof FileHandshakeCommunication handshake) {
                return handshake;
            }

            // Si llega otra cosa que no esperamos, decidimos si ignorar o lanzar error
            Logger.logInfo("Tipo inesperado recibido: " + comm.getType());
        }
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
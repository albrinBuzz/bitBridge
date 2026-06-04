package org.bitBridge.Client.managers;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.*;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.model.basic.FileDirectoryCommunication;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DirectoryTransferManager implements TransferManager {
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    private String rutaCopia;
    private String carpeta;
    private String rutaCarpetaActual;

    // Cambiados a DataStreams para compatibilidad JSON/Binario
    private DataOutputStream out;
    private DataInputStream entrada;

    private ConfiguracionApp configCliente;
    private String nick;
    private int totalArchivos;
    private long tamanoTotal;
    private long totalBytesLeidos = 0;
    private TransferenciaController transferenciaController;
    private String recipient;
    private static final int BUFFER_SIZE = 128 * 1024; // 128KB

    public DirectoryTransferManager(TransferenciaController transferenciaController) {

        this.rutaCopia = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR);
        this.rutaCarpetaActual = rutaCopia;
        this.transferenciaController = transferenciaController;
    }

    public void sendDirectory(File archivo, String SERVER_ADDRESS, int port, String recipient) {
        this.carpeta = archivo.getName();
        this.nick = SERVER_ADDRESS;
        this.recipient = recipient;
        String sessionId = "SENDER_" + new Random().nextInt(10000);

        AtomicInteger totalArchivosContador = new AtomicInteger(0);
        AtomicLong totalTam = new AtomicLong(0);

        try {
            archivosTotales(archivo, totalArchivosContador, totalTam);
        } catch (IOException e) {
            Logger.logError("Error al contar archivos: " + e.getMessage());
            return;
        }

        this.totalArchivos = totalArchivosContador.get();
        this.tamanoTotal = totalTam.get();

        try (Socket socket = new Socket(SERVER_ADDRESS, port)) {
            socket.setTcpNoDelay(true);

            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.flush();

            // 1. Identificación y Metadatos de Carpeta (JSON)
            ProtocolService.writeFormattedPayload(out, new Mensaje(sessionId));
            ProtocolService.writeFormattedPayload(out, new FileDirectoryCommunication(archivo.getName(), totalArchivos, recipient, tamanoTotal));
            out.flush();

            // 2. Esperar Handshake (JSON)
            Communication respuesta = waitForHandshake(entrada);

            if (respuesta instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER) {
                String idTransfe = transferenciaController.addTransference(FileTransferState.SENDING.name(), nick, nick, archivo.getName(), this,tamanoTotal);

                // 3. Iniciar envío recursivo
                enviarDirectorio(archivo, idTransfe);

                // 4. Notificar fin de transferencia de carpeta (JSON)
                ProtocolService.writeFormattedPayload(out, new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));
                out.flush();

            } else if (respuesta instanceof FileHandshakeCommunication f) {
                transferenciaController.notifyTranference(f.getAction());
            }else {
                Logger.logInfo("Ninguna respuesta");
            }

            TimeUnit.MILLISECONDS.sleep(200);
        } catch (Exception e) {
            Logger.logError("Error en sendDirectory: " + e.getMessage());
        } finally {
            out = null;
            entrada = null;
        }
    }

    private Communication waitForHandshake(DataInputStream entrada) throws Exception {
        while (true) {
            Communication comm = ProtocolService.readFormattedPayload(entrada);
            if (comm == null) throw new IOException("Flujo nulo");

            if (comm instanceof Mensaje m) {
                Logger.logInfo("Notificación servidor: " + m.getContenido());
                continue;
            }
            return comm;
        }
    }

    public void enviarDirectorio(File archivo, String idTransfe) throws IOException, InterruptedException {
        File[] files = archivo.listFiles();
        if (files == null) return;

        // Manejo de carpetas vacías
        if (files.length == 0) {
            rutaCopia = archivo.getCanonicalPath().substring(archivo.getAbsolutePath().indexOf(carpeta));
            //ProtocolService.writeFormattedPayload(out, new FileDirectoryCommunication(archivo.getName(), 0, recipient));
            out.writeUTF(rutaCopia);
            out.flush();
            return;
        }

        for (File file : files) {
            if (!running) break;
            if (file.isFile()) {
                rutaCopia = file.getCanonicalPath().substring(file.getAbsolutePath().indexOf(carpeta));
                rutaCarpetaActual = file.getAbsolutePath();
                copy(idTransfe);
            } else if (file.isDirectory()) {
                enviarDirectorio(file, idTransfe);
            }
        }
    }

    private long copy(String idTransfe) throws IOException, InterruptedException {
        File archivo = new File(rutaCarpetaActual);
        long totalFileSize = archivo.length();
        long bytesSentInFile = 0;

        // A. Metadatos del archivo individual (JSON)
        ProtocolService.writeFormattedPayload(out, new FileDirectoryCommunication(archivo.getName(), totalFileSize));
        // B. Ruta relativa para reconstrucción (UTF)
        out.writeUTF(rutaCopia);
        out.flush();

        // C. Contenido binario
        try (FileInputStream fis = new FileInputStream(archivo)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while (bytesSentInFile < totalFileSize) {
                synchronized (pauseLock) {
                    if (paused) {
                        pauseLock.wait();
                        continue;
                    }
                }

                bytesRead = fis.read(buffer);
                if (bytesRead == -1) break;

                out.write(buffer, 0, bytesRead);
                bytesSentInFile += bytesRead;
                totalBytesLeidos += bytesRead;

                transferenciaController.updateProgressMetrics(
                        FileTransferState.SENDING, idTransfe, totalBytesLeidos, tamanoTotal
                );
            }
        }
        out.flush();
        return bytesSentInFile;
    }

    public void receiveDirectory(String SERVER_ADDRESS, String port, FileHandshakeCommunication handshakeCommunication) {
        String sessionId = handshakeCommunication.getSessionId();
        var folderInfo = handshakeCommunication.getFileInfo();
        long totalBytesCarpeta = folderInfo.getSize();

        try (Socket socket = new Socket(SERVER_ADDRESS, Integer.parseInt(port))) {
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.flush();

            if (transferenciaController.notifyTranference(handshakeCommunication)) {
                // Identificación (JSON)
                ProtocolService.writeFormattedPayload(out, new Mensaje(sessionId));
                ProtocolService.writeFormattedPayload(out, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));
                out.flush();

                // Esperar START_TRANSFER (JSON)
                Communication startConfirm = waitForHandshake(entrada);
                if (!(startConfirm instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER)) return;

                String idTransfe = transferenciaController.addTransference(
                        FileTransferState.RECEIVING.name(), folderInfo.getRecipient(), folderInfo.getRecipient(), folderInfo.getName(), this,
                        totalBytesCarpeta
                );

                long bytesRecibidosAcumulados = 0;
                boolean transferenciaActiva = true;

                while (transferenciaActiva) {
                    // 1. Leer Metadatos (JSON)
                    Communication comm = ProtocolService.readFormattedPayload(entrada);

                    if (comm instanceof FileDirectoryCommunication archivoMeta) {
                        // 2. Leer ruta relativa (UTF)
                        String nombreRelativo = entrada.readUTF();
                        String rutaCompleta = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR) + File.separator + nombreRelativo;

                        if (archivoMeta.isDirectory()) {
                            new File(rutaCompleta).mkdirs();
                            continue;
                        }

                        crearDirectorios(rutaCompleta);

                        // 3. Recibir Bytes (Binario crudo)
                        try (FileOutputStream fos = new FileOutputStream(rutaCompleta)) {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            long fileSize = archivoMeta.getSize();
                            long readInCurrentFile = 0;

                            while (readInCurrentFile < fileSize) {
                                int toRead = (int) Math.min(buffer.length, fileSize - readInCurrentFile);
                                int bytesRead = entrada.read(buffer, 0, toRead);
                                if (bytesRead == -1) break;

                                fos.write(buffer, 0, bytesRead);
                                readInCurrentFile += bytesRead;
                                bytesRecibidosAcumulados += bytesRead;

                                transferenciaController.updateProgressMetrics(
                                        FileTransferState.RECEIVING, idTransfe, bytesRecibidosAcumulados, totalBytesCarpeta);
                            }
                            fos.flush();
                        }
                    } else if (comm instanceof FileHandshakeCommunication h && h.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                        transferenciaActiva = false;
                    }
                }
            }else {
                ProtocolService.writeFormattedPayload(out, new Mensaje(sessionId));
                ProtocolService.writeFormattedPayload(out, new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
                out.flush();

            }
        } catch (Exception e) {
            Logger.logError("Error en receiveDirectory: " + e.getMessage());
        }
    }

    public void crearDirectorios(String archivo) {
        Path path = Paths.get(archivo);
        Path directorioPadre = path.getParent();
        if (directorioPadre != null && !Files.exists(directorioPadre)) {
            try {
                Files.createDirectories(directorioPadre);
            } catch (IOException e) {
                Logger.logError("No se pudo crear directorio: " + directorioPadre);
            }
        }
    }

    private void archivosTotales(File archivo, AtomicInteger totalArchivos, AtomicLong tamanoTotal) throws IOException {
        File[] files = archivo.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    totalArchivos.incrementAndGet();
                    tamanoTotal.addAndGet(file.length());
                } else if (file.isDirectory()) {
                    archivosTotales(file, totalArchivos, tamanoTotal);
                }
            }
        }
    }

    public void stop() { running = false; resume(); }
    public void pause() { paused = true; }
    public void resume() { synchronized (pauseLock) { paused = false; pauseLock.notifyAll(); } }
    @Override public void cancel() { stop(); }
}
package org.bitBridge.Client;



import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.shared.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class DirectoryTransferManager implements TransferManager {
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();
    private String rutaCopia;
    private String carpeta;
    private String rutaCarpetaActual;
    private ObjectOutputStream out;
    private ObjectInputStream entrada;
    private ConfiguracionCliente configCliente;
    private String nick;
    private int totalArchivos;
    private int archivosEnviados;
    private TransferenciaController transferenciaController;
    private String recipient;
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB

    public DirectoryTransferManager(TransferenciaController transferenciaController) {
        this.configCliente = new ConfiguracionCliente();
        rutaCopia = configCliente.obtener("cliente.directorio_descargas");
        rutaCarpetaActual = rutaCopia;
        this.transferenciaController = transferenciaController;
    }

    private void logMemoryUsage(String phase) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        /*
        Logger.logInfo(phase + " - Memoria total: " + totalMemory / (1024 * 1024) + " MB");
        Logger.logInfo(phase + " - Memoria libre: " + freeMemory / (1024 * 1024) + " MB");
        Logger.logInfo(phase + " - Memoria usada: " + usedMemory / (1024 * 1024) + " MB");
        */
    }

    public void sendDirectory(File archivo, String SERVER_ADDRESS, int port, String recipient) {
        this.carpeta = archivo.getName();
        this.nick = SERVER_ADDRESS;
        this.recipient = recipient;
        archivosEnviados = 0;

        AtomicInteger totalArchivosContador = new AtomicInteger(0);
        try {
            archivosTotales(archivo, totalArchivosContador);
        } catch (IOException e) {
            Logger.logError("Error al contar archivos: " + e.getMessage());
            return;
        }
        this.totalArchivos = totalArchivosContador.get();

        logMemoryUsage("Inicio de sendDirectory");

        try (Socket socket = new Socket(SERVER_ADDRESS, port)) {
            out = new ObjectOutputStream(socket.getOutputStream());
            entrada = new ObjectInputStream(socket.getInputStream());

            out.writeObject(new Mensaje("enviando", CommunicationType.MESSAGE));
            out.flush();

            out.writeObject(new FileDirectoryCommunication(archivo.getName(), totalArchivos, recipient));
            out.flush();

            String idTransfe = transferenciaController.addTransference(FileTransferState.SENDING.name(), nick, nick, archivo.getName(), this);

            logMemoryUsage("Después de configuración inicial");

            Object object;
            while ((object = entrada.readObject()) != null) {
                if (object instanceof FileHandshakeCommunication respuesta &&
                        respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                    break;
                } else if (object instanceof Mensaje mensaje) {
                    Logger.logInfo("Mensaje recibido: " + mensaje.getContenido());
                }
            }

            logMemoryUsage("Antes de enviar directorio");

            enviarDirectorio(archivo, idTransfe);

            out.writeObject(new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));
            out.flush();

            logMemoryUsage("Final de sendDirectory");
        } catch (Exception e) {
            Logger.logError("Error en sendDirectory: " + e.getMessage());
        } finally {
            out = null;
            entrada = null;
        }
    }

    public void enviarDirectorio(File archivo, String idTransfe) throws IOException, InterruptedException {
        File[] files = archivo.listFiles();
        if (files == null) return;

        if (files.length == 0) {
            rutaCopia = archivo.getCanonicalPath().substring(archivo.getAbsolutePath().indexOf(carpeta));
            rutaCarpetaActual = archivo.getAbsolutePath();

            out.writeObject(new FileDirectoryCommunication(archivo.getName(), 0, recipient));
            out.flush();
            out.writeUTF(rutaCopia);
            out.flush();
        }

        for (File file : files) {
            if (file.isFile()) {
                rutaCopia = file.getCanonicalPath().substring(file.getAbsolutePath().indexOf(carpeta));
                rutaCarpetaActual = file.getAbsolutePath();

                logMemoryUsage("Antes de copiar: " + file.getName());
                copy();
                archivosEnviados++;
                transferenciaController.updateProgress(FileTransferState.SENDING, idTransfe, (int) ((archivosEnviados * 100L) / totalArchivos));
                logMemoryUsage("Después de copiar: " + file.getName());
            } else if (file.isDirectory()) {
                enviarDirectorio(file, idTransfe);
            }
        }
    }

    public void reciveDirectory(String SERVER_ADDRESS, String port, FileHandshakeCommunication handshakeCommunication) throws IOException, ClassNotFoundException {
        try (
                Socket socket = new Socket(SERVER_ADDRESS, Integer.parseInt(port));
                ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())
        ) {
            salida.writeObject(new Mensaje(handshakeCommunication.getSessionId(), CommunicationType.MESSAGE));
            salida.flush();

            var communication = handshakeCommunication.getFileInfo();
            carpeta = communication.getName();
            totalArchivos = communication.getTotalArchivos();
            Logger.logInfo(carpeta);
            Object object;
            while ((object = entrada.readObject()) != null) {
                if (object instanceof FileHandshakeCommunication respuesta &&
                        respuesta.getAction() == FileHandshakeAction.START_TRANSFER &&
                        respuesta.getSessionId().equals(handshakeCommunication.getSessionId())) {
                    break;
                } else if (object instanceof Mensaje mensaje) {
                    Logger.logInfo("Mensaje recibido: " + mensaje.getContenido());
                }
            }

            rutaCopia += carpeta;
            File file = new File(carpeta);
            file.mkdir();

            String recipientNick = communication.getRecipient();
            String idTransfe = transferenciaController.addTransference(FileTransferState.RECEIVING.name(), recipientNick, recipientNick, carpeta, this);

            logMemoryUsage("Después de crear directorios");

            boolean transferenciaActiva = true;
            while (transferenciaActiva && (object = entrada.readObject()) != null) {
                if (object instanceof FileDirectoryCommunication archivo) {
                    String nombreArchivo = entrada.readUTF();
                    String rutaArchivo = rutaCarpetaActual + nombreArchivo;

                    if (archivo.isDirectory() && archivo.getSize() == 0) {
                        new File(rutaArchivo).mkdir();
                        archivosEnviados++;
                        transferenciaController.updateProgress(FileTransferState.RECEIVING, idTransfe, (int) ((archivosEnviados * 100L) / totalArchivos));
                        continue;
                    }

                    crearDirectorios(rutaArchivo);
                    String rutaDescargas = configCliente.obtener("cliente.directorio_descargas");

                    try (FileOutputStream fos = new FileOutputStream(rutaDescargas + nombreArchivo)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long totalBytesRead = 0;
                        long fileSize = archivo.getSize();

                        logMemoryUsage("Antes de recibir archivo: " + nombreArchivo);

                        while (totalBytesRead < fileSize && (bytesRead = entrada.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                        }

                        archivosEnviados++;
                        transferenciaController.updateProgress(FileTransferState.RECEIVING, idTransfe, (int) ((archivosEnviados * 100L) / totalArchivos));

                        logMemoryUsage("Después de recibir archivo: " + nombreArchivo);
                    }
                } else if (object instanceof FileHandshakeCommunication fin &&
                        fin.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                    transferenciaActiva = false;
                    Logger.logInfo("Transferencia finalizada.");
                }
            }
        } finally {
            entrada = null;
            out = null;
        }
    }

    public void crearDirectorios(String archivo) {
        try {
            Path path = Paths.get(archivo);
            Path directorioPadre = path.getParent();
            if (directorioPadre != null && !Files.exists(directorioPadre)) {
                Files.createDirectories(directorioPadre);
            }
        } catch (IOException e) {
            Logger.logError("Error al crear directorios: " + e.getMessage());
        }
    }

    private void copy() throws IOException, InterruptedException {
        String sourcePath = rutaCarpetaActual;
        File archivo = new File(sourcePath);
        long totalFileSize = archivo.length();
        long totalBytesReaded = 0;

        out.writeObject(new FileDirectoryCommunication(archivo.getName(), archivo.length()));
        out.flush();
        out.writeUTF(rutaCopia);
        out.flush();

        try (FileInputStream fileInputStream = new FileInputStream(sourcePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while (totalBytesReaded < totalFileSize) {
                synchronized (pauseLock) {
                    if (paused) {
                        pauseLock.wait();
                    } else {
                        bytesRead = fileInputStream.read(buffer);
                        if (bytesRead == -1) break;
                        out.write(buffer, 0, bytesRead);
                        out.flush();
                        totalBytesReaded += bytesRead;
                    }
                }
            }
        }
    }

    private void archivosTotales(File archivo, AtomicInteger totalArchivos) throws IOException {
        File[] files = archivo.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    totalArchivos.incrementAndGet();
                } else if (file.isDirectory()) {
                    archivosTotales(file, totalArchivos);
                }
            }
        }
    }

    public void stop() {
        running = false;
        resume();
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    @Override
    public void cancel() {

    }
}
package org.bitBridge.Client;



import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.shared.*;

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
    private ObjectOutputStream out;
    private ObjectInputStream entrada;
    private ConfiguracionCliente configCliente;
    private String nick;
    private int totalArchivos;
    private long tamanoTotal;
    private long totalBytesLeidos=0;
    private int archivosEnviados;
    private TransferenciaController transferenciaController;
    private String recipient;
    private static final int BUFFER_SIZE = 128 * 1024; // 64KB

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
        String sessionId = "SENDER_" + new Random().nextInt(10000);

        AtomicInteger totalArchivosContador = new AtomicInteger(0);
        AtomicLong totalTam = new AtomicLong(0);

        try {
            archivosTotales(archivo, totalArchivosContador,totalTam);

        } catch (IOException e) {
            Logger.logError("Error al contar archivos: " + e.getMessage());
            return;
        }
        this.totalArchivos = totalArchivosContador.get();
        tamanoTotal=totalTam.get();

        Logger.logInfo(String.valueOf(tamanoTotal));


        logMemoryUsage("Inicio de sendDirectory");

        try (Socket socket = new Socket(SERVER_ADDRESS, port)) {
            out = new ObjectOutputStream(socket.getOutputStream());
            entrada = new ObjectInputStream(socket.getInputStream());

            //out.writeObject(new Mensaje("enviando", CommunicationType.MESSAGE));
            out.writeObject(new Mensaje(sessionId, CommunicationType.MESSAGE));
            out.flush();

            out.writeObject(new FileDirectoryCommunication(archivo.getName(), totalArchivos, recipient,tamanoTotal));
            out.flush();

            //String idTransfe = transferenciaController.addTransference(FileTransferState.SENDING.name(), nick, nick, archivo.getName(), this);

            //logMemoryUsage("Después de configuración inicial");
            Object respuesta = waitForHandshake(entrada);

            if (respuesta instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER) {
                String idTransfe = transferenciaController.addTransference(FileTransferState.SENDING.name(), nick, nick, archivo.getName(), this);

                enviarDirectorio(archivo, idTransfe);

                out.writeObject(new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));
                out.flush();

            } else if (respuesta instanceof FileHandshakeCommunication f) {
                transferenciaController.notifyTranference(f.getAction());
            }


            /*Object object;
            while ((object = entrada.readObject()) != null) {
                if (object instanceof FileHandshakeCommunication respuesta &&
                        respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                    break;
                } else if (object instanceof Mensaje mensaje) {
                    Logger.logInfo("Mensaje recibido: " + mensaje.getContenido());
                }
            }*/

            logMemoryUsage("Antes de enviar directorio");

            TimeUnit.MILLISECONDS.sleep(200);

            logMemoryUsage("Final de sendDirectory");
        } catch (Exception e) {
            Logger.logError("Error en sendDirectory: " + e.getMessage());
        } finally {
            out = null;
            entrada = null;
        }
    }

    private Object waitForHandshake(ObjectInputStream entrada) throws Exception {
        while (true) {
            Object obj = entrada.readObject();
            if (obj instanceof Mensaje m) {
                Logger.logInfo("Notificación: " + m.getContenido());
                continue; // Sigue esperando el objeto de comunicación real
            }
            if (obj == null) throw new IOException("Flujo nulo");
            return obj;
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
               long sumaArchivo= copy(idTransfe);
                archivosEnviados++;
                //transferenciaController.updateProgress(FileTransferState.SENDING, idTransfe, (int) ((archivosEnviados * 100L) / totalArchivos));


                logMemoryUsage("Después de copiar: " + file.getName());
            } else if (file.isDirectory()) {
                enviarDirectorio(file, idTransfe);
            }
        }
    }
    public void reciveDirectory(String SERVER_ADDRESS, String port, FileHandshakeCommunication handshakeCommunication) throws IOException, ClassNotFoundException {
        String sessionId = handshakeCommunication.getSessionId();

        // Usamos try-with-resources para asegurar el cierre de sockets y streams
        try (Socket socket = new Socket(SERVER_ADDRESS, Integer.parseInt(port));
             ObjectOutputStream salida = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            salida.flush();
            ObjectInputStream entrada = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

            if (transferenciaController.notifyTranference(handshakeCommunication)) {
                // Handshake inicial
                salida.writeObject(new Mensaje(sessionId, CommunicationType.MESSAGE));
                salida.writeObject(new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));
                salida.flush();

                var communication = handshakeCommunication.getFileInfo();
                String carpeta = communication.getName();
                long totalBytesCarpeta = communication.getSize(); // <--- IMPORTANTE: Tamaño total de la carpeta
                String recipientNick = communication.getRecipient();

                // Esperar START_TRANSFER
                Object object;
                while ((object = entrada.readObject()) != null) {
                    if (object instanceof FileHandshakeCommunication f &&
                            f.getAction() == FileHandshakeAction.START_TRANSFER) break;
                }

                // Crear directorio raíz y registrar transferencia
                new File(carpeta).mkdir();
                String idTransfe = transferenciaController.addTransference(
                        FileTransferState.RECEIVING.name(), recipientNick, recipientNick, carpeta, this
                );

                long bytesRecibidosAcumulados = 0; // <--- Métrica para velocidad y ETA global
                boolean transferenciaActiva = true;

                while (transferenciaActiva && (object = entrada.readObject()) != null) {
                    if (object instanceof FileDirectoryCommunication archivo) {
                        String nombreArchivo = entrada.readUTF();
                        String rutaDescargas = configCliente.obtener("cliente.directorio_descargas");
                        String rutaCompleta = rutaDescargas + nombreArchivo;

                        if (archivo.isDirectory()) {
                            new File(rutaCompleta).mkdirs();
                            continue;
                        }

                        crearDirectorios(rutaCompleta);

                        try (FileOutputStream fos = new FileOutputStream(rutaCompleta)) {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int bytesRead;
                            long fileSize = archivo.getSize();
                            long readInCurrentFile = 0;

                            while (readInCurrentFile < fileSize && (bytesRead = entrada.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);

                                readInCurrentFile += bytesRead;
                                bytesRecibidosAcumulados += bytesRead; // Acumulamos el total de la carpeta

                                // Actualizamos con el acumulado global y el tamaño total de la carpeta
                                transferenciaController.updateProgressMetrics(
                                        FileTransferState.RECEIVING,
                                        idTransfe,
                                        bytesRecibidosAcumulados,
                                        totalBytesCarpeta
                                );
                            }
                            fos.flush();
                        }
                    } else if (object instanceof FileHandshakeCommunication fin &&
                            fin.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                        transferenciaActiva = false;
                    }
                }
                Logger.logInfo("Transferencia de carpeta finalizada.");

            } else {
                salida.writeObject(new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
                salida.flush();
            }
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

    private long copy(String idTransfe) throws IOException, InterruptedException {
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
                        totalBytesLeidos+= bytesRead;

                        transferenciaController.updateProgressMetrics(
                                FileTransferState.SENDING,
                                idTransfe,
                                totalBytesLeidos,
                                tamanoTotal
                        );
                    }
                }
            }
        }
        return totalBytesReaded;
    }

    private void archivosTotales(File archivo, AtomicInteger totalArchivos,AtomicLong tamanoTotal) throws IOException {
        File[] files = archivo.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    totalArchivos.incrementAndGet();
                    tamanoTotal.addAndGet(file.length());
                } else if (file.isDirectory()) {
                    archivosTotales(file, totalArchivos,tamanoTotal);
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
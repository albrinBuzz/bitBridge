package org.bitBridge.Client;



import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.shared.*;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class FileTransferManager implements TransferManager{
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();
    private ConfiguracionCliente configCliente;
    private TransferenciaController transferenciaController;

    public FileTransferManager(TransferenciaController transferenciaController) {
        this.configCliente=new ConfiguracionCliente();
        this.transferenciaController=transferenciaController;

    }

    public void sendFile(FileDirectoryCommunication com, File file, String SERVER_ADDRESS, int port) {
        // Generamos un ID de sesión técnico para que el servidor sepa que es transferencia de archivos
        String sessionId = "SENDER_" + new Random().nextInt(10000);

        try (Socket socket = new Socket(SERVER_ADDRESS, port);
             ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream())) {

            salida.flush();
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            // 1. Identificación técnica inicial (Muy importante para el Servidor)
            salida.writeObject(new Mensaje(sessionId, CommunicationType.MESSAGE));
            salida.flush();

            // 2. Registrar en la UI
            String idTransfe = transferenciaController.addTransference(
                    FileTransferState.SENDING.name(), com.getRecipient(), com.getRecipient(),
                    file.getName(), this
            );

            // 3. Enviar metadatos del archivo (el objeto 'com')

            salida.writeObject(com);
            salida.flush();
            //Logger.logInfo("Información del archivo enviada al servidor.");

            // 3. Esperar Handshake
            if (!waitForHandshake(entrada)) {
                Logger.logError("No se recibió confirmación del servidor para iniciar.");
                return;
            }

            // 4. Transferencia de datos
            transferData(file, salida, idTransfe);

            /*FileHandshakeCommunication response = new FileHandshakeCommunication(
                    FileHandshakeAction.TRANSFER_DONE
            );
            salida.writeObject(response);

            salida.flush();*/
            TimeUnit.MILLISECONDS.sleep(350);


        } catch (IOException e) {
            Logger.logError("Error al enviar el archivo: " + e.getMessage());
        } catch (InterruptedException e) {
            Logger.logError("Error de interrupción en el proceso de transferencia: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }




    public   void receiveFiles(String SERVER_ADDRESS,String port,FileHandshakeCommunication handshakeCommunication) {
        Logger.logInfo("recibiendo archivo");
        try {

            var communication=handshakeCommunication.getFileInfo();
            String recipientNick = communication.getRecipient();
            String fileName = communication.getName();
            long fileSize = communication.getSize();

            var idTrans= transferenciaController.addTransference(FileTransferState.RECEIVING.name(), recipientNick, recipientNick,fileName,this);


            Socket socket=new Socket(SERVER_ADDRESS, Integer.parseInt(port));

            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());

            ObjectInputStream entrada=new ObjectInputStream(socket.getInputStream());


            salida.writeObject(new Mensaje(handshakeCommunication.getSessionId(), CommunicationType.MESSAGE));
            salida.flush();



            String rutaDescargas = configCliente.obtener("cliente.directorio_descargas");


            Object object;
            while (true) {
                try {
                    object = entrada.readObject();

                    if (object instanceof FileHandshakeCommunication respuesta) {

                        // Validamos que sea la respuesta esperada (ej. inicio de transferencia)
                        if (respuesta.getAction() == FileHandshakeAction.START_TRANSFER &&
                                respuesta.getSessionId().equals(handshakeCommunication.getSessionId())) {

                            break; // Salir del bucle, ya tienes la respuesta esperada
                        }

                    } else if (object instanceof Mensaje mensaje) {
                        //Logger.logInfo("Mensaje recibido: " + mensaje.getContenido());
                        // Puedes seguir esperando o tomar otra acción
                    }

                } catch (ClassNotFoundException | IOException e) {
                    //Logger.logError("Error leyendo objeto del servidor: " + e.getMessage());
                    break;
                }
            }
            Logger.logInfo("Comenzando la descarga");

            try (FileOutputStream fileOutputStream = new FileOutputStream(rutaDescargas+fileName)) {
                byte[] buffer = new byte[65536];

                int bytesRead;
                long totalBytesRead = 0;

                while (totalBytesRead < fileSize) {
                    bytesRead = entrada.read(buffer);
                    if (bytesRead == -1) break;
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    //transferenciaController.updateProgress(FileTransferState.RECEIVING,idTrans,(int)((totalBytesRead * 100) / fileSize));
                    transferenciaController.updateProgressMetrics(
                            FileTransferState.RECEIVING,
                            idTrans,
                            totalBytesRead,
                            fileSize
                    );
                }


            }
            Logger.logInfo(rutaDescargas+fileName);

            /*Object fin = entrada.readObject(); // Leer TRANSFER_DONE
            if (fin instanceof  FileHandshakeCommunication com){
                if (com.getAction()==FileHandshakeAction.TRANSFER_DONE){
                    Logger.logInfo("Tranferencia finalizada, cerrando recursos");
                }
            }*/

            TimeUnit.MILLISECONDS.sleep(250);
            entrada.close();
            socket.close();
        } catch (IOException e) {
            System.err.println("Error durante la recepción de archivos: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void transferData(File file, ObjectOutputStream salida, String idTrans) throws IOException, InterruptedException {
        byte[] buffer = new byte[65536];
        long length = file.length();
        long totalSent = 0;

        try (FileInputStream fis = new FileInputStream(file)) {
            while (totalSent < length && running) {
                checkPaused();

                int toRead = (int) Math.min(buffer.length, length - totalSent);
                int bytesRead = fis.read(buffer, 0, toRead);
                if (bytesRead == -1) break;

                salida.write(buffer, 0, bytesRead);
                totalSent += bytesRead;

                int progress = (int) ((totalSent * 100) / length);
                //transferenciaController.updateProgress(FileTransferState.SENDING, idTrans, progress);
                transferenciaController.updateProgressMetrics(
                        FileTransferState.SENDING,
                        idTrans,
                        totalSent,
                        length
                );
            }
            salida.flush();
        }
    }

    private boolean waitForHandshake(ObjectInputStream entrada) throws Exception {
        while (true) {
            Object obj = entrada.readObject();
            if (obj instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER) {
                return true;
            } else if (obj instanceof Mensaje m) {
                Logger.logInfo("Notificación servidor: " + m.getContenido());
            }
        }
    }

    private void checkPaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused) {
                pauseLock.wait();
            }
        }
    }

    public void stop() {
        running = false;

        resume();

    }

    public void pause() {
        // you may want to throw an IllegalStateException if !running
        paused = true;

    }

    public void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll(); // Unblocks thread

        }
    }

    @Override
    public void cancel() {

    }
}
package org.bitBridge.server.client;




import org.bitBridge.server.ConfiguracionServidor;
import org.bitBridge.server.core.Server;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.server.transfer.FileTransferService;
import org.bitBridge.shared.*;

import java.io.*;
import java.net.Socket;

import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.TimeUnit;


public class ClientHandler implements Runnable {

    public final Socket clientSocket;
    public String nick;
    private ObjectInputStream entrada;
    private ObjectOutputStream salida;
    private String ip;
    private Server server;
    private ConfiguracionServidor config = ConfiguracionServidor.getInstancia();
    private final ServerContext context;
    FileTransferService transferService;

    // Crear el logger JDK
    //private static final // LOGGER = //.getLogger(ClientHandler.class.getName());


    public ClientHandler(Socket socket, Server server, ServerContext context) {
        this.clientSocket = socket;
        this.ip = socket.getInetAddress().toString();
        this.server = server;
        this.context = context;
        transferService=new FileTransferService(context);
    }

    public String getNick() {
        return nick;
    }


    public void run() {
        try {
            Logger.logInfo("Cliente conectado: " + clientSocket.getRemoteSocketAddress());

            // Inicialización de streams (Mantener el orden Out -> In es correcto)
            salida = new ObjectOutputStream(clientSocket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(clientSocket.getInputStream());

            // 1. Leer identificación inicial
            Object inicial = entrada.readObject();
            if (!(inicial instanceof Mensaje mensajeInicial)) return;

            String contenidoInicial = mensajeInicial.getContenido();
            if (isSessionId(contenidoInicial)){
                this.nick = contenidoInicial;
                server.registerClient(this, 8080); // Registrar para que otros hilos lo encuentren

                Object incoming= entrada.readObject();

                if (incoming instanceof FileHandshakeCommunication handshake) {
                    Logger.logInfo(handshake.getAction().name());
                   context.transferManager().registerHandshake(handshake.getSessionId(), handshake);

                } else if (incoming instanceof FileDirectoryCommunication communication) {

                    // Delegamos el control al método de transferencia y FINALIZAMOS el run

                    if (this.nick.startsWith("SENDER_")) {
                        Logger.logDebug("Sesión técnica [" + nick + "] detectada. Iniciando puente de datos.");
                        if (communication.getType().equals(CommunicationType.FILE)) {
                            transferService.handleForwardFile(this, this.entrada,communication);
                        } else if (communication.getType().equals(CommunicationType.DIRECTORY)) {

                            transferService.relayDirectory(communication, this, this.entrada);
                        }

                        //return;
                        //this.forwardFile(); // Método que explicamos antes para leer bytes
                    }
                }


                 // IMPORTANTE: Cerramos este hilo de control de chat
                //manegerCon();
                //return;

            }else {
                // 3. LÓGICA DE CLIENTE DE CHAT NORMAL
                nick = server.getUniqueNick(contenidoInicial);
                sendComunicacion(new Mensaje("Conectado al servidor como: " + nick, CommunicationType.MESSAGE));
                server.registerClient(this, 8080);

                server.updateClient(nick);
                server.broadcastMessage("[ " + nick + "] Se ha unido al Chat", this);

            }
            manegerCon();

        } catch (StreamCorruptedException e) {
            Logger.logError("Error de sincronización (Stream Corrupted) en [" + nick + "]: " + e.getMessage());
        } catch (Exception e) {
            Logger.logError("Error general en ClientHandler [" + nick + "]: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutDown();
        }
    }
    public void manegerCon(){
        while (!clientSocket.isClosed()) {
            try {
                Object incoming = entrada.readObject();
                if (incoming instanceof Communication communication) {
                    server.totalMessagesReceived.getAndIncrement();
                    handleComunication(communication);
                }
            } catch (EOFException | SocketException e) {
                Logger.logInfo("Cliente [" + nick + "] desconectado.");
                break;
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException(e);
            } finally {
                shutDown();
            }
        }
    }

    private boolean isSessionId(String nick) {
        return nick != null && (nick.startsWith("SENDER_") ||
                nick.startsWith("FILE_") ||
                nick.startsWith("DIR_"));
    }

    private void handleComunication(Communication communication) throws IOException {
        //Logger.logInfo("EN LA COMUNICACION");
        if (communication.getType().equals(CommunicationType.MESSAGE)) {
            Mensaje mensaje=(Mensaje)communication;
            server.broadcastMessage("[" + nick + "] => " + mensaje.getContenido(), this);
            server.addMessageHistory("[" + nick + "] => " + mensaje.getContenido());
        } else if (communication.getType().equals(CommunicationType.FILE)) {
            FileDirectoryCommunication com=(FileDirectoryCommunication)communication;
            //sendFileToClient(com);
        }
        else if (communication.getType().equals(CommunicationType.DIRECTORY)) {
            //Mensaje mensaje=(Mensaje)communication;
            FileDirectoryCommunication com=(FileDirectoryCommunication)communication;
            //sendDirectoryToClient(com);
            transferService.relayDirectory(com, this, this.entrada);
        }
        else if (communication.getType().equals(CommunicationType.DISCONNECT)) {
            shutDown();
        }

    }



    public void shutDown() {

        //.logInfo("shutDown");

        try {
            if (!isSessionId(nick)){
                server.broadcastMessage(nick + " Se a desconectado", this);
            }
            context.registry().removeClient(this);

            //Server.clients.remove(clientSocket);
            //server.getClientPool().remove(this);
            server.updateClient();
            System.out.printf("[%s] has left the chat.%n", nick);
            clientSocket.close();

            if (entrada!=null && salida!=null){
                entrada.close();
                salida.close();
            }



        } catch (IOException e) {
            Logger.logInfo("Error al cerrar conexión con el cliente: "+e.getMessage());
            //LOGGER.error("Error al cerrar conexión con el cliente: {}",e.getMessage());
        }
    }

    public void sendComunicacion(Communication communication){
        try {

            salida.writeObject(communication);
            salida.flush();

        } catch (IOException e) {
            //LOGGER.error("Error al enviar con el cliente: {}",e.getMessage());
            //.logInfo("Error al enviar con el cliente: "+e.getMessage());
        }

    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public  ObjectOutputStream getOutputStream() { return this.salida; }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ClientHandler{");
        sb.append("ip='").append(ip).append('\'');
        sb.append(", nick='").append(nick).append('\'');
        sb.append('}');
        return sb.toString();
    }


}
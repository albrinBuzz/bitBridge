package org.bitBridge.server.core.client;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.*;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable,BitBridgeClient {
    private final Socket clientSocket;
    private final ServerContext context;
    private DataInputStream entrada;
    private DataOutputStream salida;

    public String nick;
    private final Map<CommunicationType, ActionHandler> actionHandlers = new HashMap<>();

    public ClientHandler(Socket socket, ServerContext context) {
        this.clientSocket = socket;
        this.context = context;
        /*try {
            clientSocket.setSoTimeout(5000);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }*/

    }



    @Override
    public void run() {
        try {
            setupConnection();
            authenticate(); // Fase 1: ¿Quién es el cliente?
            processLoop();  // Fase 2: Ciclo de vida infinito
        } catch (Exception e) {
            Logger.logError("Error en [" + nick + "]: " + e.getMessage());
        } finally {
            shutDown();
        }
    }

    private void setupConnection() throws IOException {
        // Cambiamos los streams a DataStream
        this.salida = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
        this.salida.flush();
        this.entrada = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
    }

    private void authenticate() throws Exception {

        Communication comm = ProtocolService.readFormattedPayload(entrada);
        if (!(comm instanceof Mensaje mensaje)) throw new IOException("Protocolo inválido");

        String contenido = mensaje.getContenido();
        if (isSessionId(contenido)) {
            this.nick = contenido;
            handleTechnicalSession(); // Lógica para SENDER_, FILE_, etc.
        } else {
            this.nick = context.getServer().getUniqueNick(contenido);
            context.getServer().registerClient(this, 8080);
            sendComunicacion(new Mensaje("Conectado como: " + nick, CommunicationType.MESSAGE));
            context.getServer().broadcastMessage("[ " + nick + "] Se ha unido al Chat", this);
        }
    }


    private void processLoop() throws Exception {
        while (!clientSocket.isClosed()) {
            try {
                // Delegamos la lectura al ProtocolService
                Communication comm = ProtocolService.readFormattedPayload(entrada);

                if (comm != null) {
                    context.getDispatcher().dispatch(this, comm, context);
                }
            } catch (EOFException e) {
                break; // Conexión cerrada limpiamente
            }
        }
    }

    private void handleTechnicalSession() throws Exception {
        context.getServer().registerClient(this, 8080);
        //Object next = entrada.readObject();
        Communication next = ProtocolService.readFormattedPayload(entrada);

        if (next instanceof FileHandshakeCommunication handshake) {
            context.transferManager().registerHandshake(handshake.getSessionId(), handshake);
        } else if (next instanceof FileDirectoryCommunication comm && nick.startsWith("SENDER_")) {
            // Delegar al servicio experto y seguir escuchando o finalizar según necesidad
            //actionHandlers.get(comm.getType()).handle(comm, this, context);

            context.getDispatcher().dispatch(this, comm, context);
            this.close();
        }
    }



    private boolean isSessionId(String nick) {
        return nick != null && (nick.startsWith("SENDER_") || nick.startsWith("FILE_") || nick.startsWith("DIR_"));
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public String getNick() {
        return nick;
    }




    /*public synchronized void sendComunicacion(Object msg) {
        try {
            salida.writeObject(msg);
            salida.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }*/

    public synchronized void sendComunicacion(Communication comm) {
        try {
            ProtocolService.writeFormattedPayload(salida, comm);
        } catch (IOException e) {
            Logger.logError("Error enviando: " + e.getMessage());
        }
    }

    public void shutDown() {


        try {
            if (!isSessionId(nick)){
                context.getServer().broadcastMessage(nick + " Se a desconectado", this);
            }
            context.registry().removeClient(this);

            context.getServer().updateClient();
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

    @Override
    public String getRemoteAddress() {
        return "";
    }

    @Override
    public ClientInfo getInfo() {
        return null;
    }

    @Override
    public void setInfo(ClientInfo info) {

    }

    @Override
    public SocketChannel getSocketChannel() {
        return null;
    }

    @Override
    public ReadableByteChannel getReadableChannel() {
        return Channels.newChannel(entrada);
    }

    @Override
    public WritableByteChannel getWritableChannel() {
        return Channels.newChannel(salida);
    }

    private void close() {
        context.registry().removeClient(this);
        try { clientSocket.close(); } catch (IOException ignored) {}
    }

    public DataInputStream objectInputStream() {
        return entrada;
    }

    public DataOutputStream getOutputStream() {
        return salida;
    }
}
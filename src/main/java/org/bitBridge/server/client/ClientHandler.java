package org.bitBridge.server.client;

import org.bitBridge.server.core.ServerContext;
import org.bitBridge.server.transfer.FileTransferService;
import org.bitBridge.shared.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ServerContext context;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    public String nick;
    private final Map<CommunicationType, ActionHandler> actionHandlers = new HashMap<>();

    public ClientHandler(Socket socket, ServerContext context) {
        this.clientSocket = socket;
        this.context = context;
        try {
            clientSocket.setSoTimeout(5000);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        initializeHandlers();
    }

    private void initializeHandlers() {
        // Registro de servicios: Fácil de extender a futuro
        actionHandlers.put(CommunicationType.MESSAGE, (comm, client, ctx) -> {
            Mensaje m = (Mensaje) comm;
            context.server().broadcastMessage("[" + nick + "] => " + m.getContenido(), client);
            context.server().addMessageHistory("[" + nick + "] => " + m.getContenido());
        });

        actionHandlers.put(CommunicationType.DIRECTORY, (comm, client, ctx) -> {
            new FileTransferService(ctx).relayDirectory((FileDirectoryCommunication) comm, client, entrada);
        });

        actionHandlers.put(CommunicationType.FILE, (comm, client, ctx) -> {
            new FileTransferService(ctx).handleForwardFile(client,client.objectInputStream(), (FileDirectoryCommunication) comm);
        });


        // Aquí podrías agregar:
        // actionHandlers.put(CommunicationType.AUDIO_STREAM, new AudioHandler());
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
        salida = new ObjectOutputStream(clientSocket.getOutputStream());
        salida.flush();
        entrada = new ObjectInputStream(clientSocket.getInputStream());
    }

    private void authenticate() throws Exception {
        Object inicial = entrada.readObject();
        if (!(inicial instanceof Mensaje mensaje)) throw new IOException("Protocolo inválido");

        String contenido = mensaje.getContenido();
        if (isSessionId(contenido)) {
            this.nick = contenido;
            handleTechnicalSession(); // Lógica para SENDER_, FILE_, etc.
        } else {
            this.nick = context.server().getUniqueNick(contenido);
            context.server().registerClient(this, 8080);
            sendComunicacion(new Mensaje("Conectado como: " + nick, CommunicationType.MESSAGE));
            context.server().broadcastMessage("[ " + nick + "] Se ha unido al Chat", this);
        }
    }

    private void processLoop() throws Exception {
        while (!clientSocket.isClosed()) {
            try {
                Object incoming = entrada.readObject();
                if (incoming instanceof Communication comm) {
                    context.dispatcher().dispatch(this, comm, context);
                }
            } catch (ClassNotFoundException e) {
                Logger.logError("Objeto desconocido recibido: " + e.getMessage());
            }
        }
    }

    private void handleTechnicalSession() throws Exception {
        context.server().registerClient(this, 8080);
        Object next = entrada.readObject();

        if (next instanceof FileHandshakeCommunication handshake) {
            context.transferManager().registerHandshake(handshake.getSessionId(), handshake);
        } else if (next instanceof FileDirectoryCommunication comm && nick.startsWith("SENDER_")) {
            // Delegar al servicio experto y seguir escuchando o finalizar según necesidad
            //actionHandlers.get(comm.getType()).handle(comm, this, context);

            context.dispatcher().dispatch(this, comm, context);
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

    public ObjectInputStream objectInputStream() {
        return entrada;
    }

    public ObjectOutputStream getOutputStream() {
        return salida;
    }

    public synchronized void sendComunicacion(Object msg) {
        try {
            salida.writeObject(msg);
            salida.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void shutDown() {


        try {
            if (!isSessionId(nick)){
                context.server().broadcastMessage(nick + " Se a desconectado", this);
            }
            context.registry().removeClient(this);

            context.server().updateClient();
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

    private void close() {
        context.registry().unregister(this);
        try { clientSocket.close(); } catch (IOException ignored) {}
    }
}
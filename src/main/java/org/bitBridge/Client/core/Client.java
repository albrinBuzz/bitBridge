package org.bitBridge.Client.core;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.services.MessageTracker;
import org.bitBridge.Client.services.TransferService;
import org.bitBridge.Observers.HostsObserver;
import org.bitBridge.Observers.NetObserver;
import org.bitBridge.Observers.RemoteDirectoryListener;
import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.shared.*;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.model.basic.DirectoryQuery;
import org.bitBridge.shared.core.comunication.model.basic.HandshakeMessage;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;
import org.bitBridge.shared.network.ClientNetworkEngine;
import org.bitBridge.shared.network.NetworkManager;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Client {
    private List<NetObserver> observers = new ArrayList<>();
    private MessageTracker messageTracker=new MessageTracker();
    private List<HostsObserver>hostsObservers=new ArrayList<>();
    private String SERVER_ADDRESS;
    private int SERVER_PORT;
    private String hostName;
    private ExecutorService executorService;
    private List<RemoteDirectoryListener> listeners = new ArrayList<>();
    //private Observer observer;
    private ConfiguracionApp config =ConfiguracionApp.getInstancia();

    private TransferenciaController transferenciaController;
    private NetworkManager networkManager = new NetworkManager();
    private TransferService transferService;
    private MessageDispatcher dispatcher;

    private ClientContext context;
    private ClientNetworkEngine networkEngine;
    private boolean connectando = false; // Flag de control
    private long conectionTime=0;
    public Client()  {
        this.executorService = Executors.newFixedThreadPool(10); // Usar un pool de hilos para manejar tareas concurrentes
        transferenciaController=new TransferenciaController();
    }



    public synchronized void setConexion(String serverAddress, int serverPort) throws IOException {
        if (networkEngine != null && networkEngine.isActive()) {
            Logger.logInfo("Conexión abortada: Ya existe una sesión activa.");
            return;
        }

        this.SERVER_ADDRESS = serverAddress;
        this.SERVER_PORT = serverPort;

        context = new ClientContext(SERVER_ADDRESS, SERVER_PORT, transferenciaController, executorService, this);

        if (hostName == null){
            hostName = InetAddress.getLocalHost().getHostName();
        }

        this.dispatcher = new MessageDispatcher(this, context);
        this.transferService = new TransferService(context);

        if (this.networkEngine == null) {
            // Le pasamos la referencia de 'this' (Client) para que el motor pueda avisarnos
            this.networkEngine = new NioClientEngine(dispatcher, this,serverAddress,serverPort);
            //this.setConexion(hostName,serverPort);
        }


        networkEngine.connect(serverAddress, serverPort);

    }

    public void onConnectionReady() {
        executorService.execute(() -> {
            try {
                Logger.logInfo("🚀 Canal verificado y seguro. Despachando identificación inicial...");

                // 1. Se crea el mensaje de autenticación legítimo de BitBridge
                HandshakeMessage autenticacion = new HandshakeMessage(hostName, SocketPurpose.CHAT_COMMAND, "");

                // 2. Se invoca el método de envío
                enviarComunicacion(autenticacion);
            } catch (Exception e) {
                e.printStackTrace();
                Logger.logError("Error al enviar saludo de autenticación: " + e.getMessage());
            }
        });
    }


    /*public void conexionAutomatica() throws IOException, InterruptedException {
        DiscoveryService.ServerInfo info = discoveryService.discoverServer();
        setConexion(info.address(), info.port());

    }*/

    public CompletableFuture<Void> conexionAutomatica() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        if (networkEngine != null && networkEngine.isActive()) {
            promise.complete(null);
            return promise;
        }

        Logger.logInfo("[Auto] Iniciando descubrimiento mDNS...");

        networkManager.startLookingForServers((ip, port) -> {
            synchronized (this) {
                if (networkEngine != null && networkEngine.isActive()) return;
            }

            try {
                Logger.logInfo("[mDNS] Intentando conectar a " + ip + ":" + port);
                setConexion(ip, port);

                // SI llegamos aquí sin excepción, la promesa se cumple
                promise.complete(null);

            } catch (IOException e) {
                Logger.logError("Fallo intento con " + ip + ": " + e.getMessage());
                // No completamos excepcionalmente aquí todavía,
                // porque JmDNS podría encontrar otro nodo válido después.
            }
        });

        // Opcional: Timeout por si no encuentra nada en 10 segundos
        promise.orTimeout(10, TimeUnit.SECONDS).exceptionally(ex -> {
            promise.completeExceptionally(new Exception("No se encontró ningún servidor en la red."));
            networkManager.stopAll();
            return null;
        });

        return promise;
    }


    public int getSERVER_PORT() {
        return SERVER_PORT;
    }

    public String getSERVER_ADDRESS() {
        return SERVER_ADDRESS;
    }



    public void desconect() throws IOException {
        networkEngine.disconnect();
        onDisconnect();
    }



    // --- MÉTODOS DE ALTO NIVEL PARA LA UI ---

    /**
     * Envía un archivo de forma agnóstica.
     * La UI solo entrega el destinatario y el archivo.
     */
    public void sendFileToHost(ClientInfo recipient, File file) throws Exception {
        if (file == null || !file.exists()) return;
        transferService.enqueueFileSend(recipient, file,hostName);
    }

    /**
     * Envía un directorio de forma agnóstica.
     */
    public void sendDirectoryToHost(ClientInfo recipient, File directory) {
        if (directory == null || !directory.exists()) return;
        transferService.enqueueDirectorySend(recipient, directory);
    }


    public void enviarMensaje(String mensaje) throws IOException {
        messageTracker.trackNewMessage();
        enviarComunicacion(new Mensaje(mensaje));
    }

    public void sendScreenSnapshot(ClientInfo recipient){
        //screenNetworkHandler.sendScreenSnapshot(recipient.getNick(),1f);
    }

    public void addObserver(NetObserver observer) {
        observers.add(observer);
    }
    public void addHostOserver(HostsObserver observer){
        hostsObservers.add(observer);
    }



    private void notifyObservers(String msg) {
        for (NetObserver obs : observers) {
            obs.onMessageReceived(msg); // Hilo del Socket
        }
    }

    public void notifyHostobserves(List<ClientInfo> hosts) {

        for (HostsObserver observer : hostsObservers) {
                    observer.updateAllHosts(hosts);  // Notifica a los observadores con el nuevo mensaje
        }

        /*if (observers.isEmpty()){
            Logger.logInfo("No hay obseradores");
        }*/
        for (NetObserver observer : observers) {
            observer.onHostListUpdated(hosts);  // Notifica a los observadores con el nuevo mensaje
        }
    }

    public void addDirectoryListener(RemoteDirectoryListener l) { listeners.add(l); }

    // El Dispatcher llama a este método
    public void handleRemoteDirectoryUpdate(NodoDirectorio nodo) {
        for (RemoteDirectoryListener l : listeners) {
            l.onDirectoryDataReceived(nodo);
        }
    }


    // Cuando llega un mensaje nuevo
    public void handleIncomingMessage(String message) {
        //this.msj = message;
        notifyObservers(message);  // Notificar a los observadores que hay un nuevo mensaje
    }


    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getHostName() {
        return hostName;
    }

    public void enviarComunicacion(Communication communication) throws IOException {
        networkEngine.send(communication);
        /*try {
            if (networkEngine != null && networkEngine.isActive()) {
                networkEngine.send(communication);
            }
        } catch (IOException e) {
            Logger.logError("Fallo en el envío: " + e.getMessage());
            //disconnect();
        }*/
    }

    public MessageTracker getMessageTracker() {
        return messageTracker;
    }

    public TransferenciaController getTransferenciaController() {
        return transferenciaController;
    }

    public void setTransferenciaController(TransferenciaController transferenciaController) {
        this.transferenciaController = transferenciaController;
    }

    public void onDisconnect() {
        List<ClientInfo> clientNicks=new ArrayList<>();
        notifyHostobserves(clientNicks);
        for (NetObserver obs : observers) {
            //obs.onMessageReceived(msg); // Hilo del Socket
            obs.onStatusChanged(ServerStatusConnection.DISCONNECTED);
        }
        networkManager.stopAll();
    }

    public boolean isActive() {
       return networkEngine.isActive();
    }

    public void requestFileList(String targetIp) throws IOException {
           // networkEngine.send();
        //DirectoryQuery(targetIp)
        String token = UUID.randomUUID().toString();
        Logger.logInfo(getHostName());
        DirectoryQuery query=new DirectoryQuery(targetIp,this.getHostName(),token);
        networkEngine.send(query);
    }

    public void requestFileList(String targetIp,String ruta) throws IOException {
        // networkEngine.send();
        //DirectoryQuery(targetIp)
        String token = UUID.randomUUID().toString();
        Logger.logInfo(getHostName()+"-"+targetIp);
        DirectoryQuery query=new DirectoryQuery(targetIp,this.getHostName(),token,ruta);
        networkEngine.send(query);
    }

    public synchronized void removeDirectoryListener(RemoteDirectoryListener listener) {
        listeners.remove(listener);
    }


    public long getConnectionTime() {
        return  conectionTime;
    }

    public void onConnectionSuccess() {
        this.conectionTime = System.currentTimeMillis();
    }
}

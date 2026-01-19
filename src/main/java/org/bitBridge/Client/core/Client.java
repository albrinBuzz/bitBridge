package org.bitBridge.Client.core;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.network.DiscoveryService;
import org.bitBridge.Client.services.MessageDispatcher;
import org.bitBridge.Client.services.ScreenNetworkHandler;
import org.bitBridge.Client.services.TransferService;
import org.bitBridge.Observers.HostsObserver;
import org.bitBridge.Observers.NetObserver;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.server.ConfiguracionServidor;
import org.bitBridge.server.core.NioServerEngine;
import org.bitBridge.server.core.client.NioClientHandler;
import org.bitBridge.shared.*;
import org.bitBridge.shared.network.ClientNetworkEngine;
import org.bitBridge.shared.network.ProtocolService;
import org.bitBridge.utils.NetworkManager;
import org.bitBridge.view.core.MainController;

import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private List<NetObserver> observers = new ArrayList<>();

    private List<HostsObserver>hostsObservers=new ArrayList<>();
    private String SERVER_ADDRESS;
    private int SERVER_PORT;
    private String hostName;
    private ExecutorService executorService;
    //private Observer observer;
    private ConfiguracionServidor config =ConfiguracionServidor.getInstancia();

    private TransferenciaController transferenciaController;
    private NetworkManager networkManager = new NetworkManager();
    private TransferService transferService;
    private MessageDispatcher dispatcher;

    private ClientContext context;
    private ClientNetworkEngine networkEngine;
    private boolean connectando = false; // Flag de control

    public Client()  {
        this.executorService = Executors.newFixedThreadPool(10); // Usar un pool de hilos para manejar tareas concurrentes
        transferenciaController=new TransferenciaController();
    }



    public synchronized void setConexion(String serverAddress, int serverPort) throws IOException {
        // 1. Verificación Crítica: Si ya estamos conectados o conectando, abortar.
        if (networkEngine != null && networkEngine.isActive()) {
            Logger.logInfo("Conexión abortada: Ya existe una sesión activa.");
            return;
        }

        this.SERVER_ADDRESS = serverAddress;
        this.SERVER_PORT = serverPort;

        context = new ClientContext(SERVER_ADDRESS, SERVER_PORT, transferenciaController, executorService, this);
        hostName = InetAddress.getLocalHost().getHostName();
        this.dispatcher = new MessageDispatcher(this, context);
        this.transferService = new TransferService(context);

        // 2. Inicializar el motor NIO SOLO si no existe
        if (this.networkEngine == null) {
            this.networkEngine = new NioClientEngine(dispatcher);
        }

        //Logger.logInfo("Intentando establecer conexión NIO con " + serverAddress + ":" + serverPort);

        // 3. Conectar
        try {
            networkEngine.connect(serverAddress, serverPort);

            // 4. Enviar identificación inicial
            Mensaje saludo = new Mensaje(hostName, CommunicationType.MESSAGE);
            enviarComunicacion(saludo);

        } catch (IOException e) {
            Logger.logError("Fallo al conectar: " + e.getMessage());
            throw e; // Relanzar para que el llamador sepa que falló
        }
    }


    /*public void conexionAutomatica() throws IOException, InterruptedException {
        DiscoveryService.ServerInfo info = discoveryService.discoverServer();
        setConexion(info.address(), info.port());

    }*/

    public void conexionAutomatica() {
        // 1. Si ya estamos en proceso de conexión o ya conectados, no buscar más
        if (connectando || (networkEngine != null && networkEngine.isActive())) {
            return;
        }

        Logger.logInfo("[Auto] Iniciando descubrimiento mDNS...");

        networkManager.startLookingForServers((ip, port) -> {
            // El callback de JmDNS puede dispararse muchas veces por segundo
            synchronized (this) {
                if (connectando || (networkEngine != null && networkEngine.isActive())) {
                    return; // Bloqueo de entrada doble
                }
                connectando = true;
            }

            try {
                Logger.logInfo("[mDNS] Servidor detectado en " + ip + ":" + port);
                setConexion(ip, port);
            } catch (IOException e) {
                Logger.logError("Error en auto-conexión: " + e.getMessage());
            } finally {
                // Liberar el flag de intento para permitir futuros descubrimientos si este falló
                synchronized (this) {
                    connectando = false;
                }
            }
        });
    }


    public int getSERVER_PORT() {
        return SERVER_PORT;
    }

    public String getSERVER_ADDRESS() {
        return SERVER_ADDRESS;
    }



    public void desconect() {
        networkEngine.disconnect();
    }



    // --- MÉTODOS DE ALTO NIVEL PARA LA UI ---

    /**
     * Envía un archivo de forma agnóstica.
     * La UI solo entrega el destinatario y el archivo.
     */
    public void sendFileToHost(ClientInfo recipient, File file) {
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
        enviarComunicacion(new Mensaje(mensaje, CommunicationType.MESSAGE));
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


    public TransferenciaController getTransferenciaController() {
        return transferenciaController;
    }

    public void setTransferenciaController(TransferenciaController transferenciaController) {
        this.transferenciaController = transferenciaController;
    }
}

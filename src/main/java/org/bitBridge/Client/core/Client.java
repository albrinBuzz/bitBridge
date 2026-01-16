package org.bitBridge.Client.core;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.network.DiscoveryService;
import org.bitBridge.Client.services.MessageDispatcher;
import org.bitBridge.Client.services.TransferService;
import org.bitBridge.Observers.HostsObserver;
import org.bitBridge.Observers.NetObserver;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.server.ConfiguracionServidor;
import org.bitBridge.shared.*;
import org.bitBridge.utils.NetworkManager;
import org.bitBridge.view.core.MainController;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    private TransferencesObserver transferencesObserver;
    private String SERVER_ADDRESS;
    private int SERVER_PORT;
    private Socket socket;
    private ObjectInputStream entrada;
    private ObjectOutputStream salida;
    private String hostName;
    private ExecutorService executorService;
    //private Observer observer;
    private ConfiguracionServidor config =ConfiguracionServidor.getInstancia();

    private TransferenciaController transferenciaController;
    private NetworkManager networkManager = new NetworkManager();
    private TransferService transferService;
    private MessageDispatcher dispatcher;
    private DiscoveryService discoveryService = new DiscoveryService();

    public Client(){
        this.executorService = Executors.newFixedThreadPool(10); // Usar un pool de hilos para manejar tareas concurrentes
        transferenciaController=new TransferenciaController();

    }



    public void setConexion(String serverAddress, int serverPort) throws IOException {
        this.SERVER_ADDRESS = serverAddress;
        this.SERVER_PORT = serverPort;

        hostName = InetAddress.getLocalHost().getHostName()+ new Random().nextInt(1,9999);
        ClientContext context = new ClientContext(SERVER_ADDRESS, SERVER_PORT, transferenciaController, executorService);
        this.dispatcher = new MessageDispatcher(this, context);
        this.transferService = new TransferService(context);


        // 1. Crear Socket con Timeout de conexión (5 segundos)
        socket = new Socket();
        socket.connect(new InetSocketAddress(serverAddress, serverPort), 5000);
        socket.setSoTimeout(0); // Timeout infinito para lectura una vez conectado

        Logger.logInfo("Conectando a " + serverAddress + ":" + serverPort + " como: " + hostName);

        // 2. IMPORTANTE: El orden debe coincidir con el servidor para evitar Deadlock
        // Primero Salida -> Flush -> Luego Entrada
        salida = new ObjectOutputStream(socket.getOutputStream());
        salida.flush();

        entrada = new ObjectInputStream(socket.getInputStream());

        // 3. Enviar identificación inicial
        Mensaje saludo = new Mensaje(hostName, CommunicationType.MESSAGE);
        salida.writeObject(saludo);
        salida.flush();

        // 4. Iniciar escucha
        executorService.submit(new ReadMessages(entrada));
    }

    /*public void conexionAutomatica() throws IOException, InterruptedException {
        DiscoveryService.ServerInfo info = discoveryService.discoverServer();
        setConexion(info.address(), info.port());

    }*/

    public void conexionAutomatica() {
        Logger.logInfo("[Auto] Buscando servidores BitBridge en la red...");

        networkManager.startLookingForServers((ip, port) -> {
            try {
                // Evitar conectar si ya estamos conectados
                if (socket == null || socket.isClosed()) {
                    Logger.logInfo("[Auto] ¡Servidor detectado! Intentando enlace...");
                    setConexion(ip, port);
                }
            } catch (IOException e) {
                Logger.logError("Error al conectar automáticamente: " + e.getMessage());
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
        try {
            // Verificar si la salida y el socket no están ya cerrados
            if (socket != null && !socket.isClosed()) {
                salida.writeObject(new Mensaje(CommunicationType.DISCONNECT));
            }

            // Solo cerrar el socket y la entrada si no están ya cerrados
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            if (entrada != null) {
                entrada.close();
            }
            List<ClientInfo> clientNicks=new ArrayList<>();
            notifyHostobserves(clientNicks);
            // Actualización del estado de la conexión en el observador
            //this.observer.updateServerConnection(ServerStatusConnection.DISCONNECTED);
            for (NetObserver obs : observers) {
                //obs.onMessageReceived(msg); // Hilo del Socket
                obs.onStatusChanged(ServerStatusConnection.DISCONNECTED);
            }
            Logger.logInfo("Desconectando");

        } catch (IOException e) {
            // Manejo de la excepción
            System.err.println("Error al desconectar: " + e.getMessage());
            e.printStackTrace();
        }
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

        salida.writeObject(new Mensaje(mensaje,CommunicationType.MESSAGE));
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
        if (observers.isEmpty()){
            Logger.logInfo("No hay obseradores");
        }
        for (NetObserver observer : observers) {
            observer.onHostListUpdated(hosts);  // Notifica a los observadores con el nuevo mensaje
        }
    }

    public void setTransferencesObserver(TransferencesObserver transferencesObserver){
        this.transferencesObserver=transferencesObserver;
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

    // Hilo que lee los mensajes del servidor
    private class ReadMessages implements Runnable {
        private final ObjectInputStream entrada;
        private Communication communication;

        public ReadMessages(ObjectInputStream entrada) {
            this.entrada = entrada;
        }

        @Override
        public void run() {
            try {


                while (socket.isConnected()) {

                    Object object = entrada.readObject();


                    if (object != null) {

                        dispatcher.dispatch(object);

                    }else {
                        Logger.logInfo("Mensaje nulo");
                    }
                }
                Logger.logInfo("socket cerrado");

            } catch (IOException | ClassNotFoundException e) {
                Logger.logInfo("Error leyendo del servidor: " + e.getMessage());
                //cleanUp();
                e.printStackTrace();

            }catch (Exception e) {
                    Logger.logInfo("Error leyendo del servidor: " + e.getMessage());
                    e.printStackTrace();

            } finally {
                cleanUp();
            }
        }


        public void cleanUp() {
            Logger.logInfo("Limpiando");
            try {
                if (entrada != null) {
                    entrada.close();
                }
                if (socket != null) {
                    socket.close();
                }
                //notifyHostobserves(new ArrayList<>());
                desconect();

            } catch (IOException e) {
                System.out.println("Error al cerrar recursos: " + e.getMessage());
            }
        }
    }


    public TransferenciaController getTransferenciaController() {
        return transferenciaController;
    }

    public void setTransferenciaController(TransferenciaController transferenciaController) {
        this.transferenciaController = transferenciaController;
    }
}

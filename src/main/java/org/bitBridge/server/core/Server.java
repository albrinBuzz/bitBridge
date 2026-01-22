package org.bitBridge.server.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Observers.ServerObserver;
import org.bitBridge.server.ConfiguracionServidor;
import org.bitBridge.server.NetworkServer;
import org.bitBridge.server.core.client.*;
import org.bitBridge.server.console.ConsoleView;
import org.bitBridge.server.network.NetworkUtils;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.server.transfer.TransferSessionManager;
import org.bitBridge.shared.core.comunication.ClientListMessage;
import org.bitBridge.shared.core.comunication.CommunicationType;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.Mensaje;

import org.bitBridge.shared.network.ServerNetworkEngine;
import org.bitBridge.shared.network.NetworkManager;
import org.bitBridge.utils.UPnPManager;
import org.springframework.context.ConfigurableApplicationContext;


public class Server {
    // Configuración del puerto y otras variables del servidor

    private final TransferSessionManager transferManager = new TransferSessionManager();

    private final ClientRegistry registry = new ClientRegistry();
    private final NicknameService nicknameService = new NicknameService();



    private final CommunicationDispatcher dispatcher;


    private ServerStats stats;
    private NetworkManager networkManager = new NetworkManager();
    private UPnPManager upnpManager = new UPnPManager();

    private ServerObserver serverObserver;
    private static volatile Server serverInstancia;
    private ConfiguracionServidor config = ConfiguracionServidor.getInstancia();
    private  int PORT;

    private ConfigurableApplicationContext springContext;
    public boolean isRunning;

    private final ConsoleView consoleView;
    // Constructor vacío del servidor
    private NetworkServer network;
    private ServerNetworkEngine networkEngine;
    // El Contexto que une a todos
    private final ServerContext context;

    private Server() {
        this.stats = new ServerStats();
        this.PORT = Integer.parseInt(ConfiguracionServidor.getInstancia().obtener("servidor.puerto"));
        this.consoleView = new ConsoleView(stats, PORT);
        this.dispatcher = new CommunicationDispatcher();

        // 1. Crear contexto sin el motor de red todavía
        this.context = new ServerContext(registry, nicknameService, transferManager, stats, this, dispatcher);

        // 2. Crear el motor de red pasándole el contexto (que ya existe)
        this.networkEngine = new NioServerEngine(context);

        // 3. "Cerrar el círculo": Inyectar el motor de red de vuelta al contexto
        this.context.setNetworkEngine(this.networkEngine);
    }



    public CompletableFuture<Void> startServer() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        // 1. Validación de rango antes de intentar abrir el socket
        if (PORT < 0 || PORT > 65535) {
            promise.completeExceptionally(new IllegalArgumentException("Puerto inválido: " + PORT + ". Debe estar entre 0 y 65535."));
        }

        try {

            networkEngine.start(PORT);
            promise.complete(null);
            //startBackgroundServices();

        } catch (BindException e) {
            String sugerencia = (PORT < 1024) ?
                    " (Nota: Los puertos < 1024 son para el sistema)" :
                    " (Verifica si otra instancia de FileTalk está abierta)";

            String errorMsg = String.format("Error: El puerto %d ya está en uso.%s", PORT, sugerencia);
            //Logger.logError(errorMsg);
            //throw new BindException(errorMsg);
            promise.completeExceptionally(new BindException(errorMsg));

        } catch (SecurityException e) {
            String errorMsg = String.format("Permiso denegado: El sistema operativo no permite abrir el puerto %d.", PORT);
            Logger.logError(errorMsg);
            //throw new SecurityException(errorMsg);
            promise.completeExceptionally(new SecurityException(errorMsg));

        } catch (SocketException e) {
            String errorMsg = "Fallo de hardware o protocolo de red en puerto " + PORT + ": " + e.getMessage();
            Logger.logError(errorMsg);
            //throw new SocketException(errorMsg);
            promise.completeExceptionally(new SocketException(errorMsg));

        } catch (IOException e) {
            String errorMsg = "Error crítico de E/S al iniciar en puerto " + PORT + ": " + e.getMessage();
            Logger.logError(errorMsg);
            promise.completeExceptionally(new IOException(errorMsg));
            //throw new IOException(errorMsg);
        }

        return promise;
    }

    /*public void startServer() throws IOException {

        // 1. Validación de rango antes de intentar abrir el socket
        if (PORT < 0 || PORT > 65535) {
            throw new IllegalArgumentException("Puerto inválido: " + PORT + ". Debe estar entre 0 y 65535.");
        }

        try {

            networkEngine.start(PORT);

            startBackgroundServices();

        } catch (BindException e) {
            String sugerencia = (PORT < 1024) ?
                    " (Nota: Los puertos < 1024 son para el sistema)" :
                    " (Verifica si otra instancia de FileTalk está abierta)";

            String errorMsg = String.format("Error: El puerto %d ya está en uso.%s", PORT, sugerencia);
            Logger.logError(errorMsg);
            throw new BindException(errorMsg);

        } catch (SecurityException e) {
            String errorMsg = String.format("Permiso denegado: El sistema operativo no permite abrir el puerto %d.", PORT);
            Logger.logError(errorMsg);
            throw new SecurityException(errorMsg);

        } catch (SocketException e) {
            String errorMsg = "Fallo de hardware o protocolo de red en puerto " + PORT + ": " + e.getMessage();
            Logger.logError(errorMsg);
            throw new SocketException(errorMsg);

        } catch (IOException e) {
            String errorMsg = "Error crítico de E/S al iniciar en puerto " + PORT + ": " + e.getMessage();
            Logger.logError(errorMsg);
            throw new IOException(errorMsg);
        }
    }*/



    private void startBackgroundServices() throws UnknownHostException {
        //ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // Broadcast cada 2 segundos es suficiente
        //scheduler.scheduleAtFixedRate(this::brocastServer, 0, 2, TimeUnit.SECONDS);
        String nickname = ConfiguracionServidor.getInstancia().obtener("usuario.nickname");
        //String host=serverSocket.getInetAddress().getHostName();
        String hostName = InetAddress.getLocalHost().getHostName();
        //int puertoReal = serverSocket.getLocalPort();

        //upnpManager.openPort(puertoReal);

        networkManager.startServerAnnouncement(PORT, hostName);

        // IMPORTANTE: Cambio de MILISEGUNDOS a SEGUNDOS
        //scheduler.scheduleAtFixedRate(this::updateStatus, 0, 1, TimeUnit.SECONDS);
    }


    public String getUniqueNick(String desiredNick) {
        // El Server ya no sabe de sufijos ni bucles, solo delega
        return nicknameService.generateUniqueNick(desiredNick, this.registry);
    }



    public  void starServerCLI() throws IOException {


        //new Thread(consoleView, "Console-Monitor").start();
        startServer();
    }


    public void stopServer() {
        try {
            registry.shutDown();
            networkEngine.stop();
            networkManager.stopAll();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    // Método para obtener la instancia única del servidor (Singleton)
    public static synchronized Server getInstance() {
        if (serverInstancia == null) {
            synchronized (Server.class) {
                if (serverInstancia == null) {
                    serverInstancia = new Server();
                }
            }
        }
        return serverInstancia;
    }

    public synchronized void setServerObserver(ServerObserver observer){
        synchronized (Server.class) {
            this.serverObserver=observer;
        }
    }

    // Método sincronizado para agregar un mensaje al historial de mensajes
    public synchronized void addMessageHistory(String message) {
        stats.recordMessage(message);

    }

    // Método sincronizado para agregar bytes enviados al total
    public synchronized void addBytes(long bytes) {
        stats.recordBytes(bytes);
    }

    // Obtener la lista de clientes conectados
    public List<BitBridgeClient> getClientPool() {

        return registry.getAllHandlers();
    }


    /**
     * Registra un cliente en el pool oficial.
     */
    public void registerClient(BitBridgeClient handler, int puerto) throws IOException {
        // 1. Delegar decisión de transferencia al manager
        if (transferManager.isTransferSession(handler.getNick())) {
            transferManager.registerReceptor(handler.getNick(), handler);
            return;
        }

        // 2. Registro atómico en el Registry
        ClientInfo info = registry.register(handler, puerto);

        // 3. Notificar a servicios satélites (Stats y UI)
        stats.addClient(info);
        notifyObservers();
        updateClient();
    }

    private void notifyObservers() {
        if (serverObserver != null) {
            serverObserver.updateClient(registry.getAllClientInfos(), registry.count());
        }
    }

    /**
     * El emisor llama a este método para esperar al receptor de forma eficiente.
     */
    public BitBridgeClient waitForDataClient(String sessionId, int timeoutSeconds) {
        return transferManager.waitForReceptor(sessionId, timeoutSeconds);
    }





    // Método para hacer broadcast del estado del servidor en la red
    private void brocastServer() {
        String msg = "[" + NetworkUtils.getLocalIp() + "][" + PORT + "]";
        Logger.logInfo(msg);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] buf = msg.getBytes();
            socket.send(new DatagramPacket(buf, buf.length, NetworkUtils.getBroadcastAddress(), 9090));
        } catch (Exception e) {
            Logger.logError("Broadcast error: " + e.getMessage());
        }
    }




    // Método sincronizado para enviar un mensaje a todos los clientes, excepto uno
    // ELIMINA el synchronized. El registry ya usa CopyOnWriteArrayList, que es segura.
    public void broadcastMessage(String message, BitBridgeClient excludeClient) {
        Mensaje msg = new Mensaje(message, CommunicationType.MESSAGE);
        stats.addMessage(message);

        // No bloqueamos todo el servidor mientras iteramos
        registry.getHandlersExcept(excludeClient).forEach(client -> {
            // Importante: sendComunicacion ya tiene su propio synchronized interno por cliente
            client.sendComunicacion(msg);
        });
    }



    public void updateClient() {
        // Obtenemos la lista DESPUÉS de que el registry haya eliminado al cliente
        List<ClientInfo> currentClients = registry.getAllClientInfos();
        ClientListMessage updateMsg = new ClientListMessage(CommunicationType.UPDATE, currentClients);

        // Usamos getAllHandlers() para asegurarnos de no enviar a clientes ya cerrados
        registry.getAllHandlers().forEach(h -> {
            try {
                h.sendComunicacion(updateMsg);
            } catch (Exception e) {
                // Si falla un envío aquí, no pasa nada, ese cliente probablemente se está cerrando también
            }
        });

        if (stats != null) stats.setClients(currentClients);
    }

    public ServerStats getStats() {
        return stats;
    }

/*private void updateUptime(){
        this.serverObserver.updateUptime(formatUptime(serverStartTime));
    }*/

    public void updateStatus(){

        /*Runtime runtime = Runtime.getRuntime();


    // Total de memoria en JVM (en bytes)
            long totalMemory = runtime.totalMemory();

    // Memoria libre en la JVM (en bytes)
            long freeMemory = runtime.freeMemory();

    // Memoria usada en la JVM (en bytes)
            long usedMemory = totalMemory - freeMemory;

    // Convertir de bytes a gigabytes (1 GB = 1024 * 1024 * 1024 bytes)
            double usedMemoryInGB = (double) usedMemory / (1024 * 1024 * 1024);

            String memoria=String.format("%.2f", usedMemoryInGB);

        this.serverObserver.updateMemory(memoria);
        //this.serverObserver.updateUptime(formatUptime(serverStartTime));
        //serverObserver.updateClient(clients.forEach();,Thread.activeCount());
        //clients.forEach(()-> serverObserver.updateClient(t));
        //List<ClientInfo>clientInfos=new ArrayList<>(clients.values());

        //serverObserver.updateClient(clientInfos, Thread.activeCount());
        //clients.forEach((clave,valor)->serverObserver.updateClient(valor, Thread.activeCount()));*/
    }

    public void updateBytes() {

       // this.serverObserver.updateBytes(formatBytes(totalBytesSent));
    }
    public int getPORT() {
        return PORT;
    }
}


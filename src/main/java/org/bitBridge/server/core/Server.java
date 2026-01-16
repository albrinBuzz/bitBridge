package org.bitBridge.server.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Observers.ServerObserver;
import org.bitBridge.server.ConfiguracionServidor;
import org.bitBridge.server.NetworkServer;
import org.bitBridge.server.client.ClientHandler;
import org.bitBridge.server.client.ClientRegistry;
import org.bitBridge.server.client.NicknameService;
import org.bitBridge.server.console.ConsoleView;
import org.bitBridge.server.network.NetworkUtils;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.server.transfer.TransferSessionManager;
import org.bitBridge.shared.ClientListMessage;
import org.bitBridge.shared.CommunicationType;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.Mensaje;

import org.bitBridge.utils.NetworkManager;
import org.bitBridge.utils.UPnPManager;
import org.springframework.context.ConfigurableApplicationContext;


public class Server {
    // Configuración del puerto y otras variables del servidor

    private final TransferSessionManager transferManager = new TransferSessionManager();

    private final ClientRegistry registry = new ClientRegistry();
    private final NicknameService nicknameService = new NicknameService();

    private final List<ClientHandler> clientPool = new CopyOnWriteArrayList<>();

    public AtomicLong totalMessagesReceived = new AtomicLong(0);

    private ServerStats stats;
    private NetworkManager networkManager = new NetworkManager();
    private UPnPManager upnpManager = new UPnPManager();

    private ServerObserver serverObserver;
    private static volatile Server serverInstancia;
    private ConfiguracionServidor config = ConfiguracionServidor.getInstancia();
    private  int PORT;

    private ServerSocket serverSocket;
    private ConfigurableApplicationContext springContext;
    public boolean isRunning;

    private final ConsoleView consoleView;
    // Constructor vacío del servidor
    private NetworkServer network;
    // El Contexto que une a todos
    private final ServerContext context;

    private Server() {
        this.stats = new ServerStats();
        this.PORT = Integer.parseInt(ConfiguracionServidor.getInstancia().obtener("servidor.puerto"));
        this.consoleView = new ConsoleView(stats, PORT);
        this.context = new ServerContext(registry, nicknameService, transferManager, stats, this);
    }

    // Método que inicia el servidor y maneja las conexiones de los clientes
    private int actualPort; // Cambia PORT por una variable para saber cuál quedó activo

    public void startServer() throws IOException {


        // 1. Validación de rango antes de intentar abrir el socket
        if (PORT < 0 || PORT > 65535) {
            throw new IllegalArgumentException("Puerto inválido: " + PORT + ". Debe estar entre 0 y 65535.");
        }

        try {


            // Intentar enlazar el socket
            serverSocket = new ServerSocket(PORT);
            //serverSocket = new ServerSocket(25565, 50, InetAddress.getByName("0.0.0.0"));
            serverSocket.setReuseAddress(true); // Permite reiniciar la app sin esperar a que el puerto se libere
            this.isRunning = true;
            // Obtener la dirección local para el log
            //String localAddress = serverSocket.getInetAddress().getHostAddress();
            String localAddress = NetworkManager.getLocalIp();
            int localPort = serverSocket.getLocalPort();

            startBackgroundServices();

            new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(clientSocket, this, context);
                        new Thread(handler).start();
                    } catch (IOException e) {
                        if (isRunning) Logger.logError("Error al aceptar conexión: " + e.getMessage());
                    }
                }
            }, "Network-Acceptor").start();

            Logger.logInfo("Servidor P2P escuchando en " + localAddress + ":" + localPort);

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
    }

    // Método útil para que la UI sepa qué puerto se asignó finalmente
    public int getActualPort() {
        return actualPort;
    }

    private void startBackgroundServices() throws UnknownHostException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // Broadcast cada 2 segundos es suficiente
        //scheduler.scheduleAtFixedRate(this::brocastServer, 0, 2, TimeUnit.SECONDS);
        String nickname = ConfiguracionServidor.getInstancia().obtener("usuario.nickname");
        //String host=serverSocket.getInetAddress().getHostName();
        String hostName = InetAddress.getLocalHost().getHostName();
        int puertoReal = serverSocket.getLocalPort();

        //upnpManager.openPort(puertoReal);

        networkManager.startServerAnnouncement(puertoReal, hostName);

        // IMPORTANTE: Cambio de MILISEGUNDOS a SEGUNDOS
        scheduler.scheduleAtFixedRate(this::updateStatus, 0, 1, TimeUnit.SECONDS);
    }


    public String getUniqueNick(String desiredNick) {
        // El Server ya no sabe de sufijos ni bucles, solo delega
        return nicknameService.generateUniqueNick(desiredNick, this.registry);
    }



    public  void starServerCLI() throws IOException {


        new Thread(consoleView, "Console-Monitor").start();
        startServer();
    }


    public void stopServer() {
        if (!isRunning) return;

        try {
            Logger.logInfo("Deteniendo servidor...");
            isRunning = false;

            // 1. Cerrar todos los clientes conectados de forma paralela
            clientPool.parallelStream().forEach(handler -> {
                try {
                    handler.shutDown();
                } catch (Exception e) {
                    Logger.logError("Error al cerrar un cliente: " + e.getMessage());
                }
            });
            clientPool.clear();

            // 2. Cerrar el socket principal
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            Logger.logInfo("Servidor detenido correctamente.");
        } catch (IOException e) {
            Logger.logError("Error crítico al cerrar el servidor: " + e.getMessage());
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
    public List<ClientHandler> getClientPool() {

        return registry.getAllHandlers();
    }


    /**
     * Registra un cliente en el pool oficial.
     */
    public void registerClient(ClientHandler handler, int puerto) {
        // 1. Delegar decisión de transferencia al manager
        if (transferManager.isTransferSession(handler.nick)) {
            transferManager.registerReceptor(handler.nick, handler);
            return;
        }

        // 2. Registro atómico en el Registry
        ClientInfo info = registry.register(handler, puerto);

        // 3. Notificar a servicios satélites (Stats y UI)
        stats.addClient(info);
        notifyObservers();
        updateClient(); // Broadcast a los demás clientes
    }

    private void notifyObservers() {
        if (serverObserver != null) {
            serverObserver.updateClient(registry.getAllClientInfos(), registry.count());
        }
    }

    /**
     * El emisor llama a este método para esperar al receptor de forma eficiente.
     */
    public ClientHandler waitForDataClient(String sessionId, int timeoutSeconds) {
        return transferManager.waitForReceptor(sessionId, timeoutSeconds);
    }

    /**
     * Este método lo llama el hilo del RECEPTOR cuando se conecta
     * con un Nick que es en realidad un SessionID.
     */
    public void registerDataClient(String sessionId, ClientHandler receptorHandler) {
        // 1. Buscamos si hay un emisor esperando en ese "punto de encuentro"
        transferManager.registerReceptor(sessionId, receptorHandler);

    }
    /**
     * Busca un cliente por su nombre de usuario.
     */
    public ClientHandler findClientByNick(String nick) {
       return registry.findByNick(nick);
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
    public synchronized void broadcastMessage(String message, ClientHandler excludeClient) {
        Mensaje msg = new Mensaje(message, CommunicationType.MESSAGE);

        // Usamos el registry para obtener a quién enviar
        registry.getHandlersExcept(excludeClient).forEach(client -> {
            client.sendComunicacion(msg);
        });
    }

    // Actualizar la lista de clientes conectados
    public void updateClient(String nick) {
        List<ClientInfo> currentClients = registry.getAllClientInfos();
        ClientListMessage updateMsg = new ClientListMessage(CommunicationType.UPDATE, currentClients);

        registry.getAllHandlers().forEach(h -> h.sendComunicacion(updateMsg));
    }

    public void updateClient() {
        List<ClientInfo> currentClients = registry.getAllClientInfos();
        ClientListMessage updateMsg = new ClientListMessage(CommunicationType.UPDATE, currentClients);

        registry.getAllHandlers().forEach(h -> h.sendComunicacion(updateMsg));
        stats.setClients(currentClients);
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


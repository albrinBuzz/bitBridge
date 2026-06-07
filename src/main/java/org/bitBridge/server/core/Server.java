package org.bitBridge.server.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Observers.ServerObserver;
import org.bitBridge.models.LogEntry;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.server.NetworkServer;
import org.bitBridge.server.core.client.*;
import org.bitBridge.server.console.ConsoleView;
import org.bitBridge.server.handlers.NicknameService;
import org.bitBridge.server.network.NetworkUtils;
import org.bitBridge.server.stats.RemoteTelemetryManager;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.server.transfer.TransferSessionManager;
import org.bitBridge.shared.LogLevel;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.model.basic.ClientListMessage;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;

import org.bitBridge.shared.network.ServerNetworkEngine;
import org.bitBridge.shared.network.NetworkManager;
import org.bitBridge.utils.UPnPManager;
import org.bitBridge.web.MainWeb;
import org.springframework.context.ConfigurableApplicationContext;


public class Server {
    // Configuración del puerto y otras variables del servidor

    private final TransferSessionManager transferManager = new TransferSessionManager();

    private final ClientRegistry registry = new ClientRegistry();
    private final NicknameService nicknameService = new NicknameService();


    private RemoteTelemetryManager telemetryManager;
    private final CommunicationDispatcher dispatcher;
    private final AtomicBoolean updatePending = new AtomicBoolean(false);

    private ServerStats stats;
    private NetworkManager networkManager = new NetworkManager();
    private UPnPManager upnpManager = new UPnPManager();

    private ServerObserver serverObserver;
    private static volatile Server serverInstancia;
    private ConfiguracionApp config = ConfiguracionApp.getInstancia();
    private  int PORT;
    private Consumer<LogEntry> logListener;

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
        this.PORT = Integer.parseInt(ConfiguracionApp.getInstancia().obtener(ConfigKey.SERVER_PORT));
        this.consoleView = new ConsoleView(stats, PORT);
        this.dispatcher = new CommunicationDispatcher();

        // 1. Crear contexto sin el motor de red todavía
        this.context = new ServerContext(registry, nicknameService, transferManager, stats, this, dispatcher);

        // [MODIFICADO] Ya no instanciamos RemoteTelemetryManager aquí para evitar que su
        // ScheduledExecutorService empiece a transmitir en red antes de abrir el socket.

        // 2. Crear el motor de red pasándole el contexto (que ya existe)
        this.networkEngine = new NioServerEngine(context);

        // 3. "Cerrar el círculo": Inyectar el motor de red de vuelta al contexto
        this.context.setNetworkEngine(this.networkEngine);
    }


    public CompletableFuture<Void> startServer() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

        // Evitar la condición de carrera por doble llamada (Spring + Main thread)
        if (this.isRunning) {
            Logger.logWarn("[CORE] El servidor ya se encuentra corriendo o inicializado. Ignorando petición duplicada.");
            promise.complete(null);
            return promise;
        }

        if (PORT < 0 || PORT > 65535) {
            promise.completeExceptionally(new IllegalArgumentException("Puerto inválido: " + PORT));
            return promise;
        }

        try {
            Logger.logInfo("[CORE] Intentando adueñarse del puerto " + PORT + " vía NIO...");

            // Marcar que estamos en proceso críticas antes de abrir el socket
            this.isRunning = true;

            networkEngine.start(PORT);
            startBackgroundServices();

            promise.complete(null);

        } catch (BindException e) {
            this.isRunning = false; // Revertir estado si falló
            String sugerencia = (PORT < 1024) ? " (Nota: Los puertos < 1024 son para el sistema)" : " (Verifica si otra instancia de BitBridge está abierta)";
            String errorMsg = String.format("Error: El puerto %d ya está en uso.%s", PORT, sugerencia);
            promise.completeExceptionally(new BindException(errorMsg));
        } catch (Exception e) {
            this.isRunning = false;
            promise.completeExceptionally(e);
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
        String hostName = InetAddress.getLocalHost().getHostName();

        // Inicialización tardía segura (Post-binding del socket)
        if (this.telemetryManager == null) {
            this.telemetryManager = new RemoteTelemetryManager(this);
        }

        networkManager.startServerAnnouncement(PORT, hostName, this);
    }


    public String getUniqueNick(String desiredNick) {
        // El Server ya no sabe de sufijos ni bucles, solo delega
        return nicknameService.generateUniqueNick(desiredNick, this.registry);
    }



    /**
     * Punto de entrada para el procesamiento de argumentos pasados vía consola (CLI).
     * Modifica las propiedades globales y orquesta el encendido de los componentes asíncronos.
     * java -jar bitBridge.jar --port 9090 --no-encryption --web-server 9091
     * java -jar bitBridge.jar --headless --auto-accept --port 8080 --web-server 8081
     * java -jar bitBridge.jar --headless --auto-accept --workers 16 --download-dir /mnt/storage/downloads --shared-dir /mnt/storage/shared
     */
    public void starServerCLI(String[] args) throws IOException {
        boolean headless = false;
        boolean iniciarWebHeadless = false;
        int puertoWebInyectado = 8081; // Puerto web por defecto para evitar colisiones con el de datos (8080)

        ConfiguracionApp configGlobal = ConfiguracionApp.getInstancia();

        // 1. Procesamiento quirúrgico de argumentos de entrada
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("-h") || arg.equals("--help")) {
                printHelp();
                System.exit(0);
            }

            switch (arg) {
                case "--port":
                    if (i + 1 < args.length) {
                        this.PORT = Integer.parseInt(args[++i]);
                        configGlobal.setProperty(ConfigKey.SERVER_PORT, this.PORT);
                        Logger.logInfo("[CLI-BIND] 🌐 Sobrescribiendo puerto de sockets bitBridge » " + this.PORT);
                    }
                    break;

                case "--headless":
                    headless = true;
                    Logger.logInfo("[CLI-BIND] 🤖 Modo Headless detectado. Se omitirá el despliegue de hilos de interfaz gráfica Swing.");
                    break;

                case "--auto-accept":
                    configGlobal.setProperty(ConfigKey.TRANSFER_AUTO_ACCEPT, "true");
                    Logger.logInfo("[CLI-BIND] 🤖 Auto-Accept forzado vía CLI. Handshakes entrantes se aprobarán automáticamente.");
                    break;

                case "--download-dir":
                    if (i + 1 < args.length) {
                        String nuevoPath = args[++i];
                        configGlobal.setProperty(ConfigKey.DOWNLOAD_DIR, nuevoPath);
                        Logger.logInfo("[CLI-BIND] 📂 Redireccionando directorio de entrada (Downloads) » " + nuevoPath);
                    }
                    break;

                case "--shared-dir":
                    if (i + 1 < args.length) {
                        String nuevoShared = args[++i];
                        configGlobal.setProperty(ConfigKey.SHARED_DIR, nuevoShared);
                        Logger.logInfo("[CLI-BIND] 📂 Redireccionando directorio de activos compartidos (Assets) » " + nuevoShared);
                    }
                    break;

                case "--workers":
                    if (i + 1 < args.length) {
                        int numWorkers = Integer.parseInt(args[++i]);
                        configGlobal.setProperty(ConfigKey.NET_WORKER_THREADS, numWorkers);
                        Logger.logInfo("[CLI-BIND] ⚙️ Escalando Pool de Subprocesos reactivos » " + numWorkers + " Worker Threads.");
                    }
                    break;

                case "--no-encryption":
                    configGlobal.setProperty(ConfigKey.NET_ENCRYPTION, "NONE");
                    Logger.logWarn("[CLI-BIND] ⚠️ ALERTA: Criptografía de canal desactivada vía CLI. Datos viajarán en texto plano.");
                    break;

                case "--web-server":
                    iniciarWebHeadless = true;
                    // Evalúa si el siguiente argumento es un puerto numérico y no otra bandera de comando
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        try {
                            puertoWebInyectado = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            Logger.logError("[CLI-ERROR] El puerto provisto para el servidor web no es válido.");
                            System.exit(1);
                        }
                    }
                    Logger.logInfo("[CLI-BIND] 🌐 Servidor Web bitBridge programado en el puerto » " + puertoWebInyectado);
                    break;

                default:
                    Logger.logError("[CLI-ERROR] Argumento desconocido o mal configurado: '" + arg + "'. Use --help para el listado.");
                    System.exit(1);
            }
        }

        // 2. Orquestación del arranque (Previene dobles inicios del Socket NIO gracias a tu validación nativa)
        if (headless) {
            startServerHeadless(args);

            if (iniciarWebHeadless) {
                ejecutarServidorWebHeadless(puertoWebInyectado);
            }
        } else {
            Logger.logInfo("[CORE] Lanzando consola interactiva de monitoreo ConsoleView...");
            new Thread(this.consoleView, "Console-Monitor").start();

            if (iniciarWebHeadless) {
                ejecutarServidorWebHeadless(puertoWebInyectado);
            }

            try {
                // Ejecuta el arranque asíncrono y bloquea de forma segura el hilo principal para que la terminal no se muera
                startServer().join();
            } catch (Exception e) {
                Logger.logError("[CORE-FATAL] Error crítico al arrancar el socket interactivo: " + e.getMessage());
            }
        }
    }

    /**
     * Muestra el menú instructivo formateado para la terminal de administración en Linux.
     */
    private void printHelp() {
        System.out.println("=======================================================================");
        System.out.println("   bitBridge Engine v1.0.0-SNAPSHOT - CLI Core Management System");
        System.out.println("=======================================================================");
        System.out.println("Uso: java -jar bitBridge.jar [opciones]");
        System.out.println("\nOpciones de Infraestructura:");
        System.out.println("  -h, --help               Muestra este menú de ayuda estructurado y finaliza.");
        System.out.println("  --port <puerto>          Sobrescribe el puerto TCP del socket binario (Default: 8080).");
        System.out.println("  --headless               Inicia el servidor sin monitores gráficos (Modo Daemon).");
        System.out.println("  --workers <cantidad>     Define el tamaño del pool de hilos para procesamiento masivo.");
        System.out.println("  --no-encryption          Desactiva el cifrado de payloads (Optimiza CPU en LANs locales).");
        System.out.println("\nOpciones de Almacenamiento y Servidores Auxiliares:");
        System.out.println("  --auto-accept            Acepta automáticamente transferencias entrantes (Rsync Bypass).");
        System.out.println("  --download-dir <ruta>    Especifica la ruta absoluta para depositar descargas locales.");
        System.out.println("  --shared-dir <ruta>      Especifica la ruta raíz del catálogo que verán los peers.");
        System.out.println("  --web-server [puerto]    Levanta el puente web HTTP/JSF embebido (Por defecto: 8081).");
        System.out.println("=======================================================================");
    }

    /**
     * Lanza el motor asíncrono en modo demonio oculto esperando de forma segura que el binding de red finalice.
     */
    private void startServerHeadless(String[] args) throws IOException {
        Logger.logInfo("[CORE] Levantando socket en modo headless seguro (Daemon)...");

        // Bloquea de forma asíncrona controlada utilizando la promesa CompletableFuture que ya programaste en startServer()
        startServer().join();

        Logger.logInfo("[CORE] Servidor iniciado en modo headless de forma correcta. Monitoreo pasivo en ejecución.");
    }

    /**
     * Comprueba de manera rápida la disponibilidad de un puerto en el Kernel de Linux.
     */
    private boolean esPuertoDisponible(int puerto) {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(puerto)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Inicializa el servidor Web embebido de Spring Boot utilizando la variable global de la clase Server.
     */
    private void ejecutarServidorWebHeadless(int puertoAIntentar) {
        if (!esPuertoDisponible(puertoAIntentar)) {
            Logger.logError(String.format("[WEB-CRITICAL] El puerto HTTP %d ya está siendo utilizado por otra aplicación.", puertoAIntentar));
            System.exit(1);
        }

        Logger.logInfo("[WEB-HTTP] Levantando contexto Spring Boot en segundo plano...");

        new Thread(() -> {
            try {
                System.setProperty("server.port", String.valueOf(puertoAIntentar));

                // Levantamos el contexto directamente sobre tu variable de clase privada: springContext
                this.springContext = new org.springframework.boot.builder.SpringApplicationBuilder(MainWeb.class)
                        .properties("server.port=" + puertoAIntentar)
                        .headless(true) // Forzar headless nativo de Spring (Aísla de entornos gráficos X11)
                        .run();

                String ip = InetAddress.getLocalHost().getHostAddress();
                String urlFinal = "http://" + ip + ":" + puertoAIntentar + "/home/index.xhtml";

                Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
                Logger.logInfo("│ 🌐  [PUENTE WEB ACTIVO] El panel de control está en línea       │");
                Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
                Logger.logInfo(String.format("│ 🔗  URL de Acceso: %-45s │", urlFinal));
                Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");

            } catch (Exception e) {
                Logger.logError("[WEB-FATAL] Falló el arranque del puente web HTTP.");

                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                if (msg.contains("Port already in use") || msg.contains("BindException")) {
                    Logger.logError("   ├── [CAUSA] Puerto ocupado. Libera el puerto o reasigna con --web-server [puerto_libre]");
                } else if (msg.contains("Permission denied")) {
                    Logger.logError("   ├── [CAUSA] Permiso denegado por el Kernel de Linux (Puertos < 1024 requieren privilegios root/sudo).");
                } else {
                    Logger.logError("   ├── [DETALLE] " + msg);
                }
                System.exit(1);
            }
        }, "Nio-Web-Engine").start();
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


    public void setLogListener(Consumer<LogEntry> listener) {
        this.logListener = listener;
    }


    public void notifyUI(String msg, LogLevel type) {
        if (logListener != null) {
            logListener.accept(new LogEntry(msg, type));
        }
    }

    // Método sincronizado para enviar un mensaje a todos los clientes, excepto uno
    // ELIMINA el synchronized. El registry ya usa CopyOnWriteArrayList, que es segura.
    /*public void broadcastMessage(String message, BitBridgeClient excludeClient) {
        Mensaje msg = new Mensaje(message, CommunicationType.MESSAGE);

        // 1. Preparamos el buffer compartido
        ByteBuffer sharedBuffer = null;
        try {
            sharedBuffer = ProtocolService.toNioBuffer(msg, 100);
            if (sharedBuffer == null) return;

            List<BitBridgeClient> recipients = registry.getHandlersExcept(excludeClient);
            int count = recipients.size();

            // 2. Usamos un "AtomicInteger" para saber cuándo todos terminaron de enviar
            AtomicInteger refCount = new AtomicInteger(count);

            ByteBuffer finalSharedBuffer = sharedBuffer;
            recipients.forEach(client -> {
                // Pasamos el buffer y el contador al cliente
                if (client instanceof  NioClientHandler cliente){
                    cliente.sendSharedBuffer(finalSharedBuffer.duplicate(), refCount);
                }

            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/

    public void broadcastMessage(String message, BitBridgeClient excludeClient) {
        Mensaje msg = new Mensaje(message);
        //stats.addMessage(message);

        // No bloqueamos todo el servidor mientras iteramos
        registry.getHandlersExcept(excludeClient).forEach(client -> {
            // Importante: sendComunicacion ya tiene su propio synchronized interno por cliente
            //client.sendComunicacion(msg);
            Thread.ofVirtual().start(() -> {
                client.sendComunicacion(msg);
            });
        });
    }


    public void updateClient() {
        // Si ya hay una actualización programada, no hacemos nada
        if (updatePending.compareAndSet(false, true)) {
            Thread.ofVirtual().start(() -> {
                try {
                    // Esperamos un poco para agrupar múltiples desconexiones/conexiones
                    Thread.sleep(1000);

                    List<ClientInfo> currentClients = registry.getAllClientInfos();
                    ClientListMessage updateMsg = new ClientListMessage(currentClients);

                    // Enviamos a todos
                    registry.getAllHandlers().forEach(h -> h.sendComunicacion(updateMsg));

                    if (stats != null) stats.setClients(currentClients);
                } catch (InterruptedException ignored) {
                } finally {
                    updatePending.set(false);
                }
            });
        }
    }

    public ServerStats getStats() {
        return stats;
    }

    public int getPORT() {
        return PORT;
    }

    public ServerNetworkEngine getNetworkEngine() {
        return this.networkEngine;
    }

    public RemoteTelemetryManager getTelemetryManager() {
        return this.telemetryManager;
    }
}


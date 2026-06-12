package org.bitBridge.server.core;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
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
import org.bitBridge.server.core.secure.SslContextFactory;
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
import org.bitBridge.shared.core.secure.BitBridgeCryptoEngine;
import org.bitBridge.shared.core.secure.CryptoConfig; // Asegurar imports de tu motor modular


import org.bitBridge.shared.network.ServerNetworkEngine;
import org.bitBridge.shared.network.NetworkManager;
import org.bitBridge.utils.UPnPManager;
import org.bitBridge.web.MainWeb;
import org.springframework.context.ConfigurableApplicationContext;

import javax.net.ssl.SSLContext;

public class Server {

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
    private int PORT;
    private Consumer<LogEntry> logListener;

    private ConfigurableApplicationContext springContext;
    public boolean isRunning;

    private final ConsoleView consoleView;
    private NetworkServer network;
    private ServerNetworkEngine networkEngine;
    private final ServerContext context;
    private SSLContext sslContext;
    private boolean tlsHabilitado;

    private Server() {
        this.stats = new ServerStats();
        this.PORT = Integer.parseInt(ConfiguracionApp.getInstancia().obtener(ConfigKey.SERVER_PORT));
        this.consoleView = new ConsoleView(stats, PORT);
        this.dispatcher = new CommunicationDispatcher();

        // 1. Crear el contexto base de BitBridge
        this.context = new ServerContext(registry, nicknameService, transferManager, stats, this, dispatcher);

        // 2. Intentar inicialización temprana por defecto basada en archivo de propiedades externo
        inicializarEntornoSeguro();

        // 3. Unir capas del ciclo de vida NIO
        this.networkEngine = new NioServerEngine(context);
        this.context.setNetworkEngine(this.networkEngine);
    }

    /**
     * Módulo interceptor que puede ser reinvocado bajo demanda si el CLI genera llaves dinámicamente
     * en el arranque temprano (Early Boot Lifecycle).
     */
    public void inicializarEntornoSeguro() {
        try {
            String tlsProp = config.obtener(ConfigKey.NET_TLS_ENABLED);
            tlsHabilitado = (tlsProp != null) && tlsProp.trim().equalsIgnoreCase("true");

            if (tlsHabilitado) {
                String keystorePath = config.obtener(ConfigKey.NET_TLS_KEYSTORE);
                String keystorePass = config.obtener(ConfigKey.NET_TLS_PASSWORD);

                if (keystorePath == null || keystorePath.isEmpty()) {
                    throw new IllegalArgumentException("La ruta del Keystore (PKCS12) está configurada como vacía.");
                }

                // Carga dinámica mediante BouncyCastle/SunJSSE nativo
                this.sslContext = SslContextFactory.crearContextoServidor(keystorePath.trim(), keystorePass.trim());
                this.context.setSslContext(this.sslContext);
                Logger.logInfo("🔒 [CRYPTO-CORE] Entorno SSL/TLS 1.3 cargado e inyectado con éxito.");
            } else {
                this.sslContext = null;
                this.context.setSslContext(null);
                Logger.logWarn("⚠️ [CRYPTO-CORE] Entorno SSL/TLS 1.3 deshabilitado explícitamente en la configuración.");
            }
        } catch (Exception e) {
            Logger.logError("❌ Error crítico inicializando llaves TLS. El servidor caerá en texto plano: " + e.getMessage());
            this.sslContext = null;
            this.context.setSslContext(null);
        }
    }

    public CompletableFuture<Void> startServer() {
        CompletableFuture<Void> promise = new CompletableFuture<>();

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
            this.isRunning = true;

            networkEngine.start(PORT);
            startBackgroundServices();
            promise.complete(null);

        } catch (BindException e) {
            this.isRunning = false;
            String sugerencia = (PORT < 1024) ? " (Nota: Los puertos < 1024 son para el sistema)" : " (Verifica si otra instancia de BitBridge está abierta)";
            String errorMsg = String.format("Error: El puerto %d ya está en uso.%s", PORT, sugerencia);
            promise.completeExceptionally(new BindException(errorMsg));
        } catch (Exception e) {
            this.isRunning = false;
            promise.completeExceptionally(e);
        }

        return promise;
    }

    private void startBackgroundServices() throws UnknownHostException {
        String hostName = InetAddress.getLocalHost().getHostName();
        if (this.telemetryManager == null) {
            this.telemetryManager = new RemoteTelemetryManager(this);
        }
        networkManager.startServerAnnouncement(PORT, hostName, this);
    }

    public String getUniqueNick(String desiredNick) {
        return nicknameService.generateUniqueNick(desiredNick, this.registry);
    }

    /**
     * Punto de entrada para el procesamiento de argumentos pasados vía consola (CLI).
     * Modifica las propiedades globales y orquesta el encendido de los componentes asíncronos.
     */
    public void starServerCLI(String[] args) throws IOException {
        boolean headless = false;
        boolean iniciarWebHeadless = false;
        int puertoWebInyectado = 8081;

        ConfiguracionApp configGlobal = ConfiguracionApp.getInstancia();

        // ——————————————————————————————————————————————————————————————————————————
        // PASO 1: INTERCEPTOR TEMPRANO CRIPTOGRÁFICO
        // Analiza primero si el administrador requiere generar la criptografía de infraestructura
        // ——————————————————————————————————————————————————————————————————————————
        for (int i = 0; i < args.length; i++) {
            if ("--secure-init".equalsIgnoreCase(args[i])) {
                Logger.logInfo("🔒 [CLI-INTERCEPTOR] Inicializador criptográfico detectado. Suspendiendo hilos de red...");

                // 1. Definir la ruta estándar Unix (XDG Compliance)
                Path dirCerts = Paths.get(System.getProperty("user.home"), ".config", "bitbridge", "certs");
                String pass = "123";
                String cn = "BitBridgeLocalNode";
                String org = "BitBridgeBeta";
                int validez = 365;
                List<String> dominiosExtra = new ArrayList<>();

                // 2. Parsing de sub-parámetros (Permite sobrescribir el directorio por si corres como servicio en /etc/ssl)
                for (int j = 0; j < args.length; j++) {
                    if ("--secure-dir".equalsIgnoreCase(args[j]) && j + 1 < args.length) {
                        dirCerts = Paths.get(args[++j]);
                    }
                    if ("--secure-pass".equalsIgnoreCase(args[j]) && j + 1 < args.length) pass = args[++j];
                    if ("--secure-cn".equalsIgnoreCase(args[j]) && j + 1 < args.length) cn = args[++j];
                    if ("--secure-org".equalsIgnoreCase(args[j]) && j + 1 < args.length) org = args[++j];
                    if ("--secure-days".equalsIgnoreCase(args[j]) && j + 1 < args.length) validez = Integer.parseInt(args[++j]);
                    if ("--secure-san".equalsIgnoreCase(args[j]) && j + 1 < args.length) {
                        dominiosExtra = Arrays.asList(args[++j].split("\\s*,\\s*"));
                    }
                }

                try {
                    // 3. Crear el árbol de directorios con permisos POSIX ultra-restrictivos (rwx------ / 700)
                    if (!Files.exists(dirCerts)) {
                        if (System.getProperty("os.name").toLowerCase().contains("unix") ||
                                System.getProperty("os.name").toLowerCase().contains("linux") ||
                                System.getProperty("os.name").toLowerCase().contains("mac")) {

                            Set<PosixFilePermission> permsDir = PosixFilePermissions.fromString("rwx------");
                            Files.createDirectories(dirCerts, PosixFilePermissions.asFileAttribute(permsDir));
                        } else {
                            // Fallback para Windows (NIO gestiona las ACL por defecto del usuario actual)
                            Files.createDirectories(dirCerts);
                        }
                        Logger.logInfo("📂 [OS-SECURE] Directorio seguro Unix creado en: " + dirCerts.toAbsolutePath());
                    }

                    Logger.logInfo("> Desplegando CryptoConfig dinámico en entorno CLI...");
                    CryptoConfig cryptoConfig = new CryptoConfig(cn, org, "CL", validez);
                    dominiosExtra.forEach(cryptoConfig::agregarDominioODns);

                    Path pathKeystore = dirCerts.resolve("keystore_beta.p12");
                    Path pathCert = dirCerts.resolve("certificado_beta.crt");

                    // 4. Invocación al motor modular
                    BitBridgeCryptoEngine.generarEntornoSeguroProduction(
                            pathKeystore.toAbsolutePath().toString(),
                            pathCert.toAbsolutePath().toString(),
                            pass,
                            cryptoConfig
                    );

                    // 5. Hardening de archivos: Aplicar permisos chmod 600 (rw-------) a las llaves generadas
                    if (Files.getFileStore(dirCerts).supportsFileAttributeView("posix")) {
                        Set<PosixFilePermission> permsArchivo = PosixFilePermissions.fromString("rw-------");
                        Files.setPosixFilePermissions(pathKeystore, permsArchivo);
                        Files.setPosixFilePermissions(pathCert, permsArchivo);
                        Logger.logInfo("🛡️ [POSIX-HARDENING] Permisos endurecidos con éxito a nivel de Kernel (chmod 600).");
                    }

                    Logger.logInfo("✅ [CLI-INTERCEPTOR] Infraestructura criptográfica de nivel OS creada correctamente.");

                    // Sincronizar dinámicamente las propiedades en caliente
                    configGlobal.setProperty(ConfigKey.NET_TLS_ENABLED, "true");
                    configGlobal.setProperty(ConfigKey.NET_TLS_KEYSTORE, pathKeystore.toAbsolutePath().toString());
                    configGlobal.setProperty(ConfigKey.NET_TLS_PASSWORD, pass);

                    // Forzar recarga del contexto SSL del Server antes de abrir sockets NIO
                    inicializarEntornoSeguro();

                } catch (Exception e) {
                    Logger.logError("❌ [CLI-FATAL] Error de aislamiento en el Sistema Operativo: " + e.getMessage());
                    System.exit(1);
                }
                break;
            }
        }

        /**
         * Punto de entrada para el procesamiento de argumentos pasados vía consola (CLI).
         * Modifica las propiedades globales y orquesta el encendido de los componentes asíncronos.
         * java -jar bitBridge.jar --port 9090 --no-encryption --web-server 9091
         * java -jar bitBridge.jar --headless --auto-accept --port 8080 --web-server 8081
         * java -jar bitBridge.jar --headless --auto-accept --workers 16 --download-dir /mnt/storage/downloads --shared-dir /mnt/storage/shared
         * java -jar bitBridge.jar --headless --secure-init --secure-dir ./certs --secure-pass secret123 --secure-cn nodeserver.com --secure-san 200.10.20.30,node1.net --port 8443
         *
         */

        // ——————————————————————————————————————————————————————————————————————————
        // PASO 2: PROCESAMIENTO ESTÁNDAR DE ARGUMENTOS DE RED Y ALMACENAMIENTO
        // ——————————————————————————————————————————————————————————————————————————
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("-h") || arg.equals("--help")) {
                printHelp();
                System.exit(0);
            }

            // Omitir banderas ya procesadas por el interceptor temprano
            if (arg.startsWith("--secure-")) {
                if (arg.equals("--secure-init")) continue;
                // Si la bandera lleva argumento variable, saltarlo para no romper el switch
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) i++;
                continue;
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
                    configGlobal.setProperty(ConfigKey.NET_TLS_ENABLED, "false");
                    inicializarEntornoSeguro(); // Forzar caída a texto plano en caliente
                    Logger.logWarn("[CLI-BIND] ⚠️ ALERTA: Criptografía de canal desactivada vía CLI. Datos viajarán en texto plano.");
                    break;

                case "--web-server":
                    iniciarWebHeadless = true;
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

        // 3. Orquestación final del arranque
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
                startServer().join();
            } catch (Exception e) {
                Logger.logError("[CORE-FATAL] Error crítico al arrancar el socket interactivo: " + e.getMessage());
            }
        }
    }

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
        System.out.println("\nOpciones de Seguridad Automática (Early Boot):");
        System.out.println("  --secure-init            Inicializa e inyecta certificados TLS antes de prender el servidor.");
        System.out.println("  --secure-dir <ruta>      Ruta base de salida para llaves y certificados.");
        System.out.println("  --secure-pass <clave>    Password del contenedor criptográfico PKCS12.");
        System.out.println("  --secure-cn <name>       Common Name (Sujeto principal) del certificado.");
        System.out.println("  --secure-san <ips/dns>   Valores dinámicos extra SAN separados por comas.");
        System.out.println("\nOpciones de Almacenamiento y Servidores Auxiliares:");
        System.out.println("  --auto-accept            Acepta automáticamente transferencias entrantes (Rsync Bypass).");
        System.out.println("  --download-dir <ruta>    Especifica la ruta absoluta para depositar descargas locales.");
        System.out.println("  --shared-dir <ruta>      Especifica la ruta raíz del catálogo que verán los peers.");
        System.out.println("  --web-server [puerto]    Levanta el puente web HTTP/JSF embebido (Por defecto: 8081).");
        System.out.println("=======================================================================");
    }

    private void startServerHeadless(String[] args) throws IOException {
        Logger.logInfo("[CORE] Levantando socket en modo headless seguro (Daemon)...");
        startServer().join();
        Logger.logInfo("[CORE] Servidor iniciado en modo headless de forma correcta. Monitoreo pasivo en ejecución.");
    }

    private boolean esPuertoDisponible(int puerto) {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(puerto)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void ejecutarServidorWebHeadless(int puertoAIntentar) {
        if (!esPuertoDisponible(puertoAIntentar)) {
            Logger.logError(String.format("[WEB-CRITICAL] El puerto HTTP %d ya está siendo utilizado por otra aplicación.", puertoAIntentar));
            System.exit(1);
        }

        Logger.logInfo("[WEB-HTTP] Levantando contexto Spring Boot en segundo plano...");

        new Thread(() -> {
            try {
                System.setProperty("server.port", String.valueOf(puertoAIntentar));
                this.springContext = new org.springframework.boot.builder.SpringApplicationBuilder(MainWeb.class)
                        .properties("server.port=" + puertoAIntentar)
                        .headless(true)
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
            this.serverObserver = observer;
        }
    }

    public synchronized void addMessageHistory(String message) {
        stats.recordMessage(message);
    }

    public synchronized void addBytes(long bytes) {
        stats.recordBytes(bytes);
    }

    public List<BitBridgeClient> getClientPool() {
        return registry.getAllHandlers();
    }

    public void registerClient(BitBridgeClient handler, int puerto) throws IOException {
        Logger.logInfo("Registrando el cliente");
        if (transferManager.isTransferSession(handler.getNick())) {
            transferManager.registerReceptor(handler.getNick(), handler);
            return;
        }
        ClientInfo info = registry.register(handler, puerto);
        stats.addClient(info);
        notifyObservers();
        updateClient();
    }

    private void notifyObservers() {
        if (serverObserver != null) {
            serverObserver.updateClient(registry.getAllClientInfos(), registry.count());
        }
    }

    public BitBridgeClient waitForDataClient(String sessionId, int timeoutSeconds) {
        return transferManager.waitForReceptor(sessionId, timeoutSeconds);
    }

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

    public void broadcastMessage(String message, BitBridgeClient excludeClient) {
        Logger.logInfo("Haciendo el broadcast");
        Mensaje msg = new Mensaje(message);
        registry.getHandlersExcept(excludeClient).forEach(client -> {
            Thread.ofVirtual().start(() -> {
                client.sendComunicacion(msg);
            });
        });
    }

    public void updateClient() {
        Logger.logInfo("Actualizando los clientes ");
        if (updatePending.compareAndSet(false, true)) {
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(1000);
                    List<ClientInfo> currentClients = registry.getAllClientInfos();
                    ClientListMessage updateMsg = new ClientListMessage(currentClients);
                    registry.getAllHandlers().forEach(h -> h.sendComunicacion(updateMsg));
                    if (stats != null) stats.setClients(currentClients);
                } catch (InterruptedException ignored) {
                } finally {
                    updatePending.set(false);
                }
            });
        }
    }

    public ServerStats getStats() { return stats; }
    public int getPORT() { return PORT; }
    public ServerNetworkEngine getNetworkEngine() { return this.networkEngine; }
    public RemoteTelemetryManager getTelemetryManager() { return this.telemetryManager; }
}
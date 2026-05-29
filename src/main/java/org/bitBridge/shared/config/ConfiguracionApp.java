package org.bitBridge.shared.config;

import org.bitBridge.server.config.ConfigKey;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ConfiguracionApp {

    private static ConfiguracionApp instancia;
    private final Properties propiedades;
    private final String rutaArchivoConfig;
    private final int CORES = Runtime.getRuntime().availableProcessors();

    private ConfiguracionApp() {
        this.propiedades = new Properties();
        // Unificamos el archivo físico en la ruta estándar de configuración de Linux (~/.config)
        Path pathConfig = Paths.get(System.getProperty("user.home"), ".config", "bitbridge", "bitbridge.properties");
        this.rutaArchivoConfig = pathConfig.toString();
        loadConfig();
    }

    public static synchronized ConfiguracionApp getInstancia() {
        if (instancia == null) {
            instancia = new ConfiguracionApp();
        }
        return instancia;
    }

    private void loadConfig() {
        File file = new File(rutaArchivoConfig);
        try {
            if (!file.exists()) {
                crearConfiguracionBase();
            } else {
                try (BufferedInputStream entrada = new BufferedInputStream(new FileInputStream(file))) {
                    propiedades.load(entrada);
                    validarClavesFaltantes();
                }
            }
        } catch (IOException e) {
            crearConfiguracionBase();
        }
    }

    private void crearConfiguracionBase() {
        // --- 1. IDENTIDAD Y RED ---
        setProperty(ConfigKey.SERVER_NAME, "BB-NODE-" + System.getProperty("user.name").toUpperCase());
        setProperty(ConfigKey.SERVER_PORT, "8080");
        setProperty(ConfigKey.NET_MAX_CONN, "500");
        setProperty(ConfigKey.NET_BACKLOG, "128");
        setProperty(ConfigKey.NET_KEEPALIVE, "45000");
        setProperty(ConfigKey.NET_NODELAY, "true");
        setProperty(ConfigKey.NET_ENCRYPTION, "AES-256-GCM");

        // --- 2. MULTITHREADING (HILOS Y PROCESADORES) ---
        setProperty(ConfigKey.NET_SELECTOR_THREADS, "1");
        setProperty(ConfigKey.NET_WORKER_THREADS, String.valueOf(Math.max(2, CORES)));

        // --- 3. ALMACENAMIENTO SIMÉTRICO (ESPEJO RECEPTOR/EMISOR) ---
        Path homeUser = Paths.get(System.getProperty("user.home"));
        Path baseBitBridge = homeUser.resolve("BitBridge");
        Path descargas = baseBitBridge.resolve("Downloads");
        Path compartido = baseBitBridge.resolve("Shared");

        setProperty(ConfigKey.DOWNLOAD_DIR, descargas.toString());
        setProperty(ConfigKey.SHARED_DIR, compartido.toString());
        setProperty(ConfigKey.TRANSFER_MAX_ACTIVE, "3");
        setProperty(ConfigKey.TRANSFER_AUTO_RESUME, "true");
        setProperty(ConfigKey.TRANSFER_OVERWRITE, "false");

        // --- 4. CONFIGURACIONES EXCLUSIVAS DE CLIENTE ---
        propiedades.setProperty("cliente.id", "CL-" + (System.currentTimeMillis() % 1000));
        propiedades.setProperty("cliente.intentos_reconexion", "5");

        // --- 5. POOLS DE MEMORIA BAJO NIVEL (NIO) ---
        setProperty(ConfigKey.NIO_BUFFER_SIZE, "32768"); // 32KB
        setProperty(ConfigKey.NIO_ZERO_COPY, "true");
        setProperty(ConfigKey.NIO_DIRECT_BUF, "true");
        setProperty(ConfigKey.NIO_POOL_CAPACITY, "50");

        // Mensajes cortos (JSON/Control)
        setProperty(ConfigKey.POOL_MSG_SIZE, "4096");
        setProperty(ConfigKey.POOL_MSG_CAP, "100");

        // Listado de directorios
        setProperty(ConfigKey.POOL_DIR_SIZE, "32768");
        setProperty(ConfigKey.POOL_DIR_CAP, "20");

        // Bloques de transferencia pesados
        setProperty(ConfigKey.POOL_TRANS_SIZE, "262144"); // 256KB de buffer de relay rápido
        setProperty(ConfigKey.POOL_TRANS_CAP, "10");

        // --- 6. SEGURIDAD ---
        setProperty(ConfigKey.AUTH_REQUIRED, "false");
        setProperty(ConfigKey.AUTH_KEY, "");

        inicializarEntornoFisico(descargas, compartido);
    }

    private void validarClavesFaltantes() {
        boolean cambio = false;
        if (obtener(ConfigKey.NET_SELECTOR_THREADS) == null) {
            setProperty(ConfigKey.NET_SELECTOR_THREADS, "1");
            cambio = true;
        }
        if (obtener(ConfigKey.NET_WORKER_THREADS) == null) {
            setProperty(ConfigKey.NET_WORKER_THREADS, String.valueOf(CORES * 2));
            cambio = true;
        }
        if (propiedades.getProperty("cliente.intentos_reconexion") == null) {
            propiedades.setProperty("cliente.intentos_reconexion", "5");
            cambio = true;
        }
        if (cambio) guardarEnArchivo();
    }

    private void inicializarEntornoFisico(Path d, Path c) {
        try {
            Files.createDirectories(Paths.get(rutaArchivoConfig).getParent());
            Files.createDirectories(d);
            Files.createDirectories(c);
            guardarEnArchivo();
        } catch (IOException e) {
            System.err.println("Error al inicializar carpetas físicas de BitBridge: " + e.getMessage());
        }
    }

    public void guardarEnArchivo() {
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(rutaArchivoConfig))) {
            propiedades.store(output, "BitBridge Unified Global Configuration");
        } catch (IOException e) {
            System.err.println("Error al guardar bitbridge.properties: " + e.getMessage());
        }
    }

    // --- MÉTODOS DE ACCESO SEGUROS ---
    public String obtener(ConfigKey clave, String valorPorDefecto) {
        return propiedades.getProperty(clave.getKey(), valorPorDefecto);
    }

    public String obtener(ConfigKey clave) {
        return propiedades.getProperty(clave.getKey());
    }

    public String obtenerRaw(String claveString, String valorPorDefecto) {
        return propiedades.getProperty(claveString, valorPorDefecto);
    }

    public int obtenerInt(ConfigKey clave, int valorDefecto) {
        String val = propiedades.getProperty(clave.getKey());
        try { return (val != null) ? Integer.parseInt(val) : valorDefecto; }
        catch (NumberFormatException e) { return valorDefecto; }
    }

    public boolean obtenerBoolean(ConfigKey clave, boolean valorDefecto) {
        String val = propiedades.getProperty(clave.getKey());
        return (val != null) ? Boolean.parseBoolean(val) : valorDefecto;
    }

    public void setProperty(ConfigKey clave, Object valor) {
        propiedades.setProperty(clave.getKey(), String.valueOf(valor).trim());
    }

    // --- GETTERS DE RUTAS CRÍTICAS ---
    public String getSharedDir(){
        return obtener(ConfigKey.SHARED_DIR);
    }

    public String getDownloadDir() {
        return obtener(ConfigKey.DOWNLOAD_DIR);
    }

    public String getRutaArchivoConfig() {
        return rutaArchivoConfig;
    }
}
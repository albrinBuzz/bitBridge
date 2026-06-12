/**
 * Copyright 2026 Cristobal Roman Zamora
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitBridge.shared.config;

import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.Logger;

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
        setProperty(ConfigKey.NIO_THREADS, String.valueOf(Math.max(2, CORES)));

        // --- 3. ALMACENAMIENTO SIMÉTRICO ---
        Path homeUser = Paths.get(System.getProperty("user.home"));
        Path baseBitBridge = homeUser.resolve("BitBridge");
        Path descargas = baseBitBridge.resolve("Downloads");
        Path compartido = baseBitBridge.resolve("Shared");

        setProperty(ConfigKey.DOWNLOAD_DIR, descargas.toString());
        setProperty(ConfigKey.SHARED_DIR, compartido.toString());
        setProperty(ConfigKey.TRANSFER_MAX_ACTIVE, "3");
        setProperty(ConfigKey.TRANSFER_AUTO_RESUME, "true");
        setProperty(ConfigKey.TRANSFER_OVERWRITE, "false");
        setProperty(ConfigKey.TRANSFER_AUTO_ACCEPT, "false");

        // --- 4. CONFIGURACIONES EXCLUSIVAS DE CLIENTE ---
        propiedades.setProperty("cliente.id", "CL-" + (System.currentTimeMillis() % 1000));
        propiedades.setProperty("cliente.intentos_reconexion", "5");

        // --- 5. POOLS DE MEMORIA BAJO NIVEL (NIO) ---
        setProperty(ConfigKey.NIO_BUFFER_SIZE, "32768");
        setProperty(ConfigKey.NIO_ZERO_COPY, "true");
        setProperty(ConfigKey.NIO_DIRECT_BUF, "true");
        setProperty(ConfigKey.NIO_POOL_CAPACITY, "50");

        setProperty(ConfigKey.POOL_MSG_SIZE, "4096");
        setProperty(ConfigKey.POOL_MSG_CAP, "100");

        setProperty(ConfigKey.POOL_DIR_SIZE, "32768");
        setProperty(ConfigKey.POOL_DIR_CAP, "20");

        setProperty(ConfigKey.POOL_TRANS_SIZE, "262144");
        setProperty(ConfigKey.POOL_TRANS_CAP, "10");

        // --- 6. SEGURIDAD, AUTENTICACIÓN Y TLS ---
        setProperty(ConfigKey.AUTH_REQUIRED, "false");
        setProperty(ConfigKey.AUTH_KEY, "");

        // 🔒 Inicialización de entorno criptográfico seguro por defecto

        Path baseConfigDir = Paths.get(System.getProperty("user.home"), ".config", "bitbridge");
        Path defaultKeystorePath = baseConfigDir.resolve("certs").resolve("server.p12");
        Path defaultTofuTrustStore = baseConfigDir.resolve("trusted_servers.properties");
        Path defaultStaticCert = baseConfigDir.resolve("certs").resolve("certificado_vps.crt");

        setProperty(ConfigKey.NET_TLS_ENABLED, "true");
        setProperty(ConfigKey.NET_TLS_VALIDATION_MODE, "TOFU"); // 👈 Estrategia por defecto
        setProperty(ConfigKey.NET_TLS_STATIC_CERT_PATH, defaultStaticCert.toString()); // 👈 Preparado para Opción 2
        setProperty(ConfigKey.NET_TLS_KEYSTORE, defaultKeystorePath.toString());
        setProperty(ConfigKey.NET_TLS_PASSWORD, "bitbridgepass");
        setProperty(ConfigKey.NET_TLS_TRUSTSTORE, defaultTofuTrustStore.toString());


        inicializarEntornoFisico(descargas, compartido);
    }

    private void validarClavesFaltantes() {
        boolean cambio = false;
        Path baseConfigDir = Paths.get(System.getProperty("user.home"), ".config", "bitbridge");

        if (obtener(ConfigKey.SERVER_NAME) == null) { setProperty(ConfigKey.SERVER_NAME, "BB-NODE-" + System.getProperty("user.name").toUpperCase()); cambio = true; }
        if (obtener(ConfigKey.SERVER_PORT) == null) { setProperty(ConfigKey.SERVER_PORT, "8080"); cambio = true; }
        if (obtener(ConfigKey.NET_MAX_CONN) == null) { setProperty(ConfigKey.NET_MAX_CONN, "500"); cambio = true; }
        if (obtener(ConfigKey.NET_BACKLOG) == null) { setProperty(ConfigKey.NET_BACKLOG, "128"); cambio = true; }
        if (obtener(ConfigKey.NET_KEEPALIVE) == null) { setProperty(ConfigKey.NET_KEEPALIVE, "45000"); cambio = true; }
        if (obtener(ConfigKey.NET_NODELAY) == null) { setProperty(ConfigKey.NET_NODELAY, "true"); cambio = true; }
        if (obtener(ConfigKey.NET_ENCRYPTION) == null) { setProperty(ConfigKey.NET_ENCRYPTION, "AES-256-GCM"); cambio = true; }

        if (obtener(ConfigKey.NET_SELECTOR_THREADS) == null) { setProperty(ConfigKey.NET_SELECTOR_THREADS, "1"); cambio = true; }
        if (obtener(ConfigKey.NET_WORKER_THREADS) == null) { setProperty(ConfigKey.NET_WORKER_THREADS, String.valueOf(Math.max(2, CORES))); cambio = true; }
        if (obtener(ConfigKey.NIO_THREADS) == null) { setProperty(ConfigKey.NIO_THREADS, String.valueOf(Math.max(2, CORES))); cambio = true; }

        Path homeUser = Paths.get(System.getProperty("user.home"));
        Path baseBitBridge = homeUser.resolve("BitBridge");
        if (obtener(ConfigKey.DOWNLOAD_DIR) == null) { setProperty(ConfigKey.DOWNLOAD_DIR, baseBitBridge.resolve("Downloads").toString()); cambio = true; }
        if (obtener(ConfigKey.SHARED_DIR) == null) { setProperty(ConfigKey.SHARED_DIR, baseBitBridge.resolve("Shared").toString()); cambio = true; }
        if (obtener(ConfigKey.TRANSFER_MAX_ACTIVE) == null) { setProperty(ConfigKey.TRANSFER_MAX_ACTIVE, "3"); cambio = true; }
        if (obtener(ConfigKey.TRANSFER_AUTO_RESUME) == null) { setProperty(ConfigKey.TRANSFER_AUTO_RESUME, "true"); cambio = true; }
        if (obtener(ConfigKey.TRANSFER_OVERWRITE) == null) { setProperty(ConfigKey.TRANSFER_OVERWRITE, "false"); cambio = true; }
        if (obtener(ConfigKey.TRANSFER_AUTO_ACCEPT) == null) { setProperty(ConfigKey.TRANSFER_AUTO_ACCEPT, "false"); cambio = true; }

        if (propiedades.getProperty("cliente.intentos_reconexion") == null) {
            propiedades.setProperty("cliente.intentos_reconexion", "5");
            cambio = true;
        }

        if (obtener(ConfigKey.NIO_BUFFER_SIZE) == null) { setProperty(ConfigKey.NIO_BUFFER_SIZE, "32768"); cambio = true; }
        if (obtener(ConfigKey.NIO_ZERO_COPY) == null) { setProperty(ConfigKey.NIO_ZERO_COPY, "true"); cambio = true; }
        if (obtener(ConfigKey.NIO_DIRECT_BUF) == null) { setProperty(ConfigKey.NIO_DIRECT_BUF, "true"); cambio = true; }
        if (obtener(ConfigKey.NIO_POOL_CAPACITY) == null) { setProperty(ConfigKey.NIO_POOL_CAPACITY, "50"); cambio = true; }

        if (obtener(ConfigKey.POOL_MSG_SIZE) == null) { setProperty(ConfigKey.POOL_MSG_SIZE, "4096"); cambio = true; }
        if (obtener(ConfigKey.POOL_MSG_CAP) == null) { setProperty(ConfigKey.POOL_MSG_CAP, "100"); cambio = true; }
        if (obtener(ConfigKey.POOL_DIR_SIZE) == null) { setProperty(ConfigKey.POOL_DIR_SIZE, "32768"); cambio = true; }
        if (obtener(ConfigKey.POOL_DIR_CAP) == null) { setProperty(ConfigKey.POOL_DIR_CAP, "20"); cambio = true; }
        if (obtener(ConfigKey.POOL_TRANS_SIZE) == null) { setProperty(ConfigKey.POOL_TRANS_SIZE, "262144"); cambio = true; }
        if (obtener(ConfigKey.POOL_TRANS_CAP) == null) { setProperty(ConfigKey.POOL_TRANS_CAP, "10"); cambio = true; }

        if (obtener(ConfigKey.AUTH_REQUIRED) == null) { setProperty(ConfigKey.AUTH_REQUIRED, "false"); cambio = true; }
        if (obtener(ConfigKey.AUTH_KEY) == null) { setProperty(ConfigKey.AUTH_KEY, ""); cambio = true; }

        // 🔒 Inyección adaptativa de propiedades seguras para migración limpia de nodos antiguos
        if (obtener(ConfigKey.NET_TLS_ENABLED) == null) {
            setProperty(ConfigKey.NET_TLS_ENABLED, "true");
            setProperty(ConfigKey.NET_TLS_KEYSTORE, baseConfigDir.resolve("certs").resolve("server.p12").toString());
            setProperty(ConfigKey.NET_TLS_PASSWORD, "bitbridgepass");
            cambio = true;
        }

        if (obtener(ConfigKey.NET_TLS_VALIDATION_MODE) == null) { // 👈 NUEVO
            setProperty(ConfigKey.NET_TLS_VALIDATION_MODE, "TOFU");
            cambio = true;
        }

        if (obtener(ConfigKey.NET_TLS_STATIC_CERT_PATH) == null) { // 👈 NUEVO
            setProperty(ConfigKey.NET_TLS_STATIC_CERT_PATH, baseConfigDir.resolve("certs").resolve("certificado_vps.crt").toString());
            cambio = true;
        }

        if (obtener(ConfigKey.NET_TLS_TRUSTSTORE) == null) {
            setProperty(ConfigKey.NET_TLS_TRUSTSTORE, baseConfigDir.resolve("trusted_servers.properties").toString());
            cambio = true;
        }

        if (obtener(ConfigKey.NET_TLS_GLOBAL_CA) == null) {
            setProperty(ConfigKey.NET_TLS_GLOBAL_CA, "false");
            cambio = true;
        }

        if (cambio) guardarEnArchivo();
    }

    private void inicializarEntornoFisico(Path d, Path c) {
        try {
            Path baseConfigDir = Paths.get(rutaArchivoConfig).getParent();
            Files.createDirectories(baseConfigDir);
            Files.createDirectories(baseConfigDir.resolve("certs"));
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

    // ==========================================
    // --- 🔥 MÉTODOS DE ACCESO ENUM (TYPED) ---
    // ==========================================

    public String obtener(ConfigKey clave) {
        return propiedades.getProperty(clave.getKey());
    }

    public String obtener(ConfigKey clave, String valorPorDefecto) {
        return propiedades.getProperty(clave.getKey(), valorPorDefecto);
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

    // ==========================================
    // --- 🔓 MÉTODOS DE ACCESO RAW (STRING) ---
    // ==========================================

    public String obtenerRaw(String claveString, String valorPorDefecto) {
        return propiedades.getProperty(claveString, valorPorDefecto);
    }

    // ==========================================
    // --- 🏎️ GETTERS METADATA ESTRUCTURAL ---
    // ==========================================

    public String getSharedDir() { return obtener(ConfigKey.SHARED_DIR); }
    public String getDownloadDir() { return obtener(ConfigKey.DOWNLOAD_DIR); }
    public String getRutaArchivoConfig() { return rutaArchivoConfig; }
}
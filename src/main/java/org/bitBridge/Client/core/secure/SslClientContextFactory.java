package org.bitBridge.Client.core.secure;

import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.secure.TofuTrustManager;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class SslClientContextFactory {

    private static TofuTrustManager activoTofuManager;

    /**
     * Orquestador inteligente de contexto para el cliente BitBridge.
     * Lee la configuración de la App y decide qué estrategia de confianza aplicar.
     */
    public static SSLContext crearContextoCliente(String host, int port) throws Exception {
        ConfiguracionApp config = ConfiguracionApp.getInstancia();

        // 1. Leer flags de configuración (puedes setearlos en tu archivo de propiedades)
        //boolean usoCAsGlobales = config.obtenerBoolean("net.tls.use_global_ca", false);
        //boolean usaCertificadoDescargado = config.obtenerBoolean("net.tls.use_custom_cert", true);
        boolean tlsHabilitado = config.obtenerBoolean(ConfigKey.NET_TLS_ENABLED, false);
        if (!tlsHabilitado) {
            Logger.logWarn("⚠️ [SECURITY] TLS deshabilitado. Cargando canal inseguro (Permisivo).");
            return crearContextoClientePermisivo();
        }

        boolean usoCAsGlobales=false;
        boolean usaCertificadoDescargado=true;
        String modoValidacion = config.obtener(ConfigKey.NET_TLS_VALIDATION_MODE, "TOFU").toUpperCase();


        switch (modoValidacion) {

            case "GLOBAL":
                // 🌍 OPCIÓN 1: Confianza Centralizada Comercial (JRE cacerts)
                Logger.logInfo("🔒 [SECURITY] Modo: GLOBAL. Usando almacén de confianza nativo del JRE (Let's Encrypt / CAs Oficiales).");
                return crearContextoProduccionGlobal();

            case "STATIC":
                // 📌 OPCIÓN 2: Confianza Estática Privada (Archivo .crt específico)
                String rutaCertEstatico = config.obtener(ConfigKey.NET_TLS_STATIC_CERT_PATH);
                if (rutaCertEstatico == null || rutaCertEstatico.isEmpty()) {
                    throw new IllegalStateException("El modo TLS 'STATIC' requiere una ruta válida en net.tls.static_cert_path");
                }
                Logger.logInfo("🔒 [SECURITY] Modo: STATIC. Cargando certificado fijo desde: " + rutaCertEstatico);
                return crearContextoDesdeCertificadoPlano(rutaCertEstatico);

            case "TOFU":
                // 🔄 OPCIÓN 3: Confianza Dinámica Descentralizada (Trust On First Use)
                String rutaTofu = config.obtener(ConfigKey.NET_TLS_TRUSTSTORE);
                Logger.logInfo("🔒 [SECURITY] Modo: TOFU. Validando dinámicamente contra: " + rutaTofu);
                return crearContextoTofu(rutaTofu,host, port);

            default:
                Logger.logError("❌ [SECURITY] Modo de validación desconocido: " + modoValidacion + ". Aplicando TOFU por seguridad.");
                return crearContextoTofu(config.obtener(ConfigKey.NET_TLS_TRUSTSTORE),host, port);
        }

        // CASO 1: Certificación Profesional (Let's Encrypt / CAs Oficiales)
        /*if (usoCAsGlobales) {
            Logger.logInfo("🔒 [SECURITY] Iniciando canal TLS v1.3 usando el almacén de confianza nativo del JRE.");
            return crearContextoProduccionGlobal();
        }

        // CASO 2: Certificado privado bajado de internet (Tu VPS con IP)
        if (usaCertificadoDescargado) {
            //String rutaTrust = config.obtenerString("net.tls.truststore_path", "truststore_cliente.jks");
            //String passTrust = config.obtenerString("net.tls.truststore_password", "passwordCliente123");
            String rutaTrust ="/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/certificado_beta.crt";
            //String rutaTrust ="/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/bitDrige_certificado.crt";

            //String rutaTrust ="/home/cris/.config/bitbridge";
            String passTrust="betaPassword999";
            //String passTrust = config.obtenerString("net.tls.truststore_password", "passwordCliente123");


            Logger.logInfo("🔒 [SECURITY] Cargando almacén de confianza privado desde: " + rutaTrust);

            // DETECCIÓN INTELIGENTE: Si el archivo es un certificado plano (.crt o .pem)
            if (rutaTrust.endsWith(".crt") || rutaTrust.endsWith(".pem")) {
                return crearContextoDesdeCertificadoPlano(rutaTrust);
            } else {
                // Si es un contenedor estructurado (.jks o .p12)
                return createSSLContext(rutaTrust, passTrust);
            }

            //return createSSLContext(rutaTrust, passTrust);
        }

        // CASO 3: Fallback de desarrollo (Última opción si no hay nada configurado)
        Logger.logWarn("⚠️ [SECURITY] Alerta: No se detectó configuración segura. Cargando TrustManager permisivo.");
        return crearContextoClientePermisivo();*/
    }

    /**
     * Caso Profesional: Utiliza el paraguas de confianza de Java (cacerts).
     * Ideal para Let's Encrypt o entidades de certificación compradas.
     */
    public static SSLContext crearContextoProduccionGlobal() throws Exception {
        return SSLContext.getDefault();
    }

    /**
     * Caso Intermedio/Híbrido: Carga un archivo JKS o PKCS12 específico
     * que contiene únicamente las llaves públicas del VPS.
     */
    public static SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        // Detectar automáticamente el tipo de almacén por extensión para dar soporte a JKS y PKCS12 (.p12)
        String tipoAlmacen = keystorePath.endsWith(".p12") || keystorePath.endsWith(".pkcs12") ? "PKCS12" : "JKS";
        KeyStore keyStore = KeyStore.getInstance(tipoAlmacen);

        try (FileInputStream fp = new FileInputStream(keystorePath)) {
            keyStore.load(fp, password.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        // Al ser cliente puro, pasamos null en el KeyManager (no necesitamos enviar llaves privadas al VPS)
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext;
    }

    /**
     * NUEVO MÉTODO: Permite pasar directamente un archivo plano de certificado (.crt / .pem)
     * bajado de internet sin tener que usar comandos de consola keytool.
     * ¡Ideal para automatizar la CLI de usuarios no técnicos!
     */

    public static SSLContext crearContextoDesdeCertificadoPlano(String rutaCertificadoCrt) throws Exception {
        try (InputStream fis = new FileInputStream(rutaCertificadoCrt)) {
            // 1. Carga el certificado desde el archivo (.crt)
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificado = (X509Certificate) cf.generateCertificate(fis);

            // 2. Crea un almacén de confianza (TrustStore) exclusivo en RAM
            KeyStore ramTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            ramTrustStore.load(null, null);
            ramTrustStore.setCertificateEntry("bitbridge-server", certificado);

            // 3. Inicializa el gestor de confianza SOLO con este certificado
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ramTrustStore);

            // 4. El contexto TLS resultante SOLO confiará en lo que le dimos
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
            return sslContext;
        }
    }

    private static SSLContext crearContextoClientePermisivo() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }

    private static SSLContext crearContextoTofu(String rutaPersistenciaTofu,String host, int port) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        TrustManager[] tofuManagers = new TrustManager[]{
                new TofuTrustManager(rutaPersistenciaTofu,host,port)
        };
        sslContext.init(null, tofuManagers, new java.security.SecureRandom());
        return sslContext;
    }

    public static TofuTrustManager getActivoTofuManager() {
        return activoTofuManager;
    }
}
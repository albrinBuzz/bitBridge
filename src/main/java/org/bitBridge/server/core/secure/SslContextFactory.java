package org.bitBridge.server.core.secure;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import org.bitBridge.shared.Logger;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.File;
import java.security.KeyStore;

public class SslContextFactory {
    /**
     * Levanta el contexto TLS v1.3 para el servidor BitBridge.
     * Soporta tanto certificados de desarrollo (Autofirmados) como de producción (Let's Encrypt).
     */
    public static SSLContext crearContextoServidor(String keystorePath, String password) throws Exception {
        Logger.logInfo("Seteando contexto del servidor");
        if (keystorePath == null || keystorePath.isEmpty()) {
            throw new IllegalArgumentException("La ruta del almacén de llaves (KeyStore) del servidor no puede estar vacía.");
        }

        // 1. Detectar automáticamente el tipo de almacén (JKS o PKCS12) según la extensión del archivo
        // Let's Encrypt y los estándares modernos usan PKCS12 (.p12 / .pfx), keytool viejo usa JKS
        String tipoAlmacen = keystorePath.endsWith(".jks") ? "JKS" : "PKCS12";

        File archivoKeystore = new File(keystorePath);
        if (!archivoKeystore.exists()) {
            throw new java.io.FileNotFoundException("No se encontró el almacén del servidor en: " + archivoKeystore.getAbsolutePath());
        }

        Logger.logInfo("🔒 [SERVER-TLS] Cargando identidad del servidor [" + tipoAlmacen + "] desde: " + keystorePath);

        KeyStore keyStore = KeyStore.getInstance(tipoAlmacen);
        try (FileInputStream is = new FileInputStream(archivoKeystore)) {
            keyStore.load(is, password.toCharArray());
        }

        // 2. Inicializar el Gestor de Claves Privadas (Identidad del Servidor)
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        // 3. Configurar el contexto TLSv1.3 puro
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");

        // Al ser servidor puro, pasamos sus KeyManagers para que firme, y null en TrustManagers
        // (a menos que pidas autenticación mutua, el servidor no necesita validar los certificados del cliente)
        sslContext.init(kmf.getKeyManagers(), null, new java.security.SecureRandom());

        Logger.logInfo("-> [SERVER-TLS] Contexto criptográfico TLSv1.3 inicializado con éxito.");
        return sslContext;
    }

}
package org.bitBridge.Tests.tls.hibrid;

import org.bitBridge.shared.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class SecurityUtils {

    /**
     * Crea un SSLContext para el Servidor cargando un almacén de claves privadas (Keystore PKCS12).
     * Requerido por el Servidor para demostrar su identidad (contiene su clave privada y certificado).
     */
    public static SSLContext createSSLContext(String path, String password) throws Exception {
        Logger.logInfo("🔒 [SECURITY] Cargando KeyStore del servidor desde: " + path);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(path)) {
            keyStore.load(fis, password.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    /**
     * Crea un SSLContext para el Cliente inyectando un archivo de certificado plano (.crt o .pem)
     * directamente en un TrustStore volátil en la memoria RAM.
     * Evita tener que usar archivos .p12 o JKS pesados en el lado del cliente.
     */
    public static SSLContext crearContextoDesdeCertificadoPlano(String rutaCertificadoCrt) throws Exception {
        Logger.logInfo("🔒 [SECURITY] Inyectando certificado plano .crt directamente en la RAM del cliente.");

        try (InputStream fis = new FileInputStream(rutaCertificadoCrt)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificado = (X509Certificate) cf.generateCertificate(fis);

            // Crear un contenedor de confianza limpio (TrustStore) directamente en memoria RAM
            KeyStore ramTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            ramTrustStore.load(null, null);
            ramTrustStore.setCertificateEntry("vps-custom-entry", certificado);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ramTrustStore);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        }
    }
}
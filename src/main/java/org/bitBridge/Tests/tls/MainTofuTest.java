package org.bitBridge.Tests.tls;


import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.secure.TofuTrustManager;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;

public class MainTofuTest {

    private static final String FINGERPRINT_FILE = "./test_fingerprint.txt";
    //private static final String RUTA_CERT = "/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/certificado_beta.crt";
    //private static final String RUTA_CERT = "./build/test_certs/certificado_beta.crt";

    private static final String RUTA_CERT = "./build/test_certs/certificado_beta.crt";


    public static void main(String[] args) {
        // Limpiamos test previo
        new File(FINGERPRINT_FILE).delete();

        try {
            Logger.logInfo("🧪 [TEST] Iniciando simulación TOFU...");

            // 1. CARGAR EL CERTIFICADO REAL
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certServidor;

            try (FileInputStream fis = new FileInputStream(RUTA_CERT)) {
                certServidor = (X509Certificate) cf.generateCertificate(fis);
            }

            TofuTrustManager tofu = new TofuTrustManager(FINGERPRINT_FILE,"localhost",8080);

            Logger.logInfo("--- PASO 1: Primera conexión (Aprendizaje) ---");
            tofu.checkServerTrusted(new X509Certificate[]{ certServidor }, "RSA");

            Logger.logInfo("--- PASO 2: Segunda conexión (Validación) ---");
            tofu.checkServerTrusted(new X509Certificate[]{ certServidor }, "RSA");

            Logger.logInfo("✅ Test completado: El TOFU funciona correctamente.");

        } catch (Exception e) {
            Logger.logError("❌ Fallo en el test TOFU: " + e.getMessage());
        }
    }
}
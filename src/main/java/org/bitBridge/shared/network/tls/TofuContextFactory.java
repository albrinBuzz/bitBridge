package org.bitBridge.shared.network.tls;


import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.SecureRandom;

public class TofuContextFactory {

    public static SSLContext crearContextoTofu(String configDir, String host, int port) throws Exception {
        // 1. Inicializar el almacenamiento de hosts conocidos
        TofuStorage storage = new TofuStorage(configDir);

        // 2. Instanciar el TrustManager dedicado para este socket específico
        TrustManager[] tofuManagers = new TrustManager[] {
                new BitBridgeTofuTrustManager(storage, host, port)
        };

        // 3. Inicializar el contexto criptográfico TLS v1.3
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");

        // Pasamos 'null' en KeyManager (el cliente no necesita certificado propio)
        // Pasamos nuestro gestor TOFU personalizado
        sslContext.init(null, tofuManagers, new SecureRandom());

        return sslContext;
    }
}
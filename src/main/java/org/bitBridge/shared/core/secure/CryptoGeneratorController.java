package org.bitBridge.shared.core.secure;


import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.secure.CryptoConfig;

import java.io.File;
import java.util.List;

public class CryptoGeneratorController {

    /**
     * Interfaz de callback para notificar los eventos de la generación
     * sin importar si el destino es una consola de texto o un JTextArea.
     */
    public interface CryptoExecutionListener {
        void onProgress(String message);
        void onSuccess(String keystorePath, String certPath);
        void onError(String errorMessage, Throwable cause);
    }

    /**
     * Método central de ejecución. Diseñado para ser agnóstico a la vista.
     */
    public void procesarGeneracion(String carpetaBase, String password, String commonName,
                                   String organizacion, int validezDias,
                                   List<String> ipsYDominios, CryptoExecutionListener listener) {

        // Validaciones previas de negocio
        if (carpetaBase == null || carpetaBase.trim().isEmpty() ||
                password == null || password.trim().isEmpty() ||
                commonName == null || commonName.trim().isEmpty()) {
            listener.onError("Campos obligatorios inválidos o vacíos.", null);
            return;
        }

        String rutaP12 = carpetaBase + File.separator + "keystore_beta.p12";
        String rutaCrt = carpetaBase + File.separator + "certificado_beta.crt";

        listener.onProgress("> Inicializando CryptoConfig dinámico...");

        CryptoConfig config = new CryptoConfig(commonName, organizacion, "CL", validezDias);
        if (ipsYDominios != null) {
            ipsYDominios.forEach(config::agregarDominioODns);
        }

        try {
            listener.onProgress("> Solicitando generación al motor criptográfico (BouncyCastle)...");

            // Invocación al motor de producción (WAN/LAN)
            BitBridgeCryptoEngine.generarEntornoSeguroProduction(rutaP12, rutaCrt, password, config);

            listener.onSuccess(rutaP12, rutaCrt);
        } catch (Exception e) {
            listener.onError(e.getMessage(), e);
        }
    }
}
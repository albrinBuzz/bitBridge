package org.bitBridge.shared.network.tls;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class BitBridgeTofuTrustManager implements X509TrustManager {
    private final TofuStorage storage;
    private final String targetHost;
    private final int targetPort;

    public BitBridgeTofuTrustManager(TofuStorage storage, String targetHost, int targetPort) {
        this.storage = storage;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // El cliente no necesita validar certificados de otros clientes en este flujo inbound.
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("❌ La cadena de certificados del servidor está vacía.");
        }

        try {
            X509Certificate serverCert = chain[0];
            String huellaCalculada = TofuStorage.calcularHuella(serverCert);
            String hostKey = targetHost + ":" + targetPort;

            String huellaRegistrada = storage.getFingerprint(hostKey);

            if (huellaRegistrada == null) {
                // ─── FASE 1: PRIMERA CONEXIÓN (CONFÍAR EN PRIMER USO) ───
                System.out.println("⚠️ [TOFU] Nodo desconocido detectado: " + hostKey);
                System.out.println("⚠️ Guardando huella digital en almacenamiento seguro local...");

                storage.saveHost(hostKey, huellaCalculada);

                System.out.println("✅ [TOFU] Confianza establecida de forma atómica para: " + hostKey);
            } else {
                // ─── FASE 2: CONEXIONES SUCESIVAS (VALIDACIÓN ESTRICTA) ───
                if (!huellaRegistrada.equalsIgnoreCase(huellaCalculada)) {
                    System.err.println("🚨 🚨 🚨 [ALERTA CRÍTICA DE MITM] 🚨 🚨 🚨");
                    System.err.println("¡LA HUELLA DIGITAL DEL SERVIDOR HA CAMBIADO!");
                    System.err.println("Host: " + hostKey);
                    System.err.println("Huella Registrada: " + huellaRegistrada);
                    System.err.println("Huella Recibida:   " + huellaCalculada);

                    throw new CertificateException("Posible ataque de suplantación de identidad (MITM) detectado en la IP local.");
                }
                // Si coinciden, la conexión continúa limpiamente bajo TLS 1.3
            }
        } catch (CertificateException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateException("Error crítico procesando lógica TOFU: " + e.getMessage(), e);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
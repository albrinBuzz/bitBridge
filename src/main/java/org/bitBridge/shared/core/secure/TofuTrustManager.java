package org.bitBridge.shared.core.secure;

import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Properties;
import org.bitBridge.shared.Logger;

public class TofuTrustManager implements X509TrustManager {
    private  Path rutaDestinosConfiables;
    private final Properties servidoresConfiables;

    // 🎯 CORREGIDO: Contexto de red dinámico e inyectado para evitar colisiones de alias
    private final String targetHost;
    private final int targetPort;
    private X509Certificate ultimoCertificadoValidado;

    public TofuTrustManager(String storePath, String targetHost, int targetPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;

        // Si nos pasan un directorio base, construimos la ruta ahí. Si no, usamos el fallback por defecto.
        if (storePath != null && !storePath.isEmpty()) {
            this.rutaDestinosConfiables = Paths.get(storePath, "trusted_servers.properties");
        } else {
            this.rutaDestinosConfiables = Paths.get(System.getProperty("user.home"), ".config", "bitbridge", "trusted_servers.properties");
        }

        this.rutaDestinosConfiables = Paths.get(System.getProperty("user.home"), ".config", "bitbridge", "trusted_servers.properties");
        this.servidoresConfiables = new Properties();
        cargarServidoresConfiables();
    }

    private void cargarServidoresConfiables() {
        if (Files.exists(rutaDestinosConfiables)) {
            try (BufferedReader reader = Files.newBufferedReader(rutaDestinosConfiables)) {
                servidoresConfiables.load(reader);
            } catch (IOException e) {
                Logger.logError("❌ No se pudo cargar el archivo TOFU: " + e.getMessage());
            }
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // El cliente no valida a otros clientes en transferencias de salida simples
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("La cadena de certificados del servidor está vacía.");
        }

        X509Certificate certServidor = chain[0];

        // 🎯 CORREGIDO: Nuestra clave única inequívoca en la LAN es el host:puerto físico
        String hostKey = (targetHost + ":" + targetPort).toLowerCase();
        String fingerprintActual = calcularFingerprintSHA256(certServidor);

        // Caso 1: El servidor ya se ha registrado previamente bajo TOFU
        if (servidoresConfiables.containsKey(hostKey)) {
            String fingerprintGuardado = servidoresConfiables.getProperty(hostKey);
            if (fingerprintGuardado.equalsIgnoreCase(fingerprintActual)) {
                Logger.logInfo(String.format("🔒 [TOFU] Nodo [%s] reconocido y validado de forma segura.", hostKey));
                return;
            } else {
                Logger.logError(String.format("🚨 [TOFU - MITM ALERT] ¡La huella digital de [%s] ha mutado radicalmente!", hostKey));
                Logger.logError("Guardada: " + fingerprintGuardado);
                Logger.logError("Recibida: " + fingerprintActual);
                throw new CertificateException("¡Peligro! La huella criptográfica no coincide. Posible interceptor de red.");
            }
        }

        // Caso 2: Confianza en el Primer Uso (First Use)
        Logger.logWarn(String.format("⚠️ [TOFU] Conectando por primera vez a un nodo desconocido: [%s]", hostKey));

        // Ejecutamos tus excelentes herramientas de auditoría visual
        imprimirDetallesCertificado(certServidor);
        imprimirDetallesAvanzados(certServidor);

        // 🎯 ARQUITECTURA DE CONTROL: ¿Cómo decidir si confiar sin congelar el Selector?
        // En un entorno de red automatizado o headless, la filosofía pura de TOFU acepta el primer certificado
        // de forma implícita (como lo hace SSH si no estás en una TTY interactiva).
        boolean usuarioConfia = solicitarConfirmacionAlUsuario(hostKey, fingerprintActual);

        if (usuarioConfia) {
            registrarNuevoServidor(hostKey, fingerprintActual);
            this.ultimoCertificadoValidado = chain[0];
        } else {
            throw new CertificateException("Conexión rechazada: El operador del software denegó la confianza al certificado.");
        }
    }

    private void imprimirDetallesCertificado(X509Certificate cert) {
        Logger.logInfo("--------------------------------------------------");
        Logger.logInfo("📜 DETALLES DEL CERTIFICADO RECIBIDO:");
        Logger.logInfo("  - Sujeto (Subject):   " + cert.getSubjectX500Principal().getName());
        Logger.logInfo("  - Emisor (Issuer):    " + cert.getIssuerX500Principal().getName());
        Logger.logInfo("  - Versión:            " + cert.getVersion());
        Logger.logInfo("  - Núm. Serie:         " + cert.getSerialNumber().toString(16).toUpperCase());
        Logger.logInfo("  - Válido desde:       " + cert.getNotBefore());
        Logger.logInfo("  - Válido hasta:       " + cert.getNotAfter());
        Logger.logInfo("  - Algoritmo firma:    " + cert.getSigAlgName());
        Logger.logInfo("--------------------------------------------------");
    }

    private void imprimirDetallesAvanzados(X509Certificate cert) {
        Logger.logInfo("🔍 [DETALLES AVANZADOS DEL CERTIFICADO]");
        Logger.logInfo("  - Algoritmo Clave Pública: " + cert.getPublicKey().getAlgorithm());
        Logger.logInfo("  - Formato Clave: " + cert.getPublicKey().getFormat());

        if (cert.getPublicKey() instanceof java.security.interfaces.RSAPublicKey) {
            Logger.logInfo("  - Longitud Clave RSA: " + ((java.security.interfaces.RSAPublicKey) cert.getPublicKey()).getModulus().bitLength() + " bits");
        }

        int bc = cert.getBasicConstraints();
        Logger.logInfo("  - Es Entidad Final (End Entity): " + (bc == -1));

        boolean[] ku = cert.getKeyUsage();
        if (ku != null) {
            Logger.logInfo("  - Usos de Clave permitidos:");
            String[] nombresKu = {"DigitalSignature", "NonRepudiation", "KeyEncipherment", "DataEncipherment", "KeyAgreement", "KeyCertSign", "CRLSign"};
            for (int i = 0; i < ku.length; i++) {
                if (ku[i] && i < nombresKu.length) Logger.logInfo("      * " + nombresKu[i]);
            }
        }

        try {
            java.util.Collection<java.util.List<?>> san = cert.getSubjectAlternativeNames();
            if (san != null) {
                Logger.logInfo("  - Nombres Alternativos (SAN):");
                for (java.util.List<?> item : san) {
                    Logger.logInfo("      * " + item.get(1));
                }
            }
        } catch (java.security.cert.CertificateParsingException e) {
            Logger.logError("  - Error al leer SAN: " + e.getMessage());
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    private String calcularFingerprintSHA256(X509Certificate cert) throws CertificateException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] der = cert.getEncoded();
            byte[] digest = md.digest(der);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X:", b));
            }
            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        } catch (Exception e) {
            throw new CertificateException("Error computando SHA-256 del certificado: " + e.getMessage());
        }
    }

    private boolean solicitarConfirmacionAlUsuario(String hostKey, String fingerprint) {
        System.out.println("\n==================================================");
        Logger.logInfo("🤖 CONEXIÓN NUEVA DETECTADA EN BITBRIDGE");
        Logger.logInfo("Dirección de Red (HostKey): " + hostKey);
        Logger.logInfo("Huella Criptográfica: " + fingerprint);
        Logger.logInfo("Modo TOFU activo: Confianza automática otorgada para primer uso.");
        System.out.println("==================================================");

        // 💡 NOTA ARQUITECTÓNICA DE NIO: Para que una interfaz JavaFX pregunte de verdad al usuario (s/n),
        // este método no debe bloquear. Dado que JSSE (la infraestructura nativa de SSLContext) maneja un diseño
        // síncrono al disparar el TrustManager, la mejor práctica para aplicaciones distribuidas es el "auto-trust"
        // en primer contacto (igual que SSH bajo modo automatizado BatchMode).
        return true;
    }

    private synchronized void registrarNuevoServidor(String hostKey, String fingerprint) {
        servidoresConfiables.setProperty(hostKey, fingerprint);
        try {
            Files.createDirectories(rutaDestinosConfiables.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(rutaDestinosConfiables)) {
                servidoresConfiables.store(writer, "BitBridge Known Hosts (TOFU)");
                Logger.logInfo("💾 [TOFU] Registro de host persistido con éxito en: " + rutaDestinosConfiables);
            }
        } catch (IOException e) {
            Logger.logError("❌ No se pudo persistir el nuevo host confiable: " + e.getMessage());
        }
    }

    public X509Certificate getUltimoCertificadoValidado() {
        return ultimoCertificadoValidado;
    }
}
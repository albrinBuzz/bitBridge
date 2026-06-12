package org.bitBridge.shared.network.tls;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Properties;

public class TofuStorage {
    private final Path storagePath;
    private final Properties knownHosts = new Properties();

    public TofuStorage(String basePath) {
        this.storagePath = Paths.get(basePath, "bitbridge_known_hosts.properties");
        cargarHosts();
    }

    private synchronized void cargarHosts() {
        if (Files.exists(storagePath)) {
            try (InputStream is = Files.newInputStream(storagePath)) {
                knownHosts.load(is);
            } catch (IOException e) {
                System.err.println("❌ Error cargando archivo TOFU: " + e.getMessage());
            }
        }
    }

    public synchronized String getFingerprint(String hostKey) {
        return knownHosts.getProperty(hostKey);
    }

    public synchronized void saveHost(String hostKey, String fingerprint) throws IOException {
        knownHosts.setProperty(hostKey, fingerprint);
        try (OutputStream os = Files.newOutputStream(storagePath)) {
            knownHosts.store(os, "BitBridge TOFU - Known Hosts");
        }
    }

    // Calcula el Hash SHA-256 del certificado para usarlo como huella única
    public static String calcularHuella(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] der = cert.getEncoded();
        byte[] digest = md.digest(der);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
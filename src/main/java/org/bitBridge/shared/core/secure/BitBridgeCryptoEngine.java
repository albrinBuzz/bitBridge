package org.bitBridge.shared.core.secure;

import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.secure.CryptoConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

public class BitBridgeCryptoEngine {

    static {
        // Asegurar la inicialización estática del proveedor criptográfico BouncyCastle
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    /**
     * Genera dinámicamente un par de claves y un certificado autofirmado compatible con TLS 1.3
     * apto tanto para direccionamiento local (LAN/Loopback) como entornos WAN (Internet).
     */
    public static void generarEntornoSeguroProduction(String rutaKeystore, String rutaCertCrt, String password, CryptoConfig config) throws Exception {
        Logger.logInfo("🔒 [CRYPTO-ENGINE] Iniciando generación de infraestructura criptográfica asíncrona...");

        // 1. Validar y preparar estructura de directorios en disco
        File fileP12 = new File(rutaKeystore);
        if (fileP12.getParentFile() != null) {
            fileP12.getParentFile().mkdirs();
        }

        // 2. Generación del par de claves RSA de nivel industrial (2048 bits mínimo para TLS 1.3)
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair parLlaves = keyPairGenerator.generateKeyPair();

        // 3. Ventana Temporal de Validez Dinámica
        long ahora = System.currentTimeMillis();
        Date validoDesde = new Date(ahora - (10 * 60 * 1000)); // 10 minutos de gracia por desajustes de relojes
        Date validoHasta = new Date(ahora + (config.getDiasValidez() * 24L * 60 * 60 * 1000));

        X500Name emisorYSujeto = new X500Name(config.getX500PrincipalString());
        BigInteger numeroSerie = BigInteger.valueOf(ahora);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                emisorYSujeto, numeroSerie, validoDesde, validoHasta, emisorYSujeto, parLlaves.getPublic()
        );

        // Restricciones Básicas básicas de TLS: El certificado identifica un nodo terminal, no una CA jerárquica
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        // 4. Población Dinámica de Nombres Alternativos del Sujeto (SAN) para Entornos LAN e Internet
        Set<GeneralName> sanNombres = new LinkedHashSet<>();

        // Reglas Base para desarrollo local
        sanNombres.add(new GeneralName(GeneralName.dNSName, "localhost"));
        sanNombres.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));

        // Descubrimiento Dinámico de interfaces de red físicas internas (LAN)
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> direcciones = iface.getInetAddresses();
                while (direcciones.hasMoreElements()) {
                    InetAddress addr = direcciones.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        sanNombres.add(new GeneralName(GeneralName.iPAddress, addr.getHostAddress()));
                    }
                }
            }
        } catch (Exception e) {
            Logger.logWarn("⚠️ [CRYPTO] Advertencia al mapear interfaces de red LAN locales: " + e.getMessage());
        }

        // Descubrimiento de la IP WAN Pública (Internet) mediante resolución externa balanceada
        String ipPublica = resolverIpPublicaWan();
        if (ipPublica != null) {
            Logger.logInfo("🌐 [WAN DETECTED] Registrando IP Pública en extensión SAN: " + ipPublica);
            sanNombres.add(new GeneralName(GeneralName.iPAddress, ipPublica));
        }

        // Inyección de parámetros personalizados externos (Dominios DDNS, Cloudflare, IPs WAN Fijas añadidas desde GUI)
        for (String dnsOrIp : config.getDominiosAdicionales()) {
            if (validarSiEsIp(dnsOrIp)) {
                sanNombres.add(new GeneralName(GeneralName.iPAddress, dnsOrIp));
            } else {
                sanNombres.add(new GeneralName(GeneralName.dNSName, dnsOrIp));
            }
        }

        GeneralNames subjectAlternativeNames = new GeneralNames(sanNombres.toArray(new GeneralName[0]));
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNames);

        // 5. Firma Digital con algoritmo fuerte compatible con TLS 1.3 (SHA384 con RSA)
        ContentSigner firmador = new JcaContentSignerBuilder("SHA384withRSA").setProvider("BC").build(parLlaves.getPrivate());
        X509Certificate certificadoFinal = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(firmador));

        // 6. Almacenamiento Estricto A: Keystore PKCS12 Seguro para el Servidor BitBridge
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);
        keyStore.setKeyEntry("bitbridge", parLlaves.getPrivate(), password.toCharArray(), new X509Certificate[]{certificadoFinal});

        try (FileOutputStream fos = new FileOutputStream(fileP12)) {
            keyStore.store(fos, password.toCharArray());
        }
        Logger.logInfo("💾 [KEYSTORE SAVED] Contenedor PKCS12 seguro: " + fileP12.getAbsolutePath());

        // 7. Almacenamiento Estricto B: Exportación de Certificado Público (.crt) en formato PEM estándar
        File fileCrt = new File(rutaCertCrt);
        try (FileOutputStream fos = new FileOutputStream(fileCrt)) {
            fos.write("-----BEGIN CERTIFICATE-----\n".getBytes());
            byte[] certBytesBase64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encode(certificadoFinal.getEncoded());
            fos.write(certBytesBase64);
            fos.write("\n-----END CERTIFICATE-----\n".getBytes());
        }
        Logger.logInfo("💾 [CERT EXPORTED] Certificado público plano (.crt): " + fileCrt.getAbsolutePath());
    }

    /**
     * Consulta servicios REST externos para derivar la dirección IP WAN de la red actual.
     * Implementa timeouts rigurosos para evitar congelar los hilos de ejecución de BitBridge en modo offline.
     */
    private static String resolverIpPublicaWan() {
        String[] servicios = {"https://api.ipify.org", "https://checkip.amazonaws.com", "https://ifconfig.me/ip"};
        for (String servicio : servicios) {
            try {
                URL url = new URL(servicio);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000); // 3 segundos máximo por intento
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String ip = br.readLine().trim();
                        if (validarSiEsIp(ip)) {
                            return ip;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Falla silenciosa: si un servicio está caído o no hay Internet, pasa al siguiente fallback
            }
        }
        Logger.logWarn("⚠️ [WAN CRYPTO] No se pudo determinar una IP Pública externa. Operando exclusivamente en modo LAN.");
        return null;
    }

    private static boolean validarSiEsIp(String entrada) {
        if (entrada == null) return false;
        // Expresión regular simplificada para IPv4
        String regexIPv4 = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$";
        return entrada.matches(regexIPv4);
    }
}
/**
 * Copyright 2026 Cristobal Roman Zamora
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitBridge.Tests.tls;

import org.bitBridge.Client.core.secure.SslClientContextFactory;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;
import org.bitBridge.shared.core.secure.TofuTrustManager;
import org.bitBridge.shared.network.ProtocolService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

public class TestSecurityProtocol {

    public static void main(String[] args) {
        Logger.logInfo("🧪 Iniciando test de subsistema de seguridad y red compatible con TOFU...");

        try {
            // Definir rutas de pruebas controladas en el directorio de ejecución
            String rutaKeystore = "./build/test_certs/keystore_beta.p12";
            String rutaCertPlano = "./build/test_certs/certificado_beta.crt";
            String passwordTest = "123";

            // Directorio temporal para simular el almacenamiento .properties de TOFU
            String directorioTofuTest = "./build/test_certs";

            // 1. Ejecutar Generación Dinámica y Exportación (Servidor)
            generarYExportarKeystoreAutoFirmado(rutaKeystore, rutaCertPlano, passwordTest);

            // 2. Test de Carga de Contexto TLS (Modo TOFU) compatible con la nueva firma
            testCargaContextoTofu(directorioTofuTest, "localhost", 8080);

            // 3. Test de Serialización del Buffer (Garantizar que no rompa NIO)
            testSerializacionBuffer();

            Logger.logInfo("✅ Todos los tests pasaron exitosamente.");
        } catch (Exception e) {
            Logger.logError("❌ Fallo en los tests: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test 2: Simula la inicialización exacta que ejecutará SslClientContextFactory
     * inyectando el host y puerto dinámicos en el TofuTrustManager.
     */
    private static void testCargaContextoTofu(String carpetaTofu, String hostSimulado, int puertoSimulado) throws Exception {
        Logger.logInfo("--- Test 2: Inicialización de Contexto Dinámico (Modo TOFU) ---");

        // Creamos de forma programática el SSLContext inyectando las dependencias que pide TofuTrustManager
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");

        // Instanciamos tu clase TofuTrustManager pasándole el path base, el host y el puerto
        TrustManager[] tofuManagers = new TrustManager[]{
                new TofuTrustManager(carpetaTofu, hostSimulado, puertoSimulado)
        };

        sslContext.init(null, tofuManagers, new java.security.SecureRandom());

        if (sslContext != null) {
            Logger.logInfo(String.format("Resultado: SSLContext (TOFU) inicializado para [%s:%d] con protocolo: %s",
                    hostSimulado, puertoSimulado, sslContext.getProtocol()));
        }
    }

    private static void testSerializacionBuffer() throws Exception {
        Logger.logInfo("--- Test 3: Serialización Segura (Fix UnsupportedOperation) ---");

        Mensaje msg = new Mensaje("Test de integridad de protocolo");
        ByteBuffer buffer = ProtocolService.toNioBuffer(msg, 50);

        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        Logger.logInfo("Bytes extraídos con éxito: " + data.length + " bytes.");

        org.bitBridge.shared.core.comunication.Communication comm = ProtocolService.fromBytes(data);
        if (comm instanceof Mensaje) {
            Logger.logInfo("Resultado: Deserialización correcta. Mensaje: " + ((Mensaje) comm).getContenido());
        }
    }

    /**
     * Genera el par de claves, empaqueta el PKCS12 para el Servidor y exporta el .crt limpio.
     */
    public static void generarYExportarKeystoreAutoFirmado(String rutaDestinoP12, String rutaDestinoCrt, String password) throws Exception {
        Logger.logInfo("--- Test 1: Generación Criptográfica Autonóma ---");

        File fileP12 = new File(rutaDestinoP12);
        if (fileP12.getParentFile() != null) {
            fileP12.getParentFile().mkdirs();
        }

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair parLlaves = keyPairGenerator.generateKeyPair();

        long ahora = System.currentTimeMillis();
        Date validoDesde = new Date(ahora);
        Date validoHasta = new Date(ahora + (365L * 24 * 60 * 60 * 1000)); // 1 año

        X500Name nombreIdentidad = new X500Name("CN=BitBridgeLocalNode, O=BitBridgeBeta, C=CL");
        BigInteger numeroSerie = BigInteger.valueOf(ahora);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                nombreIdentidad, numeroSerie, validoDesde, validoHasta, nombreIdentidad, parLlaves.getPublic()
        );

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        // Inyección dinámica de interfaces de red locales para poblar la extensión SAN
        List<GeneralName> nombresAlternativos = new ArrayList<>();
        nombresAlternativos.add(new GeneralName(GeneralName.dNSName, "localhost"));
        nombresAlternativos.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp()) continue;

            Enumeration<InetAddress> direcciones = iface.getInetAddresses();
            while (direcciones.hasMoreElements()) {
                InetAddress addr = direcciones.nextElement();
                if (addr instanceof java.net.Inet4Address) {
                    nombresAlternativos.add(new GeneralName(GeneralName.iPAddress, addr.getHostAddress()));
                }
            }
        }

        GeneralNames subjectAlternativeNames = new GeneralNames(nombresAlternativos.toArray(new GeneralName[0]));
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNames);

        ContentSigner firmador = new JcaContentSignerBuilder("SHA384withRSA").setProvider("BC").build(parLlaves.getPrivate());
        X509Certificate certificadoFinal = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(firmador));

        // A: Guardar el contenedor binario PKCS12 (Uso exclusivo del Servidor BitBridge)
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);
        X509Certificate[] cadenaCertificados = new X509Certificate[]{certificadoFinal};
        keyStore.setKeyEntry("bitbridge", parLlaves.getPrivate(), password.toCharArray(), cadenaCertificados);

        try (FileOutputStream fos = new FileOutputStream(fileP12)) {
            keyStore.store(fos, password.toCharArray());
        }
        Logger.logInfo("💾 Contenedor PKCS12 guardado en: " + fileP12.getAbsolutePath());

        // B: Exportar el Certificado Plano en formato PEM limpio sin duplicación de quiebres de línea
        File fileCrt = new File(rutaDestinoCrt);
        try (FileOutputStream fos = new FileOutputStream(fileCrt)) {
            fos.write("-----BEGIN CERTIFICATE-----\n".getBytes());
            byte[] certBytesBase64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encode(certificadoFinal.getEncoded());
            fos.write(certBytesBase64);
            fos.write("-----END CERTIFICATE-----\n".getBytes());
        }
        Logger.logInfo("💾 Certificado público plano (.crt) exportado en: " + fileCrt.getAbsolutePath());
    }
}
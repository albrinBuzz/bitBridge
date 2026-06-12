package org.bitBridge.Tests.tls;



import org.bitBridge.shared.core.secure.CryptoGeneratorController;

import java.io.Console;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class SecurityGeneratorCliView {

    private final CryptoGeneratorController controller;

    public SecurityGeneratorCliView(CryptoGeneratorController controller) {
        this.controller = controller;
    }

    /**
     * Arranca la ejecución en modo consola interactiva guiada.
     */
    public void iniciarModoInteractivo() {
        Scanner scanner = new Scanner(System.in);
        Console console = System.console();

        System.out.println("=======================================================================");
        System.out.println("🔒 CONFIGURACIÓN Y GENERACIÓN CRIPTOGRÁFICA DE BITBRIDGE (Modo CLI)");
        System.out.println("=======================================================================");

        System.out.print("» Carpeta Base destino [./build/test_certs]: ");
        String carpetaBase = scanner.nextLine().trim();
        if (carpetaBase.isEmpty()) carpetaBase = "./build/test_certs";

        String password = "";
        if (console != null) {
            char[] passArray = console.readPassword("» Clave del Keystore (Oculta): ");
            password = new String(passArray);
        } else {
            System.out.print("» Clave del Keystore (Texto plano fallback): ");
            password = scanner.nextLine();
        }

        System.out.print("» Sujeto Certificado (CN) [BitBridgeLocalNode]: ");
        String commonName = scanner.nextLine().trim();
        if (commonName.isEmpty()) commonName = "BitBridgeLocalNode";

        System.out.print("» Nombre Organización [BitBridgeBeta]: ");
        String organizacion = scanner.nextLine().trim();
        if (organizacion.isEmpty()) organizacion = "BitBridgeBeta";

        System.out.print("» Días de validez [365]: ");
        String diasInput = scanner.nextLine().trim();
        int diasValidez = diasInput.isEmpty() ? 365 : Integer.parseInt(diasInput);

        System.out.print("» Dominios/IPs Públicas WAN adicionales separados por coma (opcional): ");
        String sanInput = scanner.nextLine().trim();
        List<String> dominiosExtra = new ArrayList<>();
        if (!sanInput.isEmpty()) {
            dominiosExtra = Arrays.asList(sanInput.split("\\s*,\\s*"));
        }

        System.out.println("\n⏳ Procesando solicitud en el backend criptográfico...");

        // Llamada directa al mismo método del controlador unificado
        controller.procesarGeneracion(carpetaBase, password, commonName, organizacion, diasValidez, dominiosExtra,
                new CryptoGeneratorController.CryptoExecutionListener() {
                    @Override
                    public void onProgress(String message) {
                        System.out.println(message);
                    }

                    @Override
                    public void onSuccess(String keystorePath, String certPath) {
                        System.out.println("\n✨ [ÉXITO] Infraestructura TLS generada correctamente.");
                        System.out.println("   -> Keystore: " + keystorePath);
                        System.out.println("   -> Certificado público: " + certPath);
                    }

                    @Override
                    public void onError(String errorMessage, Throwable cause) {
                        System.err.println("\n💥 [ERROR] Falló la generación criptográfica: " + errorMessage);
                    }
                }
        );
    }
}
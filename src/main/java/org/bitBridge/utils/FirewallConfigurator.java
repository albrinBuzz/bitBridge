package org.bitBridge.utils;


import org.bitBridge.shared.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class FirewallConfigurator {

    public static void configure() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("linux")) {
            configureLinux();
        } else if (os.contains("win")) {
            configureWindows();
        }
    }

    private static void configureLinux() {
        Logger.logInfo("[Firewall] Detectado Linux. Solicitando permisos para configurar firewalld...");
        // Añadimos 8080-8081 y el rango de respaldo por si acaso
        String command = "pkexec firewall-cmd --add-port=8080-8081/tcp --add-port=45000-45100/tcp --add-service=mdns --permanent && pkexec firewall-cmd --reload";
        executeCommand(new String[]{"sh", "-c", command});
    }

    private static void configureWindows() {
        Logger.logInfo("[Firewall] Detectado Windows. Configurando reglas de entrada...");
        // En Windows se usa netsh (requiere ejecutar el JAR como Admin)
        String ruleName = "BitBridge_Traffic";
        String command = String.format("netsh advfirewall firewall add rule name=\"%s\" dir=in action=allow protocol=TCP localport=8080,8081,45000-45100", ruleName);
        executeCommand(new String[]{"cmd.exe", "/c", command});
    }

    private static void executeCommand(String[] command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                Logger.logInfo("[Firewall] Configuración aplicada exitosamente.");
            } else {
                Logger.logWarn("[Firewall] El comando terminó con código de error: " + exitCode);
            }
        } catch (Exception e) {
            Logger.logError("[Firewall] Fallo al ejecutar configuración: " + e.getMessage());
        }
    }
}
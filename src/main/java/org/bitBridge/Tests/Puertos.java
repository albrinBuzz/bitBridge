package org.bitBridge.Tests;

import org.bitBridge.shared.Logger;

public class Puertos {
    public static void main(String[] args) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("linux")) {
            try {
                Logger
                        .logInfo("Configurando firewall de Linux para BitBridge...");
                String[] command = {
                        "pkexec", // Abre la ventanita de contraseña de la distro
                        "firewall-cmd",
                        "--add-port=45000-45100/tcp",
                        "--add-service=mdns",
                        "--immediate"
                };
                Process p = Runtime.getRuntime().exec(command);
                p.waitFor();
                Logger.logInfo("Firewall configurado con éxito.");
            } catch (Exception e) {
                Logger.logError("No se pudo configurar el firewall automáticamente.");
            }
        }
    }
}

package org.bitBridge.Tests;



import org.bitBridge.shared.Logger;
import java.io.File;
import java.io.PrintWriter;

public class FirewallAutomation {


    public static void main(String[] args) {
        runApertura();
    }
    public static void runApertura() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("linux")) {
                // Ejecuta múltiples comandos unidos por && mediante sh -c
                String cmd = "pkexec sh -c 'firewall-cmd --add-port=8080-8085/tcp --add-port=45000-45100/tcp --add-service=mdns --permanent && firewall-cmd --reload'";
                Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                Logger.logInfo("Comando de firewall enviado en Linux.");
            }
            else if (os.contains("win")) {
                // PowerShell permite elevar privilegios para un comando específico
                String psCommand = "Start-Process powershell -ArgumentList 'netsh advfirewall firewall add rule name=\"BitBridge_In\" dir=in action=allow protocol=TCP localport=8080-8085,45000-45100' -Verb RunAs";
                Runtime.getRuntime().exec(new String[]{"powershell", "-Command", psCommand});
                Logger.logInfo("Solicitud de elevación enviada en Windows.");
            }
        } catch (Exception e) {
            Logger.logError("Error al ejecutar comandos de apertura: " + e.getMessage());
        }
    }
}
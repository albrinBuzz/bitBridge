package org.bitBridge;

import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.view.swing.MainView;
import javax.swing.SwingUtilities;

public class Launcher {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainView().setVisible(true));

        // Si detectamos una propiedad especial, significa que ya somos el proceso "hijo" con mucha RAM
        /*if (System.getProperty("bitbridge.spawned") != null) {
            SwingUtilities.invokeLater(() -> new MainView().setVisible(true));
        } else {
            // Somos el primer proceso, vamos a calcular y lanzar el "hijo"
            launch();
        }*/
    }

    public static void launch() {
        try {
            ConfiguracionApp config = ConfiguracionApp.getInstancia();
            long totalPoolsBytes = calcularTotalPools(config);

            long systemRAM = ((com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();

            // Si sobrepasa el 20%, pedimos el 50% de la RAM
            String xmxParam = "-Xmx1g";
            if (totalPoolsBytes > (systemRAM * 0.20)) {
                long segura = systemRAM / 2;
                xmxParam = "-Xmx" + (segura / (1024 * 1024)) + "m";
            }

            // COMANDO CORREGIDO PARA SPRING BOOT
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    xmxParam,
                    "-Dbitbridge.spawned=true", // Flag para evitar bucle
                    "-Dfile.encoding=UTF-8",
                    "-jar", "target/BitBridge-Desktop.jar" // Dejamos que Spring Boot maneje el arranque
            );

            pb.inheritIO();
            pb.start();
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static long calcularTotalPools(ConfiguracionApp config) {
        try {
            // Usamos los métodos de tu clase ConfiguracionServidor
            long m = Long.parseLong(config.obtener(ConfigKey.POOL_MSG_SIZE, "8"))
                    * 1024L * config.obtenerInt(ConfigKey.POOL_MSG_CAP, 10000);
            long d = Long.parseLong(config.obtener(ConfigKey.POOL_DIR_SIZE, "64"))
                    * 1024L * config.obtenerInt(ConfigKey.POOL_DIR_CAP, 1000);
            long t = Long.parseLong(config.obtener(ConfigKey.POOL_TRANS_SIZE, "512"))
                    * 1024L * config.obtenerInt(ConfigKey.POOL_TRANS_CAP, 500);
            return m + d + t;
        } catch (Exception e) {
            return 256 * 1024 * 1024; // 256MB default si falla
        }
    }
}
package org.bitBridge.Tests;

import org.bitBridge.Client.core.Client;
import com.sun.management.OperatingSystemMXBean;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MixedStressTestRunner {
    // --- CONFIGURACIÓN DE RED ---
    static final String SERVER_IP = "192.168.100.192"; // Cambia según el test
    //static final String SERVER_IP = "127.0.0.1";
    //static final String SERVER_IP = "192.168.100.212";
    //static final String SERVER_IP = "192.168.100.170"; // Cambia según el test

    static final int PORT = 8080;

    // --- CONFIGURACIÓN DE ESTRÉS ---
    static final int TOTAL_CLIENTS = 3175;
    static final int MESSAGES_PER_CLIENT = 45;
    static final int TOTAL_EXPECTED = TOTAL_CLIENTS * MESSAGES_PER_CLIENT;

    // --- INVENTARIO DINÁMICO DE HARDWARE ---
    private static final Map<String, String> HARDWARE_REGISTRY = new HashMap<>();

    static {
        HARDWARE_REGISTRY.put("192.168.100.212",
                "💻 [PENTIUM SERVER]\n" +
                        "   CPU: Intel Pentium N4200 (4C/4T) @ 1.10GHz\n" +
                        "   RAM: 4GB DDR3 | OS: Rocky Linux (Kernel 5.x)\n" +
                        "   TIPO: Nodo de Bajo Consumo");

        HARDWARE_REGISTRY.put("192.168.100.147",
                "🚀 [ACER NITRO 5 AN515-55]\n" +
                        "   CPU: Intel i5-10300H (4C/8T) @ 4.50GHz Turbo\n" +
                        "   RAM: 16GB | GPU: GTX 1650 | OS: Fedora 41 (Kernel 6.17)\n" +
                        "   NET: Realtek Killer E2600 GbE (Latencia Baja)");

        HARDWARE_REGISTRY.put("127.0.0.1",
                "🏠 [MASTER WORKSTATION - GIGABYTE B760M]\n" +
                        "   CPU: Intel i7-12700 (12C/20T) | 8 P-Cores | 4 E-Cores\n" +
                        "   RAM: 64GB DDR4 | SSD: Kingston NVMe Gen4\n" +
                        "   OS: Fedora 42 (Adams) | Kernel 6.18 | i3wm\n" +
                        "   INFO: Máxima capacidad de concurrencia.");
    }

    public static void main(String[] args) throws InterruptedException {
        final List<Client> activeClients = new CopyOnWriteArrayList<>();
        String hardwareInfo = HARDWARE_REGISTRY.getOrDefault(SERVER_IP, "❓ DISPOSITIVO DESCONOCIDO");

        System.out.println("🔥 INICIANDO ESTRÉS HACIA: " + hardwareInfo);

        long startTest = System.currentTimeMillis();

        // Monitor en vivo
        Thread monitor = Thread.ofVirtual().start(() -> {
            long lastDelivered = 0;
            while (!Thread.interrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    long current = activeClients.stream().mapToLong(c -> c.getMessageTracker().getTotalDelivered()).sum();
                    System.out.printf("\r🚀 [LIVE] %s | ACKs: %d | %d msg/s", SERVER_IP, current, (current - lastDelivered));
                    lastDelivered = current;
                } catch (InterruptedException e) { break; }
            }
        });

        // Ejecución de clientes
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < TOTAL_CLIENTS; i++) {
                executor.submit(() -> {
                    try {
                        Client c = new Client();
                        c.setConexion(SERVER_IP, PORT);
                        activeClients.add(c);
                        Thread.sleep((long) (Math.random() * 500));
                        for (int m = 0; m < MESSAGES_PER_CLIENT; m++) {
                            c.enviarMensaje("Stress Test Msg " + m);
                            Thread.sleep(45);
                        }
                    } catch (Exception ignored) {}
                });
                Thread.sleep(20);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.MINUTES);
        }

        TimeUnit.SECONDS.sleep(15);
        long endTest = System.currentTimeMillis();
        monitor.interrupt();

        printAndSaveFinalReport(activeClients, (endTest - startTest), hardwareInfo);

        TimeUnit.SECONDS.sleep(30);
    }

    private static void printAndSaveFinalReport(List<Client> clients, long totalDurationMs, String hwInfo) {
        long delivered = clients.stream().mapToLong(c -> c.getMessageTracker().getTotalDelivered()).sum();
        long sent = clients.stream().mapToLong(c -> c.getMessageTracker().getTotalSent()).sum();
        long alive = clients.stream().filter(Client::isActive).count();

        double seconds = totalDurationMs / 1000.0;
        double throughput = delivered / seconds;
        double efficiency = (sent > 0) ? (delivered * 100.0 / sent) : 0;

        // Info de la máquina LOCAL (la que lanza el test)
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        String localHost = "Desconocido";
        try { localHost = InetAddress.getLocalHost().getHostName(); } catch (Exception e) {}

        StringBuilder report = new StringBuilder();
        report.append("\n").append("█".repeat(65)).append("\n");
        report.append("📊 BITBRIDGE PERFORMANCE BENCHMARK\n");
        report.append("█".repeat(65)).append("\n");

        // SECCIÓN DE HARDWARE (Dinámica por IP)
        report.append("🖥️  TARGET HARDWARE (SERVER):\n");
        report.append(hwInfo).append("\n");
        report.append("-".repeat(65)).append("\n");

        // SECCIÓN DE RED Y TIEMPOS
        report.append(String.format("🌐 ENDPOINT   : %s:%d\n", SERVER_IP, PORT));
        report.append(String.format("⏱️  DURATION   : %.2f seconds\n", totalDurationMs / 1000.0));
        report.append(String.format("📅 TIMESTAMP  : %s\n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        report.append("-".repeat(65)).append("\n");

        // SECCIÓN DE MÉTRICAS DE ESTRÉS
        report.append(String.format("⚙️  LOAD CONFIG : %d clients | %d msg per client\n", TOTAL_CLIENTS, MESSAGES_PER_CLIENT));
        report.append(String.format("✅ ACKs        : %d / %d\n", delivered, sent));
        report.append(String.format("🚀 THROUGHPUT  : %.2f msg/sec\n", throughput));
        report.append(String.format("📊 EFFICIENCY  : %.2f%%\n", efficiency));

        // RESULTADO VISUAL
        String status = (efficiency > 98) ? "🔥 OPTIMAL" : (efficiency > 85) ? "⚠️ STRESSED" : "❌ CONGESTED";
        report.append("STATUS      : ").append(status).append("\n");
        report.append("█".repeat(65)).append("\n");

        System.out.println(report);
        saveToFile(report.toString());
    }

    private static void saveToFile(String report) {
        try {
            File dir = new File("logs_test");
            if (!dir.exists()) dir.mkdir();
            try (PrintWriter out = new PrintWriter(new FileWriter(new File(dir, "stress_test_history.log"), true))) {
                out.println(report);
            }
        } catch (IOException e) { System.err.println("Error: " + e.getMessage()); }
    }
}
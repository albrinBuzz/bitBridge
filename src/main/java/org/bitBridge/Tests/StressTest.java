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

public class StressTest {

    private static final int PORT = 8080;
    private static final Map<String, String> HARDWARE_REGISTRY = new HashMap<>();

    static {
        HARDWARE_REGISTRY.put("192.168.100.212", "💻 [PENTIUM SERVER] | 4GB RAM | Rocky Linux");
        HARDWARE_REGISTRY.put("192.168.100.147", "🚀 [ACER NITRO 5] | 16GB RAM | Fedora 41");
        HARDWARE_REGISTRY.put("192.168.100.168", "🏠 [MASTER WORKSTATION - GIGABYTE B760M] | 64GB RAM | Fedora 42");
        HARDWARE_REGISTRY.put("127.0.0.1", "🔄 [LOCAL LOOPBACK] | Internal Test");
    }

    public static void main(String[] args) throws InterruptedException {
        // --- VALIDACIÓN DE ARGUMENTOS POR CONSOLA ---
        if (args.length < 3) {
            System.out.println("\n❌ ERROR: Faltan parámetros.");
            System.out.println("👉 USO: java -jar StressTool.jar <IP_SERVIDOR> <NUM_CLIENTES> <MSG_POR_CLIENTE>");
            System.out.println("👉 EJEMPLO: java -jar StressTool.jar 192.168.100.168 500 25\n");
            return;
        }

        String serverIp = args[0];
        int totalClients = Integer.parseInt(args[1]);
        int messagesPerClient = Integer.parseInt(args[2]);
        int totalExpected = totalClients * messagesPerClient;

        final List<Client> activeClients = new CopyOnWriteArrayList<>();
        String hardwareInfo = HARDWARE_REGISTRY.getOrDefault(serverIp, "❓ DISPOSITIVO DESCONOCIDO (" + serverIp + ")");

        System.out.println("\n" + "█".repeat(65));
        System.out.println("🔥 INICIANDO ESTRÉS DINÁMICO");
        System.out.println("🎯 DESTINO: " + hardwareInfo);
        System.out.println("⚙️  CARGA  : " + totalClients + " clientes | " + messagesPerClient + " msg/cli (" + totalExpected + " totales)");
        System.out.println("█".repeat(65) + "\n");

        long startTest = System.currentTimeMillis();

        // Monitor en vivo (Virtual Thread)
        Thread monitor = Thread.ofVirtual().start(() -> {
            long lastDelivered = 0;
            while (!Thread.interrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    long current = activeClients.stream().mapToLong(c -> c.getMessageTracker().getTotalDelivered()).sum();
                    double progress = (totalExpected > 0) ? (current * 100.0 / totalExpected) : 0;
                    System.out.printf("\r🚀 [LIVE] %s | ACKs: %d/%d | %d msg/s | %.1f%% ",
                            serverIp, current, totalExpected, (current - lastDelivered), progress);
                    lastDelivered = current;
                } catch (InterruptedException e) { break; }
            }
        });

        // Ejecución de clientes usando Virtual Threads para concurrencia masiva
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < totalClients; i++) {
                executor.submit(() -> {
                    try {
                        Client c = new Client();
                        c.setConexion(serverIp, PORT);
                        activeClients.add(c);

                        // Pequeño jitter para no golpear el socket exactamente al mismo milisegundo
                        Thread.sleep((long) (Math.random() * 300));

                        for (int m = 0; m < messagesPerClient; m++) {
                            c.enviarMensaje("Stress Test Msg " + m);
                            Thread.sleep(30); // Frecuencia de envío
                        }
                    } catch (Exception e) {
                        // Opcional: System.err.println("Falló conexión: " + e.getMessage());
                    }
                });
                // Rampa de aceleración: 10ms entre creación de cada cliente
                Thread.sleep(10);
            }
        } finally {
            executor.shutdown();
            // Esperamos un máximo de 5 minutos a que terminen todos los hilos
            executor.awaitTermination(5, TimeUnit.MINUTES);
        }

        // Tiempo extra para capturar los últimos ACKs que vienen de regreso por la red
        System.out.println("\n\n⏳ Finalizando ráfaga y colectando respuestas...");
        TimeUnit.SECONDS.sleep(10);

        long endTest = System.currentTimeMillis();
        monitor.interrupt();

        printAndSaveFinalReport(activeClients, (endTest - startTest), hardwareInfo, serverIp, totalClients, messagesPerClient);
    }

    private static void printAndSaveFinalReport(List<Client> clients, long totalDurationMs, String hwInfo, String ip, int tClients, int tMsgs) {
        long delivered = clients.stream().mapToLong(c -> c.getMessageTracker().getTotalDelivered()).sum();
        long sent = clients.stream().mapToLong(c -> c.getMessageTracker().getTotalSent()).sum();

        double seconds = totalDurationMs / 1000.0;
        double throughput = delivered / seconds;
        double efficiency = (sent > 0) ? (delivered * 100.0 / sent) : 0;

        String localHost = "Desconocido";
        try { localHost = InetAddress.getLocalHost().getHostName(); } catch (Exception e) {}

        StringBuilder report = new StringBuilder();
        report.append("\n").append("█".repeat(65)).append("\n");
        report.append("📊 BITBRIDGE PERFORMANCE BENCHMARK\n");
        report.append("█".repeat(65)).append("\n");
        report.append(String.format("🖥️  TARGET HARDWARE : %s\n", hwInfo));
        report.append(String.format("🏠 ORIGIN CLIENT   : %s\n", localHost.toUpperCase()));
        report.append("-".repeat(65)).append("\n");
        report.append(String.format("🌐 ENDPOINT        : %s:%d\n", ip, PORT));
        report.append(String.format("⏱️  DURATION        : %.2f seconds\n", seconds));
        report.append(String.format("📅 TIMESTAMP       : %s\n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        report.append("-".repeat(65)).append("\n");
        report.append(String.format("⚙️  LOAD CONFIG     : %d clients | %d msg per client\n", tClients, tMsgs));
        report.append(String.format("✅ ACKs            : %d / %d\n", delivered, sent));
        report.append(String.format("🚀 THROUGHPUT      : %.2f msg/sec\n", throughput));
        report.append(String.format("📊 EFFICIENCY      : %.2f%%\n", efficiency));

        String status = (efficiency > 95) ? "🔥 OPTIMAL" : (efficiency > 80) ? "⚠️ STRESSED" : "❌ CONGESTED";
        report.append("STATUS           : ").append(status).append("\n");
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
        } catch (IOException e) { System.err.println("Error guardando log: " + e.getMessage()); }
    }
}
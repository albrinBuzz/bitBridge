package org.bitBridge.Tests;

import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.RemoteDirectoryListener;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;
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
import java.util.concurrent.atomic.AtomicLong;

public class MixedStressTestRunner {
    // --- CONFIGURACIÓN DE RED ---
    //static final String SERVER_IP = "127.0.0.1";
    static final String SERVER_IP = "192.168.100.192";
    static final int PORT = 8080;

    // --- CONFIGURACIÓN DE ESTRÉS COMBINADO ---
    static final int TOTAL_CLIENTS = 1500;            // Escala controlada para carga mixta
    static final int MESSAGES_PER_CLIENT = 30;
    static final int DIR_QUERIES_PER_CLIENT = 5;       // Consultas recursivas por cliente híbrido

    // --- MONITORES ATÓMICOS PARA FLUJO DE DIRECTORIOS ---
    private static final AtomicLong TOTAL_DIR_QUERIES_SENT = new AtomicLong(0);
    private static final AtomicLong TOTAL_DIR_RESPONSES_RECEIVED = new AtomicLong(0);

    // --- INVENTARIO DINÁMICO DE HARDWARE ---
    private static final Map<String, String> HARDWARE_REGISTRY = new HashMap<>();

    static {
        HARDWARE_REGISTRY.put("192.168.100.212",
                "💻 [PENTIUM SERVER]\n" +
                        "   CPU: Intel Pentium N4200 (4C/4T) @ 1.10GHz\n" +
                        "   RAM: 4GB DDR3 | OS: Rocky Linux (Kernel 5.x)");

        HARDWARE_REGISTRY.put("192.168.100.192",
                "🚀 [ACER NITRO 5 AN515-55]\n" +
                        "   CPU: Intel i5-10300H (4C/8T) @ 4.50GHz Turbo\n" +
                        "   RAM: 16GB | GPU: GTX 1650 | OS: Fedora 41 (Kernel 6.17)");

        HARDWARE_REGISTRY.put("127.0.0.1",
                "🏠 [MASTER WORKSTATION - GIGABYTE B760M]\n" +
                        "   CPU: Intel i7-12700 (12C/20T) | 8 P-Cores | 4 E-Cores\n" +
                        "   RAM: 64GB DDR4 | SSD: Kingston NVMe Gen4\n" +
                        "   OS: Fedora 42 (Adams) | Kernel 6.18 | i3wm");
    }

    public static void main(String[] args) throws InterruptedException {
        final List<Client> activeClients = new CopyOnWriteArrayList<>();
        String hardwareInfo = HARDWARE_REGISTRY.getOrDefault(SERVER_IP, "❓ DISPOSITIVO DESCONOCIDO");

        System.out.println("🔥 INICIANDO TEST DE ESTRÉS MIXTO (CHAT + IO RECURSIVO) HACIA: " + hardwareInfo);

        long startTest = System.currentTimeMillis();

        // Monitor asíncrono en vivo (Virtual Thread)
        Thread monitor = Thread.ofVirtual().start(() -> {
            long lastDelivered = 0;
            long lastDirAnswers = 0;
            while (!Thread.interrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    long currentMsg = activeClients.stream().mapToLong(c -> c.getMessageTracker().getTotalDelivered()).sum();
                    long currentDir = TOTAL_DIR_RESPONSES_RECEIVED.get();

                    System.out.printf("\r🚀 [LIVE] ACKs Chat: %d (%d/s) | Árboles Indexados: %d (%d/s)",
                            currentMsg, (currentMsg - lastDelivered),
                            currentDir, (currentDir - lastDirAnswers));

                    lastDelivered = currentMsg;
                    lastDirAnswers = currentDir;
                } catch (InterruptedException e) { break; }
            }
        });

        // Ejecutor masivo basado en Virtual Threads (Hilos virtuales nativos de Java 21+)
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < TOTAL_CLIENTS; i++) {
                final int clientIndex = i;
                executor.submit(() -> {
                    try {
                        Client c = new Client();
                        c.setHostName("STRESS-NODE-" + clientIndex);
                        c.setConexion(SERVER_IP, PORT);

                        // Añadir Listener para interceptar la respuesta asíncrona del árbol del servidor
                        c.addDirectoryListener(new RemoteDirectoryListener() {
                            @Override
                            public void onDirectoryDataReceived(NodoDirectorio nodo) {
                                if (nodo != null) {
                                    TOTAL_DIR_RESPONSES_RECEIVED.incrementAndGet();
                                }
                            }
                        });

                        activeClients.add(c);

                        // Escalonamiento de conexiones para no saturar el handshake TCP
                        Thread.sleep((long) (Math.random() * 800));

                        // --- FLUJO DE INYECCIÓN MIXTO ---
                        for (int step = 0; step < MESSAGES_PER_CLIENT; step++) {
                            // Enviar mensaje de chat estándar
                            c.enviarMensaje("Mixed Stress Chat Msg " + step + " from node " + clientIndex);

                            // Intercalar consultas recursivas de directorios simulados cada N ciclos
                            if (step % (MESSAGES_PER_CLIENT / DIR_QUERIES_PER_CLIENT) == 0) {
                                TOTAL_DIR_QUERIES_SENT.incrementAndGet();
                                c.requestFileList("STRESS-NODE-" + clientIndex); // Sobrecarga base

                            }
                            // Cadencia de ráfaga
                            Thread.sleep(50);
                        }

                    } catch (Exception ignored) {
                        // Capturar caídas de conexión o rechazos de socket por el kernel bajo estrés
                    }
                });
                // Delay controlado de inyección de hilos virtuales
                Thread.sleep(15);
            }
        } finally {
            executor.shutdown();
            // Espera máxima para el drenado de búferes de Netty
            executor.awaitTermination(3, TimeUnit.MINUTES);
        }

        // Ventana final de estabilización para capturar ACKs residuales
        TimeUnit.SECONDS.sleep(10);
        long endTest = System.currentTimeMillis();
        monitor.interrupt();

        printAndSaveFinalReport(activeClients, (endTest - startTest), hardwareInfo);
        TimeUnit.SECONDS.sleep(5);
    }

    private static void printAndSaveFinalReport(List<Client> clients, long totalDurationMs, String hwInfo) {
        long deliveredMsg = clients.stream().mapToLong(c -> c.getMessageTracker().getTotalDelivered()).sum();
        long sentMsg = clients.stream().mapToLong(c -> c.getMessageTracker().getTotalSent()).sum();
        long aliveNodes = clients.stream().filter(Client::isActive).count();

        long sentQueries = TOTAL_DIR_QUERIES_SENT.get();
        long receivedTrees = TOTAL_DIR_RESPONSES_RECEIVED.get();

        double seconds = totalDurationMs / 1000.0;
        double msgThroughput = deliveredMsg / seconds;
        double dirThroughput = receivedTrees / seconds;

        double msgEfficiency = (sentMsg > 0) ? (deliveredMsg * 100.0 / sentMsg) : 0;
        double dirEfficiency = (sentQueries > 0) ? (receivedTrees * 100.0 / sentQueries) : 0;

        StringBuilder report = new StringBuilder();
        report.append("\n").append("█".repeat(70)).append("\n");
        report.append("📊 BITBRIDGE HYBRID STRESS BENCHMARK REPORT\n");
        report.append("█".repeat(70)).append("\n");

        // HARDWARE TARGET
        report.append("🖥️  TARGET HARDWARE (SERVER):\n");
        report.append(hwInfo).append("\n");
        report.append("-".repeat(70)).append("\n");

        // TIEMPOS
        report.append(String.format("🌐 ENDPOINT         : %s:%d\n", SERVER_IP, PORT));
        report.append(String.format("⏱️  TOTAL DURATION  : %.2f seconds\n", seconds));
        report.append(String.format("👥 ALIVE NODES      : %d / %d\n", aliveNodes, TOTAL_CLIENTS));
        report.append("-".repeat(70)).append("\n");

        // MÉTRICAS PIPELINE 1: CHAT
        report.append("💬 PIPELINE DE MENSAJERÍA (CHAT):\n");
        report.append(String.format("   -> ACKs Recibidos : %d / %d\n", deliveredMsg, sentMsg));
        report.append(String.format("   -> Rendimiento    : %.2f msg/sec\n", msgThroughput));
        report.append(String.format("   -> Eficiencia     : %.2f%%\n", msgEfficiency));
        report.append("-".repeat(70)).append("\n");

        // MÉTRICAS PIPELINE 2: I/O REMOTO (DIRECTORIOS)
        report.append("📂 PIPELINE DE EXPLORACIÓN RECURSIVA (I/O):\n");
        report.append(String.format("   -> Consultas Out  : %d\n", sentQueries));
        report.append(String.format("   -> Árboles In     : %d\n", receivedTrees));
        report.append(String.format("   -> Rendimiento    : %.2f estructuras/sec\n", dirThroughput));
        report.append(String.format("   -> Tasa de Éxito  : %.2f%%\n", dirEfficiency));
        report.append("-".repeat(70)).append("\n");

        // ESTADO GLOBAL DEL BACKEND
        double globalScore = (msgEfficiency + dirEfficiency) / 2.0;
        String status = (globalScore > 95) ? "🔥 OPTIMAL (NIO sin pérdida)" :
                (globalScore > 80) ? "⚠️ STRESSED (Cola saturada)" : "❌ CONGESTED / DROPPING";

        report.append("STATUS METRIC LEVEL : ").append(status).append("\n");
        report.append("█".repeat(70)).append("\n");

        System.out.println(report);
        saveToFile(report.toString());
    }

    private static void saveToFile(String report) {
        try {
            File dir = new File("logs_test");
            if (!dir.exists()) dir.mkdir();
            try (PrintWriter out = new PrintWriter(new FileWriter(new File(dir, "mixed_stress_history.log"), true))) {
                out.println(report);
            }
        } catch (IOException e) { System.err.println("Error guardando bitácora: " + e.getMessage()); }
    }
}
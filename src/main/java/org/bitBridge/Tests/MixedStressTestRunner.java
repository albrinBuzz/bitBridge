package org.bitBridge.Tests;

import org.bitBridge.Client.core.Client;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MixedStressTestRunner {
    // Ajustes para Pentium N4200 (4 núcleos, 4GB RAM)
    static final int TOTAL_CLIENTS = 110;
    static final int MESSAGES_PER_CLIENT = 10;
    static final int TOTAL_EXPECTED = TOTAL_CLIENTS * MESSAGES_PER_CLIENT;
    static final String SERVER_IP = "192.168.100.147";
    static final int PORT = 8080;

    public static void main(String[] args) throws InterruptedException {
        final List<Client> activeClients = new CopyOnWriteArrayList<>();

        System.out.println("🔥 INICIANDO TEST DE RENDIMIENTO: " + TOTAL_EXPECTED + " mensajes totales.");
        System.out.println("⚙️  Configuración: " + TOTAL_CLIENTS + " clientes envían " + MESSAGES_PER_CLIENT + " mensajes c/u.");

        long startTest = System.currentTimeMillis();

        // 1. Monitor en tiempo real (Virtual Thread)
        Thread monitor = Thread.ofVirtual().start(() -> {
            long lastDelivered = 0;
            while (!Thread.interrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    long currentDelivered = activeClients.stream()
                            .mapToLong(c -> c.getMessageTracker().getTotalDelivered()).sum();

                    long mps = currentDelivered - lastDelivered;
                    lastDelivered = currentDelivered;

                    double progress = (currentDelivered * 100.0 / TOTAL_EXPECTED);
                    System.out.printf("\r🚀 [LIVE] ACKs: %d | Speed: %d msg/s | Progreso: %.1f%%",
                            currentDelivered, mps, progress);
                } catch (InterruptedException e) { break; }
            }
        });

        // 2. Ejecutor de Clientes (Virtual Threads)
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
            executor.awaitTermination(1, TimeUnit.MINUTES);
        }

        System.out.println("\n\n⏳ Esperando recuperación de ACKs finales (15s)...");
        TimeUnit.SECONDS.sleep(15);

        long endTest = System.currentTimeMillis();
        monitor.interrupt();

        // 4. Reporte Final y persistencia en Log
        printAndSaveFinalReport(activeClients, TOTAL_EXPECTED, (endTest - startTest));
    }

    private static void printAndSaveFinalReport(List<Client> clients, int expected, long totalDurationMs) {
        long delivered = clients.stream().mapToLong(c -> c.getMessageTracker().getTotalDelivered()).sum();
        long sent = clients.stream().mapToLong(c -> c.getMessageTracker().getTotalSent()).sum();
        long alive = clients.stream().filter(Client::isActive).count();

        double efficiency = (sent > 0) ? (delivered * 100.0 / sent) : 0;
        double seconds = totalDurationMs / 1000.0;
        double throughput = delivered / seconds;

        StringBuilder logContent = new StringBuilder();
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Construcción del reporte (Consola y String para el archivo)
        logContent.append("\n").append("█".repeat(45)).append("\n");
        logContent.append("📊 REPORTE DE ESTRÉS BITBRIDGE\n");
        logContent.append("█".repeat(45)).append("\n");
        logContent.append("📅 FECHA          : ").append(date).append("\n");
        logContent.append("🌐 IP SERVIDOR    : ").append(SERVER_IP).append(":").append(PORT).append("\n");
        logContent.append("⏱️  DURACIÓN TOTAL : ").append(String.format("%.2f segundos", seconds)).append("\n");
        logContent.append("🔌 SUPERVIVENCIA  : ").append(alive).append(" / ").append(TOTAL_CLIENTS).append(" clientes vivos\n");
        logContent.append("-".repeat(45)).append("\n");
        logContent.append("⚙️  CONFIG         : ").append(TOTAL_CLIENTS).append(" clientes x ").append(MESSAGES_PER_CLIENT).append(" msg\n");
        logContent.append("📤 ENVIADOS (Cli) : ").append(sent).append("\n");
        logContent.append("✅ RECIBIDOS (ACK) : ").append(delivered).append("\n");
        logContent.append("❌ DIFERENCIA     : ").append(sent - delivered).append("\n");
        logContent.append("-".repeat(45)).append("\n");
        logContent.append("🚀 VELOCIDAD REAL : ").append(String.format("%.2f msg/seg", throughput)).append("\n");
        logContent.append("📊 EFICIENCIA NETO: ").append(String.format("%.2f%%", efficiency)).append("\n");

        String resultMsg;
        if (efficiency >= 98 && alive == TOTAL_CLIENTS) {
            resultMsg = "🟢 RESULTADO: ÉXITO TOTAL";
        } else if (alive < TOTAL_CLIENTS) {
            resultMsg = "🔴 RESULTADO: FALLO DE CONEXIÓN";
        } else {
            resultMsg = "❌ RESULTADO: CONGESTIÓN / PÉRDIDA";
        }
        logContent.append("=".repeat(45)).append("\n").append(resultMsg).append("\n").append("=".repeat(45));

        // Mostrar en consola
        System.out.println(logContent);

        // Guardar en archivo
        saveLogToFile(logContent.toString());
    }

    private static void saveLogToFile(String content) {
        try {
            File directory = new File("logs_test");
            if (!directory.exists()) directory.mkdir();

            // Nombre de archivo fijo para acumular todos los tests
            File logFile = new File(directory, "stress_test_history.log");

            // El parámetro 'true' en FileWriter activa el modo "APPEND" (añadir al final)
            try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
                out.println("\n" + "=".repeat(60));
                out.println("NUEVA EJECUCIÓN DETECTADA");
                out.println("=".repeat(60));
                out.println(content);
                out.println("\n"); // Espacio extra entre logs para legibilidad
            }
            System.out.println("\n📂 Registro acumulado en: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ No se pudo escribir en el historial de logs: " + e.getMessage());
        }
    }
}
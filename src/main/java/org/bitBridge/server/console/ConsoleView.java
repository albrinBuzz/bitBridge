package org.bitBridge.server.console;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.utils.Color;

import java.net.UnknownHostException;
import java.util.List;

public class ConsoleView implements Runnable {
    private final ServerStats stats;
    private final int port;
    private final String startTimeStr;
    private volatile boolean running = true;
    private final String[] spinner = {"|", "/", "-", "\\"};
    private int index = 0;

    public ConsoleView(ServerStats stats, int port) {
        this.stats = stats;
        this.port = port;
        this.startTimeStr = new java.util.Date().toString();
    }

    @Override
    public void run() {
        while (running) {
            try {
                render();
                index = (index + 1) % spinner.length;
                Thread.sleep(150); // Velocidad de la animación
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void render() {
        clearConsole();
        printServerInfo(); // Tu logo y detalles de sistema
        printStatusLine(); // La línea con el spinner
        displayClientsInfo(); // Tu tabla con bordes ASCII
        printHistory(); // El log de mensajes
    }

    private void printServerInfo() {
        String serverVersion = "1.0.0";
        String logo = """
                                                                           X       \s
                 XXXXXX   XX   XX       XXXXXX  XXXX     XX       X        X     XXX
                 X             XX       X        X      XXXX      X        X   XXX \s
                 XXXXX    XX   XX       X        X     XX  XX     X        XXXXX   \s
                 X        XX   XX       XXXXX    X     X    XX    X        XX      \s
                 X        XX   XX       X        X    XXXXXXXX    X        XXXX    \s
                 X        XX   XX       X        X    X      X    X        X  XX   \s
                 X        XX   XX       X        X   XX      X    X        X   XX  \s
                 X        XX   XX       X        X  XX       XX   X        X    XXX\s
                XX        XX  XXXXXXXX  XXXXXX   X  X         X   XXXXXX   X      XX\s
                """;

        System.out.println(Color.CYAN_BOLD + "==========================================================");
        System.out.println("                  🌐 Servidor Iniciado 🌐    \uD83D\uDD12              ");
        System.out.println(Color.CYAN_BOLD + "==========================================================");
        System.out.println(Color.RED_BRIGHT + logo + "(" + serverVersion + ")" + Color.RESET);
        System.out.println(Color.RED_UNDERLINED + "----------------------------------------------------------" + Color.RESET);

        System.out.println(Color.YELLOW_BOLD + "  Puerto:                     " + Color.YELLOW + port + Color.RESET);
        System.out.println(Color.YELLOW_BOLD + "  Hora de Inicio:             " + Color.YELLOW + startTimeStr + Color.RESET);
        System.out.println(Color.YELLOW_BOLD + "  Versión de Java:            " + Color.YELLOW + System.getProperty("java.version") + Color.RESET);
        System.out.println(Color.YELLOW_BOLD + "  Memoria Máxima Heap:        " + Color.YELLOW + stats.getMaxMemoryFormat() + Color.RESET);
        System.out.println(Color.CYAN_BOLD + "==========================================================");
    }

    private void printStatusLine() {
        String s = spinner[index];
        System.out.printf("\r\u001B[36m------ Estado del Servidor %s \u001B[0m %sClientes: %d %sMsgs: %d %sUptime: %s %sRAM: %s%n",
                s, Color.BLUE, stats.getClientCount(), Color.BLUE, stats.getTotalMessages(),
                Color.BLUE, stats.getUptime(), Color.BLUE, stats.getMemoryUsageFormat());

        System.out.printf("%sBytes Sent: %s | Hilos: %d | Cores: %d%n",
                Color.BLUE, stats.formatBytes(stats.getTotalBytes()),
                Thread.activeCount(), Runtime.getRuntime().availableProcessors());
    }

    private void displayClientsInfo() {
        StringBuilder display = new StringBuilder();
        display.append(Color.YELLOW_BOLD + "\n============ Clientes Conectados ===========\n");
        display.append("┌─────────────────────┬──────────────────┬─────────────┐\n");
        display.append("│      IP Cliente     │  Tiempo Conexión │    Nick     │\n");
        display.append("├─────────────────────┼──────────────────┼─────────────┤\n");

        for (ClientInfo client : stats.getConnectedClients()) {
            display.append(String.format("│ %-19s │ %-16s │ %-13s │%n",
                    client.getAddress(),
                    stats.formatUptime(client.getConnectionTime()),
                    client.getNick()));
        }

        display.append("└─────────────────────┴──────────────────┴─────────────┘\n");
        System.out.print(display.toString());
    }

    private void printHistory() {
        System.out.println(Color.WHITE + "--- Últimos Mensajes ---");
        List<String> history = stats.getMessageHistory();
        int start = Math.max(0, history.size() - 5);
        for (int i = start; i < history.size(); i++) {
            System.out.println(history.get(i));
        }
    }

    private void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception ignored) {}
    }
}
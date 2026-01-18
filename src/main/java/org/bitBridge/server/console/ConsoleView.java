package org.bitBridge.server.console;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.utils.Color;

import java.util.List;
import java.util.Scanner;

public class ConsoleView implements Runnable {
    private final ServerStats stats;
    private final int port;
    private final String startTimeStr;
    private volatile boolean running = true;
    private final String[] spinner = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private int index = 0;
    private String lastCommandExecuted = "";

    public ConsoleView(ServerStats stats, int port) {
        this.stats = stats;
        this.port = port;
        this.startTimeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        startCommandListener();
    }

    @Override
    public void run() {
        //System.out.print("\033[H\033[2J");
        System.out.print("\033[?25l\033[H\033[2J");
        System.out.flush();
        while (running) {
            try {
                render();
                index = (index + 1) % spinner.length;
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.print("\033[?25h");
    }

    private void render() {
        StringBuilder sb = new StringBuilder();
        //sb.append("\033[H");
        sb.append("\033[H\033[J");
        sb.append(getHeader());
        sb.append(getCommandHelpPanel());
        sb.append(getSystemResourcesPanel()); // <--- Sección de hardware
        sb.append(getNetworkDashboard());
        sb.append(displayNodesTable());
        sb.append(getActivityLog());

        // Espaciado dinámico
        sb.append("\n");

        if (!lastCommandExecuted.isEmpty()) {
            sb.append(Color.BLACK_BOLD).append("  Última acción: ")
                    .append(Color.CYAN).append(lastCommandExecuted)
                    .append(Color.RESET).append("\n");
        } else {
            sb.append("\n");
        }

        sb.append(getCommandLinePrefix());

        // Imprimimos todo el bloque de una sola vez para evitar "tearing" visual
        System.out.print(sb.toString());
        System.out.flush();
    }


    private String getHeader() {
        return Color.PURPLE_BOLD + """
             ____  _ _   ____       _     _            
            | __ )(_) |_| __ ) _ __(_) __| | __ _  ___ 
            |  _ \\| | __|  _ \\| '__| |/ _` |/ _` |/ _ \\
            | |_) | | |_| |_) | |  | | (_| | (_| |  __/
            |____/|_|\\__|____/|_|  |_|\\__,_|\\__, |\\___|
                                            |___/      """
                + Color.CYAN_BOLD + " [ HUB OPERATOR ]\n"
                + Color.PURPLE_BOLD + " ╼" + "━".repeat(60) + "╾\n" + Color.RESET;
    }

    private String getCommandHelpPanel() {
        return Color.WHITE + " Ayuda: " +
                Color.YELLOW + "info" + Color.WHITE + " (sistema) | " +
                Color.YELLOW + "nodes" + Color.WHITE + " (lista) | " +
                Color.YELLOW + "net" + Color.WHITE + " (red) | " +
                Color.YELLOW + "cls" + Color.WHITE + " (limpiar) | " +
                Color.RED + "stop" + Color.WHITE + " (salir)\n" +
                Color.PURPLE_BOLD + " ╼" + "━".repeat(60) + "╾\n" + Color.RESET;
    }

    private String getSystemResourcesPanel() {
        Runtime runtime = Runtime.getRuntime();
        // Información de Hardware
        int cores = runtime.availableProcessors();
        int threads = Thread.activeCount();
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");

        // Información de Memoria Real-Time
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        String usedMemStr = stats.formatBytes(usedMemory);

        // Información de la JVM
        String jvmVendor = System.getProperty("java.vendor");
        String jvmVersion = System.getProperty("java.version");

        return String.format(
                " " + Color.WHITE_BOLD + "HOST:    " + Color.CYAN + "%-15s " +
                        Color.WHITE + "| SO:    " + Color.CYAN + "%s (%s)\n" +
                        " " + Color.WHITE_BOLD + "JVM:     " + Color.YELLOW + "%-15s " +
                        Color.WHITE + "| VER:   " + Color.YELLOW + "%s (%s)\n" +
                        " " + Color.WHITE_BOLD + "MEMORIA: " + Color.GREEN + "%-15s " +
                        Color.WHITE + "| HILOS: " + Color.GREEN + "%d (Cores: %d)\n" +
                        " " + Color.WHITE_BOLD + "LÍMITE:  " + Color.RED + "%-15s " +
                        Color.WHITE + "| ESTADO: " + Color.BLUE + "Sincronizado\n" +
                        Color.PURPLE_BOLD + " ╼" + "━".repeat(60) + "╾\n" + Color.RESET,
                getHostName(), os, arch,
                jvmVendor, jvmVersion, arch,
                usedMemStr + " en uso", threads, cores,
                stats.getMaxMemoryFormat()
        );
    }

    private String getNetworkDashboard() {
        String s = Color.PURPLE_BOLD + spinner[index] + Color.RESET;
        long totalBytes = stats.getTotalBytes();
        long totalMsgs = stats.getTotalMessages();

        // Cálculo de "Salud de Red" basado en hilos vs clientes
        int clients = stats.getClientCount();
        String health = (clients > Thread.activeCount() * 0.8) ? Color.YELLOW + "ESTRESADO" : Color.GREEN + "ESTABLE";

        return String.format(
                " %s  " + Color.CYAN_BOLD + "MÉTRICAS DE RED Y FLUJO DE DATOS\n" +
                        "    " + Color.WHITE_BOLD + "PUNTO ACCESO: " + Color.GREEN_BRIGHT + "%-18s" +
                        Color.WHITE + " | INTERFAZ: " + Color.YELLOW + "%-10s\n" +
                        "    " + Color.WHITE_BOLD + "DIRECCIÓN   : " + Color.WHITE + "IP: " + Color.YELLOW + "%-15s" +
                        Color.WHITE + " | PUERTO  : " + Color.YELLOW + "%-5d\n" +
                        "    " + Color.WHITE_BOLD + "ESTADÍSTICA : " + Color.WHITE + "MSG: " + Color.CYAN + "%-14d" +
                        Color.WHITE + " | SALUD   : %s\n" +
                        "    " + Color.WHITE_BOLD + "RENDIMIENTO : " + Color.WHITE + "UP: " + Color.BLUE + "%-15s" +
                        Color.WHITE + " | TOTAL   : " + Color.BLUE + "%s\n" +
                        "    " + Color.WHITE_BOLD + "CONEXIONES  : " + Color.PURPLE_BOLD + "%-15d" +
                        Color.WHITE + " | MTU     : " + Color.WHITE + "1500 (Auto)\n" +
                        Color.PURPLE_BOLD + " ╼" + "━".repeat(70) + "╾\n" + Color.RESET,
                s,
                getLocalIP() + ":" + port, getActiveInterface(),
                getLocalIP(), port,
                totalMsgs, health,
                stats.getUptime(), stats.formatBytes(totalBytes),
                clients
        );
    }

    private String displayNodesTable() {
        StringBuilder table = new StringBuilder();
        List<ClientInfo> clients = stats.getConnectedClients();

        table.append(Color.PURPLE_BOLD).append("┏━ Clientes CONECTADOS (").append(clients.size()).append(") ").append("━".repeat(Math.max(0, 35 - String.valueOf(clients.size()).length()))).append("┓\n");
        table.append("┃ ").append(Color.YELLOW_BOLD).append(String.format("%-22s %-18s %-16s", "DIRECCIÓN", "SESIÓN", "NICKNAME")).append(Color.PURPLE_BOLD).append(" ┃\n");
        table.append("┣").append("━".repeat(60)).append("┫\n");

        if (clients.isEmpty()) {
            table.append("┃ ").append(Color.BLACK_BOLD).append(String.format("%-76s", "Esperando conexiones entrantes...")).append(Color.PURPLE_BOLD).append(" ┃\n");
        } else {
            for (ClientInfo client : clients) {
                String row = String.format("%-22s %-18s %-16s",
                        client.getAddress(),
                        stats.formatUptime(client.getConnectionTime()),
                        client.getNick());
                table.append("┃ ").append(Color.WHITE).append(row).append(Color.PURPLE_BOLD).append(" ┃\n");
            }
        }
        table.append("┗").append("━".repeat(60)).append("┛\n").append(Color.RESET);
        return table.toString();
    }

    private String getActivityLog() {
        StringBuilder log = new StringBuilder();
        log.append("\n " + Color.PURPLE_BOLD + "◈ TELEMETRÍA DE EVENTOS" + Color.RESET + "\n");
        List<String> history = stats.getMessageHistory();
        int size = history.size();
        for (int i = Math.max(0, size - 4); i < size; i++) {
            log.append(Color.BLACK_BOLD + "  [" + (i + 1) + "] " + Color.RESET + history.get(i) + "\n");
        }
        return log.toString();
    }

    private void startCommandListener() {
        Thread commandThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                if (scanner.hasNextLine()) {
                    String cmd = scanner.nextLine().trim();
                    if (!cmd.isEmpty()) {
                        this.lastCommandExecuted = cmd;
                        processCommand(cmd.toLowerCase());
                    }
                }
            }
        }, "CommandListener");
        commandThread.setDaemon(true);
        commandThread.start();
    }

    private void processCommand(String cmd) {
        /*this.lastCommandExecuted = cmd;

        // Hilo temporal para limpiar el texto después de 3 segundos
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (this.lastCommandExecuted.equals(cmd)) {
                    this.lastCommandExecuted = "";
                }
            } catch (InterruptedException ignored) {}
        }).start();*/

        stats.addMessage(Color.YELLOW + "CMD: " + Color.RESET + cmd);
        switch (cmd) {
            case "help", "?" -> stats.addMessage("Comandos: info, nodes, net, cls, stop");
            case "cls" -> stats.clearMessageHistory();
            case "info" -> stats.addMessage("Arquitectura: " + System.getProperty("os.arch") + " | Cores: " + Runtime.getRuntime().availableProcessors());
            case "nodes" -> stats.addMessage("Nodos en línea: " + stats.getClientCount());
            case "net" -> stats.addMessage("Tráfico total acumulado: " + stats.formatBytes(stats.getTotalBytes()));
            case "stop" -> System.exit(0);
            default -> stats.addMessage(Color.RED + "Comando desconocido.");
        }
    }

    private String getCommandLinePrefix() {
        return Color.PURPLE_BOLD + " ╼" + "━".repeat(60) + "╾\n" +
                Color.CYAN_BOLD + " ❯ " + Color.WHITE_BOLD + "BitBridge: " + Color.RESET;
    }

    private String getDynamicProgressBar(double percent, int size) {
        StringBuilder bar = new StringBuilder();
        int completed = (int) (size * (percent / 100));
        String barColor = String.valueOf((percent > 85 ? Color.RED_BRIGHT : (percent > 60 ? Color.YELLOW_BOLD : Color.GREEN)));
        for (int i = 0; i < size; i++) {
            if (i < completed) bar.append(barColor).append("■");
            else bar.append(Color.BLACK_BOLD).append("·");
        }
        return bar.toString() + Color.RESET;
    }
    private String getLocalIP() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private String getHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
    private String getActiveInterface() {
        try {
            return java.net.NetworkInterface.getByInetAddress(java.net.InetAddress.getLocalHost()).getName();
        } catch (Exception e) {
            return "lo";
        }
    }
    public void stop() { this.running = false; }
}
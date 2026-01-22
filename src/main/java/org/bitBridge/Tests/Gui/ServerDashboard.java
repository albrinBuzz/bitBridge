package org.bitBridge.Tests.Gui;

import com.formdev.flatlaf.FlatClientProperties;
import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.Server;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.shared.network.NetworkManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class ServerDashboard extends JFrame {
    // Paleta de colores exacta de tu ConsoleView
    private static final Color BG_TERMINAL = new Color(13, 15, 18);
    private static final Color PURPLE_ANSI = new Color(162, 155, 254); // PURPLE_BOLD
    private static final Color CYAN_ANSI = new Color(0, 206, 201);   // CYAN_BOLD
    private static final Color GREEN_ANSI = new Color(0, 184, 148);  // GREEN_BRIGHT
    private static final Color YELLOW_ANSI = new Color(253, 203, 110); // YELLOW_BOLD
    private static final Color TEXT_WHITE = new Color(220, 221, 225);

    private final ServerStats stats;
    private final int port;
    private final String localIp;

    private JTextArea txtHardware, txtNetwork, txtTelemetry;
    private DefaultTableModel threadTableModel;
    private Timer guiTimer;

    public ServerDashboard(Server server) {
        this.stats = server.getStats();
        this.port = server.getPORT();
        this.localIp = NetworkManager.getLocalIp();

        setTitle("BitBridge | HUB OPERATOR [SWING-TERMINAL]");
        setSize(1300, 900);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_TERMINAL);
        setLayout(new BorderLayout(10, 10));

        initUI();
        startMonitoring();
    }

    private void initUI() {
        // --- HEADER (ASCII Art Simulation) ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(15, 25, 5, 25));

        JLabel lblLogo = new JLabel("<html><pre style='color:#a29bfe; font-weight:bold;'>" +
                " ____  _ _   ____       _     _            <br>" +
                "| __ )(_) |_| __ ) _ __(_) __| | __ _  ___ <br>" +
                "|  _ \\| | __|  _ \\| '__| |/ _` |/ _` |/ _ \\<br>" +
                "| |_) | | |_| |_) | |  | | (_| | (_| |  __/<br>" +
                "|____/|_|\\__|____/|_|  |_|\\__,_|\\__, |\\___|<br>" +
                "                                |___/      </pre></html>");

        JLabel lblStatus = new JLabel("<html><div style='text-align:right;'><font color='#00cec9' size='6'><b>[ HUB OPERATOR ]</b></font><br>" +
                "<font color='white' size='4'>MODO: INTERFAZ GRÁFICA SINCRONIZADA</font></div></html>");

        headerPanel.add(lblLogo, BorderLayout.WEST);
        headerPanel.add(lblStatus, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // --- PANEL CENTRAL (DASHBOARD GRID) ---
        JPanel mainGrid = new JPanel(new GridBagLayout());
        mainGrid.setOpaque(false);
        mainGrid.setBorder(new EmptyBorder(0, 20, 15, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 8, 8, 8);

        // 1. Recursos del Sistema (Equivalente a getSystemResourcesPanel)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.5; gbc.weighty = 0.3;
        txtHardware = createTerminalArea(" RECURSOS DEL SISTEMA (HARDWARE) ", YELLOW_ANSI);
        mainGrid.add(new JScrollPane(txtHardware), gbc);

        // 2. Dashboard de Red (Equivalente a getNetworkDashboard)
        gbc.gridx = 1; gbc.gridy = 0;
        txtNetwork = createTerminalArea(" MÉTRICAS DE RED Y FLUJO DE DATOS ", CYAN_ANSI);
        mainGrid.add(new JScrollPane(txtNetwork), gbc);

        // 3. Monitor de Hilos (Tabla detallada como displayNodesTable pero para Threads)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weighty = 0.7;
        mainGrid.add(createThreadTablePanel(), gbc);

        // 4. Telemetría de Eventos (Equivalente a getActivityLog)
        gbc.gridx = 1; gbc.gridy = 1;
        txtTelemetry = createTerminalArea(" TELEMETRÍA DE EVENTOS (REAL-TIME) ", PURPLE_ANSI);
        mainGrid.add(new JScrollPane(txtTelemetry), gbc);

        add(mainGrid, BorderLayout.CENTER);
    }

    private JTextArea createTerminalArea(String title, Color accentColor) {
        JTextArea area = new JTextArea();
        area.setBackground(PANEL_DARK_COLOR());
        area.setForeground(TEXT_WHITE);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setEditable(false);
        area.setMargin(new Insets(10, 10, 10, 10));
        area.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(accentColor, 1), title, 0, 0, null, accentColor));
        return area;
    }

    private JPanel createThreadTablePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(GREEN_ANSI, 1), " MONITOR DE HILOS BitBridge ", 0, 0, null, GREEN_ANSI));

        threadTableModel = new DefaultTableModel(new String[]{"ID", "IDENTIFICADOR", "ESTADO", "PRIORIDAD"}, 0);
        JTable table = new JTable(threadTableModel);

        // Estilo Tabla Terminal
        table.setBackground(PANEL_DARK_COLOR());
        table.setForeground(Color.WHITE);
        table.setGridColor(new Color(50, 50, 50));
        table.setRowHeight(25);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.getTableHeader().setBackground(new Color(30, 30, 30));
        table.getTableHeader().setForeground(GREEN_ANSI);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                if (c == 2) { // Colorear Estado
                    String val = v.toString();
                    if (val.equals("RUNNABLE")) comp.setForeground(GREEN_ANSI);
                    else if (val.contains("WAIT")) comp.setForeground(YELLOW_ANSI);
                    else comp.setForeground(Color.GRAY);
                }
                return comp;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(PANEL_DARK_COLOR());
        p.add(scroll);
        return p;
    }

    private void refreshData() {
        // --- HARDWARE PANEL UPDATE ---
        Runtime r = Runtime.getRuntime();
        String hwText = String.format(
                " HOST:    %-15s | SO: %s (%s)\n" +
                        " JVM:     %-15s | VER: %s\n" +
                        " MEMORIA: %-15s | HILOS: %d\n" +
                        " LÍMITE:  %-15s | CORES: %d",
                NetworkManager.getLocalIp(), System.getProperty("os.name"), System.getProperty("os.arch"),
                System.getProperty("java.vendor"), System.getProperty("java.version"),
                stats.formatBytes(r.totalMemory() - r.freeMemory()), Thread.activeCount(),
                stats.getMaxMemoryFormat(), r.availableProcessors()
        );
        txtHardware.setText(hwText);

        // --- NETWORK PANEL UPDATE ---
        String netText = String.format(
                " PUNTO ACCESO: %s:%d\n" +
                        " TOTAL MSGS : %d\n" +
                        " TRÁFICO    : %s\n" +
                        " UPTIME     : %s\n" +
                        " CLIENTES   : %d NODOS ACTIVOS",
                localIp, port, stats.getTotalMessages(), stats.formatBytes(stats.getTotalBytes()),
                stats.getUptime(), stats.getClientCount()
        );
        txtNetwork.setText(netText);

        // --- THREAD TABLE UPDATE ---
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
        threadTableModel.setRowCount(0);
        for (Thread t : allThreads.keySet()) {
            String name = t.getName();
            if (name.contains("MSG") || name.contains("FT") || name.contains("BitBridge")) {
                threadTableModel.addRow(new Object[]{t.getId(), name, t.getState().toString(), t.getPriority()});
            }
        }

        // --- TELEMETRY UPDATE ---
        StringBuilder log = new StringBuilder();
        List<String> history = stats.getMessageHistory();
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            log.append(" [").append(i + 1).append("] ").append(history.get(i)).append("\n");
        }
        txtTelemetry.setText(log.toString());
    }

    private void startMonitoring() {
        guiTimer = new Timer(1000, e -> refreshData());
        guiTimer.start();
    }

    private Color PANEL_DARK_COLOR() { return new Color(25, 30, 35); }
}
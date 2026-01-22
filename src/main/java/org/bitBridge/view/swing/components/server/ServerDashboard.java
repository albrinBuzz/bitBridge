package org.bitBridge.view.swing.components.server;

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
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Map;

public class ServerDashboard extends JFrame {
    private static final Color BG_DARK = new Color(13, 15, 18);
    private static final Color PANEL_DARK = new Color(25, 30, 35);
    private static final Color NEON_PURPLE = new Color(162, 155, 254);
    private static final Color NEON_CYAN = new Color(0, 206, 201);
    private static final Color NEON_GREEN = new Color(0, 184, 148);
    private static final Color NEON_YELLOW = new Color(253, 203, 110);
    private static final Color DANGER_RED = new Color(231, 76, 60);

    private final ServerStats stats;
    private final int port;
    private final String localIp;

    private JLabel lblHardwareLeft, lblHardwareRight, lblSoftwareBlock, lblNetworkBlock;
    private JProgressBar ramBar;
    private DefaultTableModel threadModel, clientTableModel;
    private JTextArea txtTelemetry;
    private Timer guiTimer;

    public ServerDashboard(Server server) {
        this.stats = server.getStats();
        this.port = server.getPORT();
        this.localIp = NetworkManager.getLocalIp();

        setTitle("BitBridge | HUB OPERATOR PRO [v4.0]");
        setSize(1500, 950);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(15, 15));

        initUI();
        startMonitoring();
    }

    private void initUI() {
        // --- HEADER ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(20, 25, 10, 25));

        JLabel title = new JLabel("<html>BITBRIDGE <font color='#a29bfe'>OPERATOR COMMAND</font></html>");
        title.setFont(new Font("Monospaced", Font.BOLD, 28));
        title.setForeground(Color.WHITE);

        JPanel infoHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 30, 0));
        infoHeader.setOpaque(false);
        infoHeader.add(createHeaderMetric("CORE ENGINE", "v4.0.2-STABLE", NEON_PURPLE));
        infoHeader.add(createHeaderMetric("UPTIME", stats.getUptime(), NEON_GREEN));
        header.add(title, BorderLayout.WEST);
        header.add(infoHeader, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // --- MAIN GRID ---
        JPanel mainGrid = new JPanel(new GridBagLayout());
        mainGrid.setOpaque(false);
        mainGrid.setBorder(new EmptyBorder(0, 25, 20, 25));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 8, 8, 8);

        // FILA 1: HARDWARE & SOFTWARE (Top Level)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.6; gbc.weighty = 0.35;
        mainGrid.add(createExtendedHardwarePanel(), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.4;
        mainGrid.add(createSoftwareNetworkPanel(), gbc);

        // FILA 2: THREADS & NODES
        gbc.gridx = 0; gbc.gridy = 1; gbc.weighty = 0.65;
        mainGrid.add(createThreadInspectorPanel(), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        mainGrid.add(createRightPanel(), gbc);

        add(mainGrid, BorderLayout.CENTER);
    }

    private JPanel createExtendedHardwarePanel() {
        JPanel p = new JPanel(new BorderLayout(15, 15));
        p.setBackground(PANEL_DARK);
        p.setBorder(BorderFactory.createCompoundBorder(new LineBorder(NEON_YELLOW, 1), new EmptyBorder(15, 15, 15, 15)));

        JPanel grid = new JPanel(new GridLayout(1, 2, 20, 0));
        grid.setOpaque(false);

        lblHardwareLeft = new JLabel();
        lblHardwareRight = new JLabel();
        grid.add(lblHardwareLeft);
        grid.add(lblHardwareRight);

        // RAM Meter
        ramBar = new JProgressBar(0, 100);
        ramBar.setStringPainted(true);
        ramBar.setPreferredSize(new Dimension(0, 28));
        ramBar.setBackground(new Color(30, 35, 40));
        ramBar.setForeground(NEON_GREEN);
        ramBar.setBorder(new LineBorder(BG_DARK, 1));

        p.add(grid, BorderLayout.CENTER);
        p.add(ramBar, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createSoftwareNetworkPanel() {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 10));
        p.setOpaque(false);

        lblSoftwareBlock = createInfoLabel(NEON_PURPLE, "SOFTWARE ENVIRONMENT");
        lblNetworkBlock = createInfoLabel(NEON_CYAN, "NETWORK FLOW");

        p.add(lblSoftwareBlock);
        p.add(lblNetworkBlock);
        return p;
    }

    private JLabel createInfoLabel(Color accent, String title) {
        JLabel lbl = new JLabel();
        lbl.setOpaque(true);
        lbl.setBackground(PANEL_DARK);
        lbl.setVerticalAlignment(SwingConstants.TOP);
        lbl.setBorder(BorderFactory.createCompoundBorder(new LineBorder(accent, 1), new EmptyBorder(10, 12, 10, 12)));
        return lbl;
    }

    private JPanel createThreadInspectorPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL_DARK);
        p.setBorder(BorderFactory.createTitledBorder(new LineBorder(NEON_GREEN, 1), " JVM CORE THREADS ", 0, 0, null, NEON_GREEN));

        threadModel = new DefaultTableModel(new String[]{"ID", "IDENTIFICADOR", "ESTADO", "PRIO", "DAEMON"}, 0);
        JTable table = new JTable(threadModel);
        styleTable(table);
        table.getColumnModel().getColumn(2).setCellRenderer(new ThreadStatusRenderer());
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private JPanel createRightPanel() {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 10));
        p.setOpaque(false);

        txtTelemetry = new JTextArea();
        txtTelemetry.setBackground(BG_DARK);
        txtTelemetry.setForeground(NEON_PURPLE);
        txtTelemetry.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollTele = new JScrollPane(txtTelemetry);
        scrollTele.setBorder(BorderFactory.createTitledBorder(new LineBorder(NEON_PURPLE, 1), " TELEMETRY LOG ", 0, 0, null, NEON_PURPLE));

        clientTableModel = new DefaultTableModel(new String[]{"ADDR", "SESSION", "NICK", "STATUS"}, 0);
        JTable nodeTable = new JTable(clientTableModel);
        styleTable(nodeTable);
        JScrollPane scrollNodes = new JScrollPane(nodeTable);
        scrollNodes.setBorder(BorderFactory.createTitledBorder(new LineBorder(NEON_CYAN, 1), " ACTIVE NODES ", 0, 0, null, NEON_CYAN));

        p.add(scrollTele);
        p.add(scrollNodes);
        return p;
    }

    private void styleTable(JTable table) {
        table.setBackground(PANEL_DARK);
        table.setForeground(Color.WHITE);
        table.setRowHeight(25);
        table.setGridColor(new Color(40, 45, 50));
        table.setFont(new Font("Monospaced", Font.PLAIN, 11));
        table.getTableHeader().setBackground(new Color(30, 35, 40));
        table.getTableHeader().setForeground(NEON_CYAN);
    }

    private JLabel createHeaderMetric(String title, String val, Color color) {
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return new JLabel("<html><div style='text-align: right;'><font color='gray' size='2'>" + title + "</font><br>" +
                "<font color='" + hex + "' size='4'><b>" + val + "</b></font></div></html>");
    }
    private void refreshData() {
        Runtime r = Runtime.getRuntime();
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
        long usedMem = r.totalMemory() - r.freeMemory();
        int ramPercent = (int) ((usedMem * 100) / r.maxMemory());
        int clients = stats.getClientCount();
        //int totalThreads = Thread.activeCount();
        int totalThreads= allThreads.size();
        // --- LÓGICA DE SALUD ---
        String healthText;
        String healthColor;
        if (clients > totalThreads * 0.8) {
            healthText = "ESTRESADO";
            healthColor = "#e74c3c"; // DANGER_RED
        } else {
            healthText = "ESTABLE";
            healthColor = "#55efc4"; // NEON_GREEN
        }

        // --- CÁLCULO DE CARGA DE HILOS ---
        // Definimos un límite teórico basado en cores (ej. 200 hilos por core)
        int maxExpectedThreads = r.availableProcessors() * 200;
        double threadLoad = (totalThreads * 100.0) / maxExpectedThreads;
        String loadColor = (threadLoad > 70) ? "#fdcb6e" : (threadLoad > 90 ? "#e74c3c" : "#55efc4");

        // 1. HARDWARE ASSETS (Izquierda) - Agregamos Carga y Total
        lblHardwareLeft.setText(String.format(
                "<html><font color='#fdcb6e'><b>HARDWARE ASSETS & SYSTEM</b></font><br>" +
                        "<table style='color:white; font-family:Sans-Serif; font-size:10px;'>" +
                        "<tr><td>MODELO ARCH:</td><td><b color='white'>%s (%s bits)</b></td></tr>" +
                        "<tr><td>CPU CORES:</td><td><b color='#00cec9'>%d</b> Lógicos</td></tr>" +
                        "<tr><td>CARGA HILOS:</td><td><b color='%s'>%.1f%%</b></td></tr>" + // <-- NUEVO
                        "<tr><td>VM TOTAL:</td><td>%s</td></tr>" +
                        "<tr><td>VM LÍMITE:</td><td><b color='#e74c3c'>%s</b></td></tr>" +
                        "<tr><td>MEM. LIBRE:</td><td><b color='#55efc4'>%s</b></td></tr>" +
                        "<tr><td>USER:</td><td>%s</td></tr>" +
                        "</table></html>",
                System.getProperty("os.arch"), System.getProperty("sun.arch.data.model"),
                r.availableProcessors(), loadColor, threadLoad,
                stats.formatBytes(r.totalMemory()),
                stats.getMaxMemoryFormat(),
                stats.formatBytes(r.freeMemory()),
                System.getProperty("user.name")
        ));

        // 2. PROCESS MONITOR (Derecha) - Agregamos desglose de hilos totales
        int bitBridgeThreads = 0;
        int daemonThreads = 0;

        threadModel.setRowCount(0);

        for (Thread t : allThreads.keySet()) {
            if (t.isDaemon()) daemonThreads++;
            String name = t.getName();
            if (name.contains("Worker") || name.contains("BitBridge") || name.contains("FT-Pool")) {
                bitBridgeThreads++;
            }
            threadModel.addRow(new Object[]{
                    t.getId(),
                    name.toUpperCase(),
                    t.getState(),
                    t.getPriority(),
                    t.isDaemon() ? "DAEMON" : "USER"
            });
        }

        lblHardwareRight.setText(String.format(
                "<html><font color='#fdcb6e'><b>PROCESS & RUNTIME</b></font><br>" +
                        "<table style='color:white; font-family:Sans-Serif; font-size:10px;'>" +
                        "<tr><td>BITBRIDGE WK:</td><td><b color='#55efc4'>%d ACTIVOS</b></td></tr>" +
                        "<tr><td>TOTAL HILOS:</td><td><b color='#00cec9'>%d</b></td></tr>" + // <-- NUEVO
                        "<tr><td>TIPO HILOS:</td><td>D: %d / U: %d</td></tr>" +
                        "<tr><td>HOST IP:</td><td><b color='#a29bfe'>%s</b></td></tr>" +
                        "<tr><td>OS VERSION:</td><td>%s</td></tr>" +
                        "<tr><td>JAVA HOME:</td><td>.../%s</td></tr>" +
                        "<tr><td>ESTADO:</td><td><b color='#55efc4'>SINCRONIZADO</b></td></tr>" +
                        "</table></html>",
                bitBridgeThreads,
                totalThreads, daemonThreads, (totalThreads - daemonThreads),
                localIp,
                System.getProperty("os.version"),
                new java.io.File(System.getProperty("java.home")).getName()
        ));

        // 3. SOFTWARE BLOCK (Sin cambios, manteniendo estética)
        lblSoftwareBlock.setText("<html><font color='#a29bfe'><b>SOFTWARE ENVIRONMENT</b></font><br>" +
                "<font color='white' size='3'>JVM: " + System.getProperty("java.vendor") + "</font><br>" +
                "<font color='gray' size='2'>VER: " + System.getProperty("java.version") + " (" + rb.getVmVersion() + ")</font><br>" +
                "<font color='gray' size='2'>SPEC: " + rb.getSpecName() + "</font></html>");

        // 4. NETWORK BLOCK (Manteniendo tu estructura de tabla avanzada)
        String netHTML = String.format(
                "<html>" +
                        "<div style='margin-bottom: 5px;'><font color='#00cec9' size='4'><b>MÉTRICAS DE RED Y FLUJO</b></font></div>" +
                        "<table style='color: white; font-family: Monospaced; font-size: 11px;'>" +
                        "<tr>" +
                        "<td><font color='gray'>PUNTO ACCESO :</font></td>" +
                        "<td><b color='#55efc4'>%s:%d</b></td>" +
                        "<td style='padding-left:15px;'><font color='gray'>INTERFAZ :</font></td>" +
                        "<td><font color='#fdcb6e'>%s</font></td>" +
                        "</tr>" +
                        "<tr>" +
                        "<td><font color='gray'>DIRECCIÓN IP :</font></td>" +
                        "<td>%s</td>" +
                        "<td style='padding-left:15px;'><font color='gray'>PUERTO   :</font></td>" +
                        "<td><font color='#fdcb6e'>%d</font></td>" +
                        "</tr>" +
                        "<tr>" +
                        "<td><font color='gray'>ESTADÍSTICA  :</font></td>" +
                        "<td>MSG: <font color='#00cec9'>%d</font></td>" +
                        "<td style='padding-left:15px;'><font color='gray'>SALUD    :</font></td>" +
                        "<td><b color='%s'>%s</b></td>" +
                        "</tr>" +
                        "<tr>" +
                        "<td><font color='gray'>RENDIMIENTO  :</font></td>" +
                        "<td>UP: <font color='#a29bfe'>%s</font></td>" +
                        "<td style='padding-left:15px;'><font color='gray'>TOTAL    :</font></td>" +
                        "<td><font color='#a29bfe'>%s</font></td>" +
                        "</tr>" +
                        "<tr>" +
                        "<td><font color='gray'>CONEXIONES   :</font></td>" +
                        "<td><b color='#a29bfe'>%d NODOS</b></td>" +
                        "<td style='padding-left:15px;'><font color='gray'>MTU      :</font></td>" +
                        "<td>1500 (Auto)</td>" +
                        "</tr>" +
                        "</table>" +
                        "</html>",
                localIp, port, getActiveInterface(),
                localIp, port,
                stats.getTotalMessages(), healthColor, healthText,
                stats.getUptime(), stats.formatBytes(stats.getTotalBytes()),
                clients
        );
        lblNetworkBlock.setText(netHTML);

        // 5. RAM BAR
        ramBar.setValue(ramPercent);
        ramBar.setString("HEAP USAGE: " + ramPercent + "% (" + stats.formatBytes(usedMem) + ")");
        if (ramPercent > 80) ramBar.setForeground(DANGER_RED); else ramBar.setForeground(NEON_GREEN);

        // Logs & Nodes
        txtTelemetry.setText("");
        List<String> logs = stats.getMessageHistory();
        int start = Math.max(0, logs.size() - 10);
        for (int i = start; i < logs.size(); i++) txtTelemetry.append(" > " + logs.get(i) + "\n");

        clientTableModel.setRowCount(0);
        for (ClientInfo c : stats.getConnectedClients()) {
            clientTableModel.addRow(new Object[]{c.getAddress(), "ACT", c.getNick().toUpperCase(), "ONLINE"});
        }
    }

    private void startMonitoring() {
        guiTimer = new Timer(1000, e -> refreshData());
        guiTimer.start();
    }

    private String getActiveInterface() {
        // Retorna el nombre de la interfaz (ej. eth0, wlan0, enp4s0)
        // Si ya tienes un NetworkManager, úsalo aquí.
        return "enp4s0"; // Placeholder basado en tu imagen
    }

    static class ThreadStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String state = value != null ? value.toString() : "";
            if (state.equals("RUNNABLE")) c.setForeground(NEON_GREEN);
            else if (state.contains("WAITING")) c.setForeground(NEON_YELLOW);
            else c.setForeground(Color.GRAY);
            return c;
        }
    }
}
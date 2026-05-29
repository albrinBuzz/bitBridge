package org.bitBridge.view.swing.components.server;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.Server;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.shared.network.NetworkDiagnosticEngine;
import org.bitBridge.shared.network.NetworkManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
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

    // Define estas constantes en tu clase
    private static final Color BG_AREA = new Color(5, 5, 10);
    private static final Color ACCENT_CYAN = new Color(0, 255, 200);
    private static final Color ACCENT_PURPLE = new Color(170, 100, 255);
    private static final Color ACCENT_YELLOW = new Color(255, 215, 0);
    private static final Color TEXT_DIM = new Color(150, 160, 180);

    private final ServerStats stats;
    private final int port;
    private String currentPrimaryIp; // Cambiado a dinámico

    private JLabel lblHardwareLeft, lblHardwareRight, lblSoftwareBlock, lblNetworkBlock;
    private JProgressBar ramBar;
    private DefaultTableModel threadModel, clientTableModel;
    private JTextArea txtTelemetry;
    private JTextPane txtNetworkTopology;
    private Timer guiTimer;

    // En la clase principal
    private JLabel lblTrafficMonitor;
    private long lastTotalBytes = 0;
    private double currentKbs = 0;
    private long lastTimestamp = System.currentTimeMillis();


    // Añádelo a tu panel de métricas o a una zona visible del dashboard
    public ServerDashboard(Server server) {
        this.stats = server.getStats();
        this.port = server.getPORT();

        // Inicializamos con la primera IP disponible
        List<InetAddress> initialIps = NetworkManager.getAllLocalIps();
        this.currentPrimaryIp = initialIps.isEmpty() ? "127.0.0.1" : initialIps.get(0).getHostAddress();

        setTitle("BitBridge | HUB OPERATOR PRO [v4.0]");
        setSize(1500, 950);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(15, 15));

        initUI();
        updateNetworkTopology();
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

        header.add(title, BorderLayout.WEST);
        header.add(infoHeader, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        lblTrafficMonitor = new JLabel();
        lblTrafficMonitor.setFont(new Font("Monospaced", Font.BOLD, 15)); // Un poco más grande para el header
        lblTrafficMonitor.setForeground(NEON_CYAN);

        JButton btnDeepAudit = new JButton("DEEP AUDIT");
        btnDeepAudit.setFont(new Font("Monospaced", Font.BOLD, 12));
        btnDeepAudit.setBackground(BG_DARK);
        btnDeepAudit.setForeground(NEON_CYAN);
        btnDeepAudit.setBorder(new LineBorder(NEON_CYAN, 1));
        btnDeepAudit.addActionListener(e -> showDiagnosticDialog());

// Añádelo al panel que prefieras

        infoHeader.add(lblTrafficMonitor); // <--- NUEVO: Monitor de Tráfico en vivo
        infoHeader.add(btnDeepAudit);
        infoHeader.add(createHeaderMetric("CORE ENGINE", "v4.0.2-STABLE", NEON_PURPLE));
        infoHeader.add(createHeaderMetric("UPTIME", stats.getUptime(), NEON_GREEN));

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
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // --- BLOQUE 1: TOPOLOGÍA DE RED (DOMINANTE) ---
        txtNetworkTopology = new JTextPane();
        txtNetworkTopology.setBackground(BG_DARK);
        txtNetworkTopology.setForeground(new Color(85, 239, 196)); // NEON_GREEN
        // Aumentamos un poco la fuente para legibilidad inmediata
        txtNetworkTopology.setFont(new Font("Monospaced", Font.BOLD, 13));
        txtNetworkTopology.setEditable(false);
        // Margen interno para que el texto no pegue a los bordes
        txtNetworkTopology.setMargin(new java.awt.Insets(10, 10, 10, 10));

        JScrollPane scrollNet = new JScrollPane(txtNetworkTopology);
        scrollNet.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(new Color(0, 206, 201), 2), " NETWORK TOPOLOGY MAP ", 0, 0, null, new Color(0, 206, 201)));

        // Forzamos un tamaño mínimo para que sea lo primero que se vea
        scrollNet.setPreferredSize(new java.awt.Dimension(450, 400));

        gbc.gridy = 0;
        gbc.weighty = 0.55; // 55% del espacio vertical para la red
        gbc.insets = new java.awt.Insets(0, 0, 15, 0); // Espacio extra abajo
        p.add(scrollNet, gbc);

        // --- BLOQUE 2: TELEMETRÍA (COMPACTO) ---
        txtTelemetry = new JTextArea();
        txtTelemetry.setBackground(BG_DARK);
        txtTelemetry.setForeground(NEON_PURPLE);
        txtTelemetry.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JScrollPane scrollTele = new JScrollPane(txtTelemetry);
        scrollTele.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(NEON_PURPLE, 1), " TELEMETRY LOG ", 0, 0, null, NEON_PURPLE));

        gbc.gridy = 1;
        gbc.weighty = 0.20; // Reducimos a 20% (son logs rápidos)
        gbc.insets = new java.awt.Insets(0, 0, 15, 0);
        p.add(scrollTele, gbc);

        // --- BLOQUE 3: NODOS ACTIVOS (RESTANTE) ---
        clientTableModel = new DefaultTableModel(new String[]{"ADDR", "SESSION", "NICK", "STATUS"}, 0);
        JTable nodeTable = new JTable(clientTableModel);
        styleTable(nodeTable);

        JScrollPane scrollNodes = new JScrollPane(nodeTable);
        scrollNodes.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(NEON_CYAN, 1), " ACTIVE NODES ", 0, 0, null, NEON_CYAN));

        gbc.gridy = 2;
        gbc.weighty = 0.25; // 25% para la tabla de clientes
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        p.add(scrollNodes, gbc);

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


    private void updateNetworkTopology() {
        txtNetworkTopology.setText("");
        List<InetAddress> allLocalIps = NetworkManager.getAllLocalIps();
        String primaryIp = allLocalIps.isEmpty() ? "127.0.0.1" : allLocalIps.get(0).getHostAddress();

        // Paleta de Colores Estratégica
        Color colEth = new Color(46, 204, 113);    // Verde (Ethernet - Estable)
        Color colWifi = new Color(241, 196, 15);   // Amarillo/Oro (Wi-Fi)
        Color colVirt = new Color(149, 165, 166);  // Gris (Virtual/Loopback)
        Color colPrimary = new Color(255, 118, 117); // Rojo Coral (IP ACTIVA)
        Color colInfo = new Color(0, 206, 201);    // Cian (Labels)
        Color colText = new Color(223, 230, 233);  // Blanco humo (Texto general)

        appendPane(" ╔══════════════════════════════════════════════════════════╗\n", colInfo);
        appendPane(" ║  BITBRIDGE Topologia De Red - HARDWARE         ║\n", colInfo);
        appendPane(" ╚══════════════════════════════════════════════════════════╝\n\n", colInfo);

        try {
            java.util.Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : java.util.Collections.list(nets)) {
                if (!ni.isUp()) continue;

                String name = ni.getName().toUpperCase();
                String display = ni.getDisplayName();
                boolean isVirtual = ni.isVirtual() || name.matches(".*(VBOX|DOCKER|VETH|VIRBR).*");

                // --- DETECCIÓN DE TECNOLOGÍA Y COLOR DE BLOQUE ---
                String typeStr;
                Color blockColor;

                if (name.startsWith("EN") || name.startsWith("ETH")) {
                    typeStr = "🔌 [ETHERNET]";
                    blockColor = colEth;
                } else if (name.startsWith("WL")) {
                    typeStr = "📶 [WI-FI]";
                    blockColor = colWifi;
                } else if (isVirtual) {
                    typeStr = "📦 [VIRTUAL]";
                    blockColor = colVirt;
                } else if (ni.isLoopback()) {
                    typeStr = "🔄 [LOOPBACK]";
                    blockColor = colVirt;
                } else {
                    typeStr = "🌐 [NETWORK]";
                    blockColor = colInfo;
                }

                // --- RENDERIZADO DE CABECERA ---
                appendPane(" " + typeStr + " ", blockColor);
                appendPane(String.format("%-10s", name), Color.WHITE);
                appendPane(String.format(" | MTU: %-5d", ni.getMTU()), colVirt);
                appendPane(" ● ONLINE\n", colEth);

                appendPane("  id: ", colVirt);
                appendPane(display + "\n", colText);

                // --- RENDERIZADO DE IPs ---
                java.util.List<InetAddress> addresses = java.util.Collections.list(ni.getInetAddresses());
                for (int i = 0; i < addresses.size(); i++) {
                    InetAddress addr = addresses.get(i);
                    if (!(addr instanceof Inet4Address)) continue;

                    String ip = addr.getHostAddress();
                    boolean isPrimary = ip.equals(primaryIp);
                    String branch = (i == addresses.size() - 1) ? "  └─ " : "  ├─ ";

                    appendPane(branch, colVirt);
                    if (isPrimary) {
                        appendPane("IPv4: ", colInfo);
                        appendPane(String.format("%-15s", ip), colPrimary);
                        appendPane(" <--- Conexion Primaria\n", colPrimary);
                    } else {
                        appendPane("IPv4: ", colVirt);
                        appendPane(ip + "\n", Color.WHITE);
                    }
                }
                appendPane("\n", Color.WHITE);
            }
        } catch (Exception e) {
            appendPane(" [!] ERROR AL ACCEDER A INTERFACES DE RED\n", Color.RED);
        }
        txtNetworkTopology.setCaretPosition(0);
    }

    // Método auxiliar para escribir con colores en el JTextPane
    private void appendPane(String msg, Color c) {
        javax.swing.text.StyleContext sc = javax.swing.text.StyleContext.getDefaultStyleContext();
        javax.swing.text.AttributeSet aset = sc.addAttribute(javax.swing.text.SimpleAttributeSet.EMPTY, javax.swing.text.StyleConstants.Foreground, c);
        int len = txtNetworkTopology.getDocument().getLength();
        try {
            txtNetworkTopology.getDocument().insertString(len, msg, aset);
        } catch (Exception e) {}
    }

    private void refreshData() {
        Runtime r = Runtime.getRuntime();
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        updateLiveTraffic();

        List<InetAddress> allLocalIps = NetworkManager.getAllLocalIps();
        String interfaz=getActiveInterface(allLocalIps);
        // --- LÓGICA DE MEMORIA ---
        long heapUsed = r.totalMemory() - r.freeMemory();
        long directMemUsed = 0;
        try {
            for (java.lang.management.BufferPoolMXBean pool : java.lang.management.ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class)) {
                if (pool.getName().equals("direct")) directMemUsed = pool.getMemoryUsed();
            }
        } catch (Exception ignored) {}

        long totalRealUsed = heapUsed + directMemUsed;
        int ramPercent = (int) ((totalRealUsed * 100) / r.maxMemory());

        // --- HILOS Y ESTADO ---
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
        int totalThreads = allThreads.size();
        int clients = stats.getClientCount();

        String healthText = (clients > totalThreads * 0.8) ? "ESTRESADO" : "ESTABLE";
        String healthColor = (clients > totalThreads * 0.8) ? "#e74c3c" : "#55efc4";

        int maxExpectedThreads = r.availableProcessors() * 200;
        double threadLoad = (totalThreads * 100.0) / maxExpectedThreads;
        String loadColor = (threadLoad > 90) ? "#e74c3c" : (threadLoad > 70 ? "#fdcb6e" : "#55efc4");

        // --- ACTUALIZACIÓN DE LABELS ---

        // 1. Hardware Left
        lblHardwareLeft.setText(String.format(
                "<html><font color='#fdcb6e'><b>HARDWARE ASSETS & SYSTEM</b></font><br>" +
                        "<table style='color:white; font-family:Sans-Serif; font-size:10px;'>" +
                        "<tr><td>CPU CORES:</td><td><b color='#00cec9'>%d</b> Lógicos</td></tr>" +
                        "<tr><td>CARGA HILOS:</td><td><b color='%s'>%.1f%%</b></td></tr>" +
                        "<tr><td>HEAP USED:</td><td>%s</td></tr>" +
                        "<tr><td>NIO DIRECT:</td><td><b color='#a29bfe'>%s</b></td></tr>" +
                        "<tr><td>VM LÍMITE:</td><td><b color='#e74c3c'>%s</b></td></tr>" +
                        "<tr><td>MEM. LIBRE:</td><td><b color='#55efc4'>%s</b></td></tr>" +
                        "<tr><td>USER:</td><td>%s</td></tr>" +
                        "</table></html>",
                r.availableProcessors(), loadColor, threadLoad,
                stats.formatBytes(heapUsed), stats.formatBytes(directMemUsed),
                stats.getMaxMemoryFormat(), stats.formatBytes(r.freeMemory()),
                System.getProperty("user.name")
        ));

        // 2. Process Monitor (Derecha)
        int bitBridgeThreads = 0;
        int daemonThreads = 0;
        threadModel.setRowCount(0);
        for (Thread t : allThreads.keySet()) {
            if (t.isDaemon()) daemonThreads++;
            if (t.getName().matches(".*(Worker|BitBridge|FT-Pool).*")) bitBridgeThreads++;
            threadModel.addRow(new Object[]{t.getId(), t.getName().toUpperCase(), t.getState(), t.getPriority(), t.isDaemon() ? "DAEMON" : "USER"});
        }

        lblHardwareRight.setText(String.format(
                "<html><font color='#fdcb6e'><b>PROCESS & RUNTIME</b></font><br>" +
                        "<table style='color:white; font-family:Sans-Serif; font-size:10px;'>" +
                        "<tr><td>BITBRIDGE WK:</td><td><b color='#55efc4'>%d ACTIVOS</b></td></tr>" +
                        "<tr><td>TOTAL HILOS:</td><td><b color='#00cec9'>%d</b></td></tr>" +
                        "<tr><td>TIPO HILOS:</td><td>D: %d / U: %d</td></tr>" +
                        "<tr><td>HOST IP:</td><td><b color='#a29bfe'>%s</b></td></tr>" +
                        "<tr><td>OS VERSION:</td><td>%s</td></tr>" +
                        "<tr><td>ESTADO:</td><td><b color='%s'>%s</b></td></tr>" +
                        "</table></html>",
                bitBridgeThreads, totalThreads, daemonThreads, (totalThreads - daemonThreads),
                currentPrimaryIp, System.getProperty("os.version"), healthColor, healthText
        ));

        // 3. Network Block
        lblNetworkBlock.setText(String.format(
                "<html><div style='margin-bottom: 5px;'><font color='#00cec9' size='4'><b>MÉTRICAS DE RED Y FLUJO</b></font></div>" +
                        "<table style='color: white; font-family: Monospaced; font-size: 11px;'>" +
                        "<tr><td><font color='gray'>PUNTO ACCESO :</font></td><td><b color='#55efc4'>%s:%d</b></td>" +
                        "<td style='padding-left:15px;'><font color='gray'>INTERFAZ :</font></td><td><font color='#fdcb6e'>%s</font></td></tr>" +
                        "<tr><td><font color='gray'>DIRECCIÓN IP :</font></td><td>%s</td>" +
                        "<td style='padding-left:15px;'><font color='gray'>PUERTO :</font></td><td><font color='#fdcb6e'>%d</font></td></tr>" +
                        "<tr><td><font color='gray'>ESTADÍSTICA :</font></td><td>MSG: <font color='#00cec9'>%d</font></td>" +
                        "<td style='padding-left:15px;'><font color='gray'>SALUD :</font></td><td><b color='%s'>%s</b></td></tr>" +
                        "<tr><td><font color='gray'>RENDIMIENTO :</font></td><td>UP: <font color='#a29bfe'>%s</font></td>" +
                        "<td style='padding-left:15px;'><font color='gray'>TOTAL :</font></td><td><font color='#a29bfe'>%s</font></td></tr>" +
                        "<tr><td><font color='gray'>CONEXIONES :</font></td><td><b color='#a29bfe'>%d NODOS</b></td>" +
                        "<td style='padding-left:15px;'><font color='gray'>MTU :</font></td><td>1500 (Auto)</td></tr>" +
                        "</table></html>",
                currentPrimaryIp, port, interfaz, currentPrimaryIp, port,
                stats.getTotalMessages(), healthColor, healthText,
                stats.getUptime(), stats.formatBytes(stats.getTotalBytes()), clients
        ));


        //lblNetworkBlock.setToolTipText(ipTooltip.toString());

        // 4. Ram Bar
        ramBar.setValue(ramPercent);
        ramBar.setString(String.format("TOTAL RAM: %d%% (H: %s | D: %s)", ramPercent, stats.formatBytes(heapUsed), stats.formatBytes(directMemUsed)));
        ramBar.setForeground(ramPercent > 80 ? DANGER_RED : NEON_GREEN);


        if (ramPercent > 80) ramBar.setForeground(DANGER_RED);
        else ramBar.setForeground(NEON_GREEN);

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
        guiTimer = new Timer(1000, e -> {
            refreshData();        // Actualiza etiquetas de texto
            updateLiveTraffic();  // Actualiza el gráfico del header
        });
        guiTimer.start();
    }

    private String getActiveInterface(List<InetAddress> ips) {
        if (ips.isEmpty()) return "LOOPBACK";
        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(ips.get(0));
            return (ni != null) ? ni.getName().toUpperCase() : "UNKNOWN";
        } catch (SocketException e) {
            return "ERR_NET";
        }
    }

    private void updateLiveTraffic() {
        long currentBytes = stats.getTotalBytes();
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - lastTimestamp;

        if (timeDelta > 0) {
            double bytesPerSecond = (double) (currentBytes - lastTotalBytes) / (timeDelta / 1000.0);
            this.currentKbs = bytesPerSecond / 1024.0;
        }

        lastTotalBytes = currentBytes;
        lastTimestamp = currentTime;

        // Generar barra visual
        String bar = getTrafficBar(currentKbs);
        String color = (currentKbs > 800) ? "#e74c3c" : (currentKbs > 200 ? "#fdcb6e" : "#00cec9");

        lblTrafficMonitor.setText(String.format(
                "<html><font color='gray' size='3'>FLOW:</font> <font color='#00cec9'>%s</font> <b color='%s'>%.1f KB/s</b></html>",
                bar, color, currentKbs
        ));
    }

    private String getTrafficBar(double kbs) {
        int segments = 12; // Ajustado para que quepa bien en el header
        int filled = (int) Math.min(segments, (kbs / 1024.0) * segments);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? "■" : ".");
        }
        sb.append("]");
        return sb.toString();
    }

    private void showDiagnosticDialog() {
        // 1. Obtener auditoría rápida (Instantánea)
        String basicReport = NetworkDiagnosticEngine.getQuickAudit(currentPrimaryIp);

        // Configuración del área de texto (La Terminal)
        JTextArea area = new JTextArea(basicReport);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBackground(BG_AREA);
        area.setForeground(ACCENT_CYAN);
        area.setCaretColor(ACCENT_CYAN); // El cursor también brilla
        area.setEditable(false);
        area.setMargin(new Insets(20, 20, 20, 20));

        // Scroll con estilo personalizado
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(750, 500));
        scroll.setBorder(new LineBorder(new Color(40, 45, 60), 1)); // Borde sutil
        scroll.getVerticalScrollBar().setUnitIncrement(16); // Scroll suave

        // Panel de botones con mejor espaciado
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        actionPanel.setOpaque(false);

        JButton btnConnectivity = createDebugButton("⚡ TEST CONECTIVIDAD", ACCENT_PURPLE);
        JButton btnPortScan = createDebugButton("🚀 SCAN SUBRED", ACCENT_YELLOW);

        // Lógica con Auto-Scroll
        btnConnectivity.addActionListener(e -> {
            area.append("\n\n> [CMD]: Executing Layer-3 connectivity handshake...\n");
            new Thread(() -> {
                String res = NetworkDiagnosticEngine.runConnectivityTest();
                SwingUtilities.invokeLater(() -> {
                    area.append(res);
                    area.setCaretPosition(area.getDocument().getLength()); // Auto-scroll al final
                });
            }).start();
        });

        btnPortScan.addActionListener(e -> {
            area.append("\n\n> [CMD]: Escanenado Red Local \n");

            area.append("\n\n> [CMD]: Escanenado Red Local\n");
            new Thread(() -> {
                String res = NetworkDiagnosticEngine.runDeepPortScan(currentPrimaryIp);
                SwingUtilities.invokeLater(() -> {
                    area.append(res);
                    area.setCaretPosition(area.getDocument().getLength());
                });
            }).start();
        });

        actionPanel.add(btnConnectivity);
        actionPanel.add(btnPortScan);

        // Contenedor con Header visual
        JPanel mainPanel = new JPanel(new BorderLayout(0, 5));
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header decorativo
        JLabel header = new JLabel(" BITBRIDGE NETWORK TELEMETRY ");
        header.setFont(new Font("Monospaced", Font.BOLD, 14));
        header.setForeground(TEXT_DIM);
        header.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 0));

        mainPanel.add(header, BorderLayout.NORTH);
        mainPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(actionPanel, BorderLayout.SOUTH);

        // Mostrar el diálogo
        JOptionPane.showMessageDialog(this, mainPanel, "DEBUG CONSOLE", JOptionPane.PLAIN_MESSAGE);
    }

    private JButton createDebugButton(String text, Color accentColor) {
        JButton b = new JButton(text);
        b.setFont(new Font("Monospaced", Font.BOLD, 10));
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setBackground(BG_DARK);
        b.setForeground(accentColor);

        // Borde de línea con un poco de padding
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accentColor, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        // Efecto de brillo al pasar el mouse (Rollover)
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                b.setBackground(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 20));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                b.setBackground(BG_DARK);
            }
        });

        return b;
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
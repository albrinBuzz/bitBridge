package org.bitBridge.view.swing.components.server;

import org.bitBridge.shared.core.comunication.SocketPurpose;
import org.bitBridge.shared.core.comunication.model.basic.HandshakeMessage;
import org.bitBridge.shared.core.comunication.model.basic.TelemetryPacket;
import org.bitBridge.shared.network.ProtocolService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerDashboard extends JFrame {
    // --- PALETA DE COLORES HISTÓRICA LOCAL ---
    private static final Color BG_DARK = new Color(13, 15, 18);
    private static final Color PANEL_DARK = new Color(25, 30, 35);
    private static final Color NEON_PURPLE = new Color(162, 155, 254);
    private static final Color NEON_CYAN = new Color(0, 206, 201);
    private static final Color NEON_GREEN = new Color(0, 184, 148);
    private static final Color NEON_YELLOW = new Color(253, 203, 110);
    private static final Color DANGER_RED = new Color(231, 76, 60);

    private static final Color BG_AREA = new Color(5, 5, 10);
    private static final Color ACCENT_CYAN = new Color(0, 255, 200);
    private static final Color ACCENT_PURPLE = new Color(170, 100, 255);
    private static final Color ACCENT_YELLOW = new Color(255, 215, 0);
    private static final Color TEXT_DIM = new Color(150, 160, 180);

    // --- ELEMENTOS DE CONEXIÓN NIO ---
    private SocketChannel networkChannel;
    private Thread networkWorker;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // --- CONTROLES DE LA BARRA DE CONEXIÓN ---
    private JTextField txtHost, txtPort;
    private JButton btnConnect, btnDisconnect;

    // --- COMPONENTES DE RENDERIZADO VISUAL ---
    private JLabel lblTrafficMonitor, lblUptimeMetric, lblEngineMetric;
    private JLabel lblHardwareLeft, lblHardwareRight, lblSoftwareBlock, lblNetworkBlock;
    private JProgressBar ramBar;
    private DefaultTableModel threadModel, clientTableModel;
    private JTextArea txtTelemetry;
    private JTextPane txtNetworkTopology;
    private String currentPrimaryIp = "127.0.0.1";

    public ServerDashboard() {
        setTitle("BitBridge | HUB OPERATOR PRO [REMOTE TELEMETRY]");
        setSize(1500, 950);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(15, 15));

        initUI();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { disconnect(); }
        });
    }

    private void initUI() {
        // --- PANEL NORTE: BARRA DE CONTROL + METRICAS CABECERA ---
        JPanel northPanel = new JPanel(new BorderLayout(0, 10));
        northPanel.setOpaque(false);
        northPanel.setBorder(new EmptyBorder(15, 25, 5, 25));

        // Sub-barra superior de conexión
        JPanel connectionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        connectionBar.setBackground(PANEL_DARK);
        connectionBar.setBorder(new LineBorder(new Color(45, 52, 54), 1));

        JLabel lblTarget = new JLabel("TARGET HUB:");
        lblTarget.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblTarget.setForeground(Color.WHITE);

        txtHost = new JTextField("127.0.0.1", 12);
        txtHost.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtHost.setBackground(BG_AREA);
        txtHost.setForeground(NEON_CYAN);
        txtHost.setCaretColor(NEON_CYAN);

        txtPort = new JTextField("8080", 5);
        txtPort.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtPort.setBackground(BG_AREA);
        txtPort.setForeground(NEON_CYAN);

        btnConnect = new JButton("CONNECT ENGINE");
        btnConnect.setFont(new Font("Monospaced", Font.BOLD, 11));
        btnConnect.setBackground(BG_DARK);
        btnConnect.setForeground(NEON_GREEN);
        btnConnect.setBorder(new LineBorder(NEON_GREEN, 1));
        btnConnect.addActionListener(e -> connect(txtHost.getText().trim(), Integer.parseInt(txtPort.getText().trim())));

        btnDisconnect = new JButton("DISCONNECT");
        btnDisconnect.setFont(new Font("Monospaced", Font.BOLD, 11));
        btnDisconnect.setBackground(BG_DARK);
        btnDisconnect.setForeground(DANGER_RED);
        btnDisconnect.setBorder(new LineBorder(DANGER_RED, 1));
        btnDisconnect.setEnabled(false);
        btnDisconnect.addActionListener(e -> disconnect());

        connectionBar.add(lblTarget); connectionBar.add(txtHost);
        connectionBar.add(txtPort); connectionBar.add(btnConnect); connectionBar.add(btnDisconnect);

        // Header original de telemetría estética
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("<html>BITBRIDGE <font color='#a29bfe'>OPERATOR COMMAND</font></html>");
        title.setFont(new Font("Monospaced", Font.BOLD, 28));
        title.setForeground(Color.WHITE);

        JPanel infoHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 30, 0));
        infoHeader.setOpaque(false);

        lblTrafficMonitor = new JLabel("<html><font color='gray'>FLOW:</font> [............] <b>0.0 KB/s</b></html>");
        lblTrafficMonitor.setFont(new Font("Monospaced", Font.BOLD, 15));
        lblTrafficMonitor.setForeground(NEON_CYAN);

        lblEngineMetric = createHeaderMetric("CORE ENGINE", "OFFLINE", NEON_PURPLE);
        lblUptimeMetric = createHeaderMetric("UPTIME", "00:00:00", NEON_GREEN);

        infoHeader.add(lblTrafficMonitor);
        infoHeader.add(lblEngineMetric);
        infoHeader.add(lblUptimeMetric);

        header.add(title, BorderLayout.WEST);
        header.add(infoHeader, BorderLayout.EAST);

        northPanel.add(connectionBar, BorderLayout.NORTH);
        northPanel.add(header, BorderLayout.CENTER);
        add(northPanel, BorderLayout.NORTH);

        // --- GRID PRINCIPAL (4 BLOQUES ASIGNADOS LOCALES) ---
        JPanel mainGrid = new JPanel(new GridBagLayout());
        mainGrid.setOpaque(false);
        mainGrid.setBorder(new EmptyBorder(0, 25, 20, 25));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 8, 8, 8);

        // FILA 1: HARDWARE Y CONTEXTO SOFTWARE
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.6; gbc.weighty = 0.35;
        mainGrid.add(createExtendedHardwarePanel(), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.4;
        mainGrid.add(createSoftwareNetworkPanel(), gbc);

        // FILA 2: INSPECTOR DE HILOS Y SUB-PANELES DERECHOS
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

        lblHardwareLeft = new JLabel("<html><font color='#fdcb6e'><b>HARDWARE ASSETS & SYSTEM</b></font><br><font color='gray'>Await streaming...</font></html>");
        lblHardwareRight = new JLabel("<html><font color='#fdcb6e'><b>PROCESS & RUNTIME</b></font><br><font color='gray'>Await streaming...</font></html>");
        grid.add(lblHardwareLeft);
        grid.add(lblHardwareRight);

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
        JLabel lbl = new JLabel("<html><font color='" + toHex(accent) + "'><b>" + title + "</b></font><br><font color='gray'>No network context</font></html>");
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

        txtNetworkTopology = new JTextPane();
        txtNetworkTopology.setBackground(BG_DARK);
        txtNetworkTopology.setForeground(new Color(85, 239, 196));
        txtNetworkTopology.setFont(new Font("Monospaced", Font.BOLD, 12));
        txtNetworkTopology.setEditable(false);
        txtNetworkTopology.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollNet = new JScrollPane(txtNetworkTopology);
        scrollNet.setBorder(BorderFactory.createTitledBorder(new LineBorder(NEON_CYAN, 2), " NETWORK TOPOLOGY MAP ", 0, 0, null, NEON_CYAN));
        scrollNet.setPreferredSize(new Dimension(450, 320));

        gbc.gridy = 0; gbc.weighty = 0.50;
        gbc.insets = new Insets(0, 0, 10, 0);
        p.add(scrollNet, gbc);

        txtTelemetry = new JTextArea();
        txtTelemetry.setBackground(BG_DARK);
        txtTelemetry.setForeground(NEON_PURPLE);
        txtTelemetry.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtTelemetry.setEditable(false);

        JScrollPane scrollTele = new JScrollPane(txtTelemetry);
        scrollTele.setBorder(BorderFactory.createTitledBorder(new LineBorder(NEON_PURPLE, 1), " TELEMETRY LOG ", 0, 0, null, NEON_PURPLE));

        gbc.gridy = 1; gbc.weighty = 0.22;
        gbc.insets = new Insets(0, 0, 10, 0);
        p.add(scrollTele, gbc);

        clientTableModel = new DefaultTableModel(new String[]{"ADDR", "SESSION", "NICK", "STATUS"}, 0);
        JTable nodeTable = new JTable(clientTableModel);
        styleTable(nodeTable);

        JScrollPane scrollNodes = new JScrollPane(nodeTable);
        scrollNodes.setBorder(BorderFactory.createTitledBorder(new LineBorder(NEON_CYAN, 1), " ACTIVE NODES ", 0, 0, null, NEON_CYAN));

        gbc.gridy = 2; gbc.weighty = 0.28;
        gbc.insets = new Insets(0, 0, 0, 0);
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
        return new JLabel("<html><div style='text-align: right;'><font color='gray' size='2'>" + title + "</font><br>" +
                "<font color='" + toHex(color) + "' size='4'><b>" + val + "</b></font></div></html>");
    }

    // =========================================================================
    // INYECCIÓN DE DATOS PROVENIENTES EXCLUSIVAMENTE DEL TELEMETRYPACKET REMOTO
    // =========================================================================
    public void processPacketData(TelemetryPacket p) {
        SwingUtilities.invokeLater(() -> {
            this.currentPrimaryIp = p.currentPrimaryIp;

            // 1. Cabecera Dinámica Superior
            lblEngineMetric.setText("<html><div style='text-align: right;'><font color='gray' size='2'>CORE ENGINE</font><br><font color='" + toHex(NEON_PURPLE) + "' size='4'><b>" + p.coreVersion + "</b></font></div></html>");
            lblUptimeMetric.setText("<html><div style='text-align: right;'><font color='gray' size='2'>UPTIME</font><br><font color='" + toHex(NEON_GREEN) + "' size='4'><b>" + p.uptime + "</b></font></div></html>");

            String speedColor = (p.currentKbs > 800) ? "#e74c3c" : (p.currentKbs > 200 ? "#fdcb6e" : "#00cec9");
            lblTrafficMonitor.setText(String.format("<html><font color='gray' size='3'>FLOW:</font> <font color='#00cec9'>%s</font> <b color='%s'>%.1f KB/s</b></html>", p.trafficBar, speedColor, p.currentKbs));

            // 2. Bloque Hardware Izquierdo (Métricas nativas del OS Bean de tu Manager)
            lblHardwareLeft.setText(String.format(
                    "<html><font color='#fdcb6e'><b>HARDWARE ASSETS & SYSTEM</b></font><br>" +
                            "<table style='color:white; font-family:Sans-Serif; font-size:10px;'>" +
                            "<tr><td>CPU CORES:</td><td><b color='#00cec9'>%d</b> Lógicos</td></tr>" +
                            "<tr><td>OS CPU LOAD:</td><td><b color='%s'>%.1f%%</b></td></tr>" +
                            "<tr><td>JVM PROC LOAD:</td><td><b color='%s'>%.1f%%</b></td></tr>" +
                            "<tr><td>HEAP COMMITTED:</td><td>%s</td></tr>" +
                            "<tr><td>TOTAL OS RAM:</td><td><b color='#a29bfe'>%s</b></td></tr>" +
                            "<tr><td>OS RAM USED:</td><td><b color='#e74c3c'>%s (%d%%)</b></td></tr>" +
                            "<tr><td>OS RAM FREE:</td><td><b color='#55efc4'>%s</b></td></tr>" +
                            "<tr><td>OPERATOR:</td><td>%s</td></tr>" +
                            "</table></html>",
                    p.cpuCores, p.loadColor, p.systemCpuLoad, p.loadColor, p.processCpuLoad,
                    formatBytes(p.heapCommitted), formatBytes(p.totalPhysicalMemory),
                    formatBytes(p.usedPhysicalMemory), p.systemRamPercent, formatBytes(p.freePhysicalMemory), p.username
            ));

            // 3. Bloque Hardware Derecho (JVM Runtime Internals de tu Manager)
            lblHardwareRight.setText(String.format(
                    "<html><font color='#fdcb6e'><b>PROCESS & RUNTIME</b></font><br>" +
                            "<table style='color:white; font-family:Sans-Serif; font-size:10px;'>" +
                            "<tr><td>BITBRIDGE WK:</td><td><b color='#55efc4'>%d ACTIVOS</b></td></tr>" +
                            "<tr><td>TOTAL HILOS:</td><td><b color='#00cec9'>%d</b></td></tr>" +
                            "<tr><td>TIPO HILOS:</td><td>D: %d / U: %d</td></tr>" +
                            "<tr><td>PEAK THREADS:</td><td><b color='#a29bfe'>%d Hilos</b></td></tr>" +
                            "<tr><td>JVM ENGINE:</td><td>%s</td></tr>" +
                            "<tr><td>ARCH / VM:</td><td>%s / %s</td></tr>" +
                            "<tr><td>THREAD LOAD:</td><td>%.1f%%</td></tr>" +
                            "<tr><td>ESTADO ENGINE:</td><td><b color='%s'>%s</b></td></tr>" +
                            "</table></html>",
                    p.bitBridgeWorkers, p.totalThreadsCount, p.daemonThreadsCount, p.userThreadsCount,
                    p.peakThreadsCount, p.javaVersion, p.osArch, p.vmName, p.threadLoad, p.healthColor, p.healthText
            ));

            // 4. Bloque Software Environment (Compiladores & JIT)
            lblSoftwareBlock.setText(String.format(
                    "<html><div style='margin-bottom: 2px;'><font color='#a29bfe' size='4'><b>SOFTWARE ENVIRONMENT</b></font></div>" +
                            "<table style='color: white; font-family: Monospaced; font-size: 11px;'>" +
                            "<tr><td><font color='gray'>HOST OS OS :</font></td><td><b>%s</b></td></tr>" +
                            "<tr><td><font color='gray'>KERNEL VER :</font></td><td>%s</td></tr>" +
                            "<tr><td><font color='gray'>JIT COMPILE:</font></td><td><font color='#fdcb6e'>%d ms</font></td></tr>" +
                            "<tr><td><font color='gray'>GC ENGINE  :</font></td><td><font color='#00cec9'>[%s]</font></td></tr>" +
                            "<tr><td><font color='gray'>GC ACTIVITY:</font></td><td><font color='#e74c3c'>%d runs (%d ms)</font></td></tr>" +
                            "</table></html>",
                    p.osName, p.osVersion, p.jitCompileTimeMs, p.gcName, p.gcCollectionCount, p.gcCollectionTimeMs
            ));

            // 5. Bloque Network Flow (Buffers Directos NIO)
            lblNetworkBlock.setText(String.format(
                    "<html><div style='margin-bottom: 2px;'><font color='#00cec9' size='4'><b>MÉTRICAS DE RED Y FLUJO</b></font></div>" +
                            "<table style='color: white; font-family: Monospaced; font-size: 11px;'>" +
                            "<tr><td><font color='gray'>ENDPOINT    :</font></td><td><b color='#55efc4'>%s:%d</b></td>" +
                            "<td style='padding-left:15px;'><font color='gray'>INTERFAZ :</font></td><td><font color='#fdcb6e'>%s</font></td></tr>" +
                            "<tr><td><font color='gray'>NIO BUFFERS :</font></td><td>Count: <font color='#a29bfe'>%d</font></td>" +
                            "<td style='padding-left:15px;'><font color='gray'>CLASSES  :</font></td><td>%d Loaded</td></tr>" +
                            "<tr><td><font color='gray'>ESTADÍSTICA :</font></td><td>MSG: <font color='#00cec9'>%d</font></td>" +
                            "<td style='padding-left:15px;'><font color='gray'>SALUD    :</font></td><td><b color='%s'>%s</b></td></tr>" +
                            "<tr><td><font color='gray'>RENDIMIENTO :</font></td><td>UP: <font color='#a29bfe'>%s</font></td>" +
                            "<td style='padding-left:15px;'><font color='gray'>VOLUMEN  :</font></td><td><font color='#a29bfe'>%s</font></td></tr>" +
                            "<tr><td><font color='gray'>CONEXIONES  :</font></td><td><b color='#a29bfe'>%d NODOS</b></td>" +
                            "<td style='padding-left:15px;'><font color='gray'>DESCRIP. :</font></td><td>1500 (Auto)</td></tr>" +
                            "</table></html>",
                    p.currentPrimaryIp, p.port, p.activeInterfaceName, p.directBufferCount, p.totalLoadedClassCount,
                    p.totalMessages, p.healthColor, p.healthText, p.uptime, formatBytes(p.totalBytesTransferred), p.connectedNodes.size()
            ));

            // 6. Barra de Porcentaje RAM Interna de la JVM
            ramBar.setValue(p.ramPercent);
            ramBar.setString(String.format("JVM MEMORY PROFILE: %d%% (Used: %s / Max: %s) [Direct: %s | Mapped: %s]",
                    p.ramPercent, formatBytes(p.heapUsed), formatBytes(p.maxMemory), formatBytes(p.directMemoryUsed), formatBytes(p.mappedMemoryUsed)));
            ramBar.setForeground(p.ramPercent > 80 ? DANGER_RED : NEON_GREEN);

            // 7. Re-población de la tabla de Hilos provenientes del DTO
            threadModel.setRowCount(0);
            if (p.threadDetails != null) {
                for (TelemetryPacket.ThreadDTO t : p.threadDetails) {
                    threadModel.addRow(new Object[]{t.id(), t.name(), t.state(), t.priority(), t.type()});
                }
            }

            // 8. Historial de Logs Interceptados remotos
            txtTelemetry.setText("");
            if (p.shortLogHistory != null) {
                for (String log : p.shortLogHistory) txtTelemetry.append(" > " + log + "\n");
            }

            // 9. Re-población de Nodos Clientes Conectados
            clientTableModel.setRowCount(0);
            if (p.connectedNodes != null) {
                for (TelemetryPacket.ActiveNodeDTO node : p.connectedNodes) {
                    clientTableModel.addRow(new Object[]{node.address(), node.session(), node.nick(), node.status()});
                }
            }

            // 10. Pintar el mapa de interfaces ANSI
            renderNetworkTopologyPane(p);
        });
    }

    private void renderNetworkTopologyPane(TelemetryPacket p) {
        txtNetworkTopology.setText("");
        Color colEth = new Color(46, 204, 113);
        Color colWifi = new Color(241, 196, 15);
        Color colVirt = new Color(149, 165, 166);
        Color colPrimary = new Color(255, 118, 117);
        Color colInfo = new Color(0, 206, 201);
        Color colText = new Color(223, 230, 233);

        appendPane(" ╔══════════════════════════════════════════════════════════╗\n", colInfo);
        appendPane(" ║   BITBRIDGE Topologia De Red - HARDWARE REMOTO           ║\n", colInfo);
        appendPane(" ╚══════════════════════════════════════════════════════════╝\n\n", colInfo);

        if (p.networkTopology != null) {
            for (TelemetryPacket.NetworkInterfaceDTO ni : p.networkTopology) {
                Color blockColor = ni.typeStr().contains("ETHERNET") ? colEth :
                        ni.typeStr().contains("WI-FI") ? colWifi : colVirt;

                appendPane(" " + ni.typeStr() + " ", blockColor);
                appendPane(String.format("%-10s", ni.name()), Color.WHITE);
                appendPane(String.format(" | MTU: %-5d", ni.mtu()), colVirt);
                appendPane(" ● ONLINE\n", colEth);

                appendPane("   id: ", colVirt);
                appendPane(ni.displayName() + "\n", colText);

                for (int i = 0; i < ni.addresses().size(); i++) {
                    TelemetryPacket.IpAddressDTO addr = ni.addresses().get(i);
                    String branch = (i == ni.addresses().size() - 1) ? "   └─ " : "   ├─ ";
                    appendPane(branch, colVirt);

                    if (addr.isPrimary()) {
                        appendPane("IPv4: ", colInfo);
                        appendPane(String.format("%-15s", addr.ip()), colPrimary);
                        appendPane(" <--- Conexion Primaria\n", colPrimary);
                    } else {
                        appendPane("IPv4: ", colVirt);
                        appendPane(addr.ip() + "\n", Color.WHITE);
                    }
                }
                appendPane("\n", Color.WHITE);
            }
        }
        txtNetworkTopology.setCaretPosition(0);
    }

    private void appendPane(String msg, Color c) {
        javax.swing.text.StyleContext sc = javax.swing.text.StyleContext.getDefaultStyleContext();
        javax.swing.text.AttributeSet aset = sc.addAttribute(javax.swing.text.SimpleAttributeSet.EMPTY, javax.swing.text.StyleConstants.Foreground, c);
        int len = txtNetworkTopology.getDocument().getLength();
        try { txtNetworkTopology.getDocument().insertString(len, msg, aset); } catch (Exception ignored) {}
    }

    // --- ENLACE DE RED BAJA LATENCIA (NIO CORE WORKER THREAD) ---
    private void connect(String host, int port) {
        if (isRunning.get()) return;
        isRunning.set(true);

        btnConnect.setEnabled(false); btnDisconnect.setEnabled(true);
        txtHost.setEnabled(false); txtPort.setEnabled(false);

        networkWorker = new Thread(() -> {
            try {
                networkChannel = SocketChannel.open();
                networkChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                networkChannel.setOption(StandardSocketOptions.SO_RCVBUF, 2 * 1024 * 1024);
                networkChannel.configureBlocking(true);
                networkChannel.connect(new InetSocketAddress(host, port));

                if (networkChannel.isConnected()) {
                    HandshakeMessage handshake = new HandshakeMessage("OPERATOR-REMOTE", SocketPurpose.PASSIVE_LISTENER, "");
                    ProtocolService.writeNIO(networkChannel, handshake);

                    while (isRunning.get() && networkChannel.isOpen()) {
                        Object incoming = ProtocolService.readNIO(networkChannel);
                        if (incoming instanceof TelemetryPacket packet) {
                            processPacketData(packet);
                        }
                    }
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> txtTelemetry.append(" > [!] ERROR CENTRAL INFRAESTRUCTURA: " + e.getMessage() + "\n"));
            } finally {
                disconnect();
            }
        }, "BitBridge-RemoteWorker");
        networkWorker.setDaemon(true);
        networkWorker.start();
    }

    private void disconnect() {
        if (!isRunning.getAndSet(false)) return;
        try {
            if (networkChannel != null && networkChannel.isOpen()) networkChannel.close();
        } catch (Exception ignored) {}
        if (networkWorker != null && networkWorker.isAlive()) networkWorker.interrupt();

        SwingUtilities.invokeLater(() -> {
            btnConnect.setEnabled(true); btnDisconnect.setEnabled(false);
            txtHost.setEnabled(true); txtPort.setEnabled(true);
            lblEngineMetric.setText("<html><div style='text-align: right;'><font color='gray' size='2'>CORE ENGINE</font><br><font color='red' size='4'><b>OFFLINE</b></font></div></html>");
        });
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1) + "");
    }

    private String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerDashboard().setVisible(true));
    }
}
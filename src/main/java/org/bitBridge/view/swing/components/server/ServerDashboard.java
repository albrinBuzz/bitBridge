package org.bitBridge.view.swing.components.server;

import org.bitBridge.shared.core.comunication.model.basic.telemetry.TelemetryHandshakePacket;
import org.bitBridge.shared.core.comunication.model.basic.telemetry.TelemetryStreamPacket;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class ServerDashboard extends JFrame {

    // --- PALETA DE COLORES "GTK OBSIDIAN / ENTERPRISE" (BASADA EN image_2bf5c3.png) ---
    private static final Color BG_DARK        = new Color(30, 30, 36);      // Fondo principal de la ventana
    private static final Color PANEL_DARK     = new Color(37, 37, 43);     // Fondo de contenedores y tarjetas
    private static final Color BORDER_SUBTLE  = new Color(53, 53, 61);     // Separadores y bordes inactivos
    private static final Color INPUT_BG       = new Color(25, 25, 30);      // Fondo de campos de texto

    private static final Color ACCENT_BLUE    = new Color(9, 132, 227);     // Color corporativo BitBridge (Botón Conectar)
    private static final Color ACCENT_ORANGE  = new Color(230, 126, 34);    // Títulos de secciones y avisos importantes
    private static final Color STATE_GREEN    = new Color(46, 204, 113);    // Estados óptimos u Online
    private static final Color STATE_RED      = new Color(231, 76, 60);     // Desconexión o Alertas críticas
    private static final Color TEXT_LIGHT     = new Color(220, 221, 225);   // Texto principal
    private static final Color TEXT_MUTED     = new Color(127, 140, 141);   // Etiquetas secundarias y texto tenue

    // --- CONTROLES DE CONEXIÓN ---
    private JTextField txtHost, txtPort;
    private JButton btnConnect, btnDisconnect;

    // --- LABELS DINÁMICOS CENTRALIZADOS ---
    private JLabel lblTrafficMonitor, lblEngineVal, lblUptimeVal;

    // Hardware Assets (Izquierda)
    private JLabel lblCpuCores, lblCpuSys, lblCpuProc, lblHeapCommit, lblTotalRam, lblUsedRam, lblFreeRam, lblOperator;
    // Process & Runtime (Derecha)
    private JLabel lblWkActivos, lblTotalThreads, lblThreadType, lblPeakThreads, lblJvmEngine, lblArchVm, lblThreadLoad, lblEngineStatus;
    // Software Environment
    private JLabel lblOsName, lblKernelVer, lblJitTime, lblGcEngine, lblGcActivity;
    // Network & Flow
    private JLabel lblEndpoint, lblIoDisco, lblNioBuffers, lblClasses, lblMsgCount, lblVolumen, lblConnectedNodes, lblActiveXfers;

    // Componentes de la barra de estado inferior (Inspirada en el footer de la imagen)
    private JLabel lblFooterStatus, lblFooterCpu, lblFooterRam, lblFooterThreads, lblFooterPing, lblFooterPort;

    private JProgressBar ramBar;
    private DefaultTableModel threadModel, clientTableModel;
    private JTextArea txtTelemetry;
    private JTextPane txtNetworkTopology;

    private TelemetryClient telemetryClient;
    private TelemetryHandshakePacket cachedHandshake;

    public ServerDashboard() {
        setTitle("BitBridge | HUB OPERATOR PRO [REMOTE TELEMETRY]");
        setSize(1450, 900);
        setMinimumSize(new Dimension(1280, 800));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0)); // Espaciado plano tipo paneles empresariales

        initUI();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { disconnect(); }
        });
    }

    private void initUI() {
        // ==========================================
        // PANEL NORTE: BARRA DE CONTROL INTEGRADA
        // ==========================================
        JPanel northPanel = new JPanel(new BorderLayout(0, 0));
        northPanel.setBackground(PANEL_DARK);
        northPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE));

        JPanel connectionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        connectionBar.setOpaque(false);

        JLabel lblLogo = new JLabel("BITBRIDGE  ");
        lblLogo.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblLogo.setForeground(ACCENT_BLUE);

        JLabel lblTarget = new JLabel("Host:");
        lblTarget.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblTarget.setForeground(TEXT_LIGHT);

        txtHost = createStyledTextField("127.0.0.1", 12);

        JLabel lblPortTag = new JLabel("Puerto:");
        lblPortTag.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblPortTag.setForeground(TEXT_LIGHT);

        txtPort = createStyledTextField("8080", 5);

        btnConnect = createStyledButton("CONECTAR", ACCENT_BLUE, Color.WHITE);
        btnConnect.addActionListener(e -> {
            String host = txtHost.getText().trim();
            String portStr = txtPort.getText().trim();
            try {
                int port = Integer.parseInt(portStr);
                connect(host, port);
            } catch (NumberFormatException ex) {
                logLocalError("Puerto inválido.");
            }
        });

        btnDisconnect = createStyledButton("DESCONECTAR", BG_DARK, STATE_RED);
        btnDisconnect.setEnabled(false);
        btnDisconnect.addActionListener(e -> disconnect());

        connectionBar.add(lblLogo);
        connectionBar.add(lblTarget); connectionBar.add(txtHost);
        connectionBar.add(lblPortTag); connectionBar.add(txtPort);
        connectionBar.add(btnConnect); connectionBar.add(btnDisconnect);

        // Indicadores resumidos del header derecho
        JPanel infoHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 25, 12));
        infoHeader.setOpaque(false);

        lblTrafficMonitor = new JLabel("FLUJO: 0.0 KB/s");
        lblTrafficMonitor.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblTrafficMonitor.setForeground(TEXT_LIGHT);

        JPanel pEngine = createHeaderBlock("HUB CORE", lblEngineVal = new JLabel("OFFLINE"), TEXT_MUTED);
        JPanel pUptime = createHeaderBlock("UPTIME", lblUptimeVal = new JLabel("00:00:00"), STATE_GREEN);

        infoHeader.add(lblTrafficMonitor); infoHeader.add(pEngine); infoHeader.add(pUptime);

        northPanel.add(connectionBar, BorderLayout.WEST);
        northPanel.add(infoHeader, BorderLayout.EAST);
        add(northPanel, BorderLayout.NORTH);

        // ==========================================
        // PANEL CENTRAL: REJILLA DE MONITOREO
        // ==========================================
        JPanel mainGrid = new JPanel(new GridBagLayout());
        mainGrid.setOpaque(false);
        mainGrid.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Bloque 1: Panel de Propiedades de Hardware
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.55; gbc.weighty = 0.35;
        mainGrid.add(buildHardwareAssetPanel(), gbc);

        // Bloque 2: Entorno e Infraestructura de Red
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.45;
        mainGrid.add(buildSoftwareNetworkPanel(), gbc);

        // Bloque 3: Tabla de Hilos en tiempo real
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.55; gbc.weighty = 0.65;
        mainGrid.add(buildThreadInspectorPanel(), gbc);

        // Bloque 4: Logs, Nodos y Mapa de Interfaces
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.45;
        mainGrid.add(buildRightCompositePanel(), gbc);

        add(mainGrid, BorderLayout.CENTER);

        // ==========================================
        // PANEL SUR: BARRA DE ESTADO DE ALTA DENSIDAD
        // ==========================================
        add(buildFooterStatusBar(), BorderLayout.SOUTH);
    }

    // =========================================================================
    // CONSTRUCCIÓN DE COMPONENTES ATÓMICOS (ESTILO SOBRIO / SIN RE-RENDER HTML)
    // =========================================================================

    private JPanel buildHardwareAssetPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(PANEL_DARK);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_SUBTLE, 1), new EmptyBorder(10, 12, 10, 12)));

        JPanel innerGrid = new JPanel(new GridLayout(1, 2, 20, 0));
        innerGrid.setOpaque(false);

        // Hardware del Servidor
        JPanel pLeft = new JPanel(new GridLayout(8, 2, 5, 2));
        pLeft.setOpaque(false);
        pLeft.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), "RECURSOS DE HARDWARE", TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 11), ACCENT_ORANGE));

        lblCpuCores = createSpecLabel("-"); lblCpuSys = createSpecLabel("-");
        lblCpuProc = createSpecLabel("-"); lblHeapCommit = createSpecLabel("-");
        lblTotalRam = createSpecLabel("-"); lblUsedRam = createSpecLabel("-");
        lblFreeRam = createSpecLabel("-"); lblOperator = createSpecLabel("-");

        addSpecRow(pLeft, "Núcleos CPU:", lblCpuCores); addSpecRow(pLeft, "Carga CPU (SO):", lblCpuSys);
        addSpecRow(pLeft, "Carga CPU (JVM):", lblCpuProc); addSpecRow(pLeft, "Asignación Heap:", lblHeapCommit);
        addSpecRow(pLeft, "Memoria Física:", lblTotalRam); addSpecRow(pLeft, "Memoria en Uso:", lblUsedRam);
        addSpecRow(pLeft, "Memoria Libre:", lblFreeRam); addSpecRow(pLeft, "Operador Remoto:", lblOperator);

        // Runtime de la Aplicación
        JPanel pRight = new JPanel(new GridLayout(8, 2, 5, 2));
        pRight.setOpaque(false);
        pRight.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), "ENTORNO DE EJECUCIÓN", TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 11), ACCENT_ORANGE));

        lblWkActivos = createSpecLabel("-"); lblTotalThreads = createSpecLabel("-");
        lblThreadType = createSpecLabel("-"); lblPeakThreads = createSpecLabel("-");
        lblJvmEngine = createSpecLabel("-"); lblArchVm = createSpecLabel("-");
        lblThreadLoad = createSpecLabel("-"); lblEngineStatus = createSpecLabel("-");

        addSpecRow(pRight, "Trabajadores Activos:", lblWkActivos); addSpecRow(pRight, "Hilos Totales:", lblTotalThreads);
        addSpecRow(pRight, "Clasificación Hilos:", lblThreadType); addSpecRow(pRight, "Pico de Hilos:", lblPeakThreads);
        addSpecRow(pRight, "Motor de la JVM:", lblJvmEngine); addSpecRow(pRight, "Arquitectura VM:", lblArchVm);
        addSpecRow(pRight, "Carga de Hilos:", lblThreadLoad); addSpecRow(pRight, "Estado del Motor:", lblEngineStatus);

        innerGrid.add(pLeft); innerGrid.add(pRight);

        ramBar = new JProgressBar(0, 100);
        ramBar.setStringPainted(true);
        ramBar.setFont(new Font("SansSerif", Font.PLAIN, 10));
        ramBar.setPreferredSize(new Dimension(0, 18));
        ramBar.setBackground(INPUT_BG);
        ramBar.setForeground(ACCENT_BLUE);
        ramBar.setBorder(new LineBorder(BORDER_SUBTLE, 1));
        ramBar.setString("Perfilador de Memoria: Esperando conexión...");

        p.add(innerGrid, BorderLayout.CENTER);
        p.add(ramBar, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildSoftwareNetworkPanel() {
        JPanel mainPanel = new JPanel(new GridLayout(2, 1, 0, 10));
        mainPanel.setOpaque(false);

        // Entorno de Software
        JPanel pSoft = new JPanel(new GridLayout(5, 2, 10, 2));
        pSoft.setBackground(PANEL_DARK);
        pSoft.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_SUBTLE, 1), new EmptyBorder(8, 12, 8, 12)));
        pSoft.setBorder(BorderFactory.createTitledBorder(pSoft.getBorder(), "DETALLES DEL SISTEMA OPERATIVO", TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 11), ACCENT_ORANGE));

        lblOsName = createSpecLabel("-"); lblKernelVer = createSpecLabel("-");
        lblJitTime = createSpecLabel("-"); lblGcEngine = createSpecLabel("-");
        lblGcActivity = createSpecLabel("-");

        addSpecRow(pSoft, "Sistema Operativo:", lblOsName); addSpecRow(pSoft, "Versión del Kernel:", lblKernelVer);
        addSpecRow(pSoft, "Compilación JIT:", lblJitTime); addSpecRow(pSoft, "Recolector de Basura:", lblGcEngine);
        addSpecRow(pSoft, "Actividad del GC:", lblGcActivity);

        // Red e I/O
        JPanel pNet = new JPanel(new GridLayout(5, 2, 10, 2));
        pNet.setBackground(PANEL_DARK);
        pNet.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_SUBTLE, 1), new EmptyBorder(8, 12, 8, 12)));
        pNet.setBorder(BorderFactory.createTitledBorder(pNet.getBorder(), "RENDIMIENTO DE RED E E/S", TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 11), ACCENT_ORANGE));

        lblEndpoint = createSpecLabel("-"); lblIoDisco = createSpecLabel("-");
        lblNioBuffers = createSpecLabel("-"); lblClasses = createSpecLabel("-");
        lblMsgCount = createSpecLabel("-"); lblVolumen = createSpecLabel("-");
        lblConnectedNodes = createSpecLabel("-"); lblActiveXfers = createSpecLabel("-");

        addSpecRow(pNet, "Dirección Endpoint:", lblEndpoint); addSpecRow(pNet, "E/S en Disco:", lblIoDisco);
        addSpecRow(pNet, "Búferes NIO directos:", lblNioBuffers); addSpecRow(pNet, "Clases Cargadas:", lblClasses);
        addSpecRow(pNet, "Mensajes Procesados:", lblMsgCount); addSpecRow(pNet, "Volumen Transferido:", lblVolumen);
        addSpecRow(pNet, "Nodos Conectados:", lblConnectedNodes); addSpecRow(pNet, "Tareas de Transferencia:", lblActiveXfers);

        mainPanel.add(pSoft); mainPanel.add(pNet);
        return mainPanel;
    }

    private JPanel buildThreadInspectorPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL_DARK);
        p.setBorder(BorderFactory.createTitledBorder(new LineBorder(BORDER_SUBTLE, 1), " AUDITORÍA DE HILOS INTERNOS DE LA JVM ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 11), ACCENT_ORANGE));

        threadModel = new DefaultTableModel(new String[]{"ID", "Identificador del Hilo", "Estado de Ejecución", "Prioridad", "Tipo"}, 0);
        JTable table = new JTable(threadModel);
        styleTable(table);
        table.getColumnModel().getColumn(2).setCellRenderer(new ThreadStatusRenderer());

        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(280);

        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildRightCompositePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // Topología de Red
        txtNetworkTopology = new JTextPane();
        txtNetworkTopology.setBackground(INPUT_BG);
        txtNetworkTopology.setForeground(TEXT_LIGHT);
        txtNetworkTopology.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtNetworkTopology.setEditable(false);

        JScrollPane scrollNet = new JScrollPane(txtNetworkTopology);
        scrollNet.setBorder(BorderFactory.createTitledBorder(new LineBorder(BORDER_SUBTLE, 1), " MAPA DE ADAPTADORES DE RED LOCALES ", 0, 0, new Font("SansSerif", Font.BOLD, 11), ACCENT_ORANGE));

        gbc.gridy = 0; gbc.weighty = 0.40; gbc.insets = new Insets(0, 0, 8, 0);
        p.add(scrollNet, gbc);

        // Registro de Logs
        txtTelemetry = new JTextArea();
        txtTelemetry.setBackground(INPUT_BG);
        txtTelemetry.setForeground(TEXT_LIGHT);
        txtTelemetry.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtTelemetry.setEditable(false);

        JScrollPane scrollTele = new JScrollPane(txtTelemetry);
        scrollTele.setBorder(BorderFactory.createTitledBorder(new LineBorder(BORDER_SUBTLE, 1), " CONSOLA DE AUDITORÍA DE TELEMETRÍA ", 0, 0, new Font("SansSerif", Font.BOLD, 11), ACCENT_ORANGE));

        gbc.gridy = 1; gbc.weighty = 0.30; gbc.insets = new Insets(0, 0, 8, 0);
        p.add(scrollTele, gbc);

        // Registro de Nodos Conectados
        clientTableModel = new DefaultTableModel(new String[]{"Dirección de Red (Endpoint)", "Alias del Nodo (Nick)", "Estatus"}, 0);
        JTable nodeTable = new JTable(clientTableModel);
        styleTable(nodeTable);

        JScrollPane scrollNodes = new JScrollPane(nodeTable);
        scrollNodes.setBorder(BorderFactory.createTitledBorder(new LineBorder(BORDER_SUBTLE, 1), " REGISTRO DE NODOS DE TRABAJO ACTIVOS ", 0, 0, new Font("SansSerif", Font.BOLD, 11), ACCENT_ORANGE));

        gbc.gridy = 2; gbc.weighty = 0.30;
        p.add(scrollNodes, gbc);

        return p;
    }

    private JPanel buildFooterStatusBar() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(INPUT_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_SUBTLE, 1), new EmptyBorder(4, 10, 4, 10)));

        lblFooterStatus = new JLabel("NODO: BitBridge-v1.0   |   SISTEMA INICIALIZADO / LISTO");
        lblFooterStatus.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblFooterStatus.setForeground(TEXT_MUTED);

        JPanel rightMetrics = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightMetrics.setOpaque(false);

        lblFooterCpu = createFooterMetricLabel("CPU: 0%");
        lblFooterRam = createFooterMetricLabel("RAM: 0 MB");
        lblFooterThreads = createFooterMetricLabel("TH: 0");
        lblFooterPing = createFooterMetricLabel("PING: -- ms");
        lblFooterPort = createFooterMetricLabel("PUERTO: 0000");

        rightMetrics.add(lblFooterCpu); rightMetrics.add(lblFooterRam);
        rightMetrics.add(lblFooterThreads); rightMetrics.add(lblFooterPing);
        rightMetrics.add(lblFooterPort);

        footer.add(lblFooterStatus, BorderLayout.WEST);
        footer.add(rightMetrics, BorderLayout.EAST);
        return footer;
    }

    private JLabel createFooterMetricLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 11));
        lbl.setForeground(TEXT_MUTED);
        return lbl;
    }

    // =========================================================================
    // INYECCIÓN DE DATOS EN FLUJO CONTINUO (ANTI-FLICKER)
    // =========================================================================

    public void processHandshake(TelemetryHandshakePacket h) {
        this.cachedHandshake = h;
        SwingUtilities.invokeLater(() -> {
            lblEngineVal.setText(h.coreVersion);
            lblCpuCores.setText(h.cpuCores + " Cores");
            lblTotalRam.setText(formatBytes(h.totalPhysicalMemory));
            lblOperator.setText(h.username);
            lblOsName.setText(h.osName);
            lblKernelVer.setText(h.osVersion);
            lblFooterPort.setText("PUERTO: " + h.port);

            renderStaticNetworkTopology(h);
        });
    }

    public void processStreamData(TelemetryStreamPacket s) {
        if (cachedHandshake == null) return;

        SwingUtilities.invokeLater(() -> {
            // Cabeceras
            lblUptimeVal.setText(s.uptime);
            lblTrafficMonitor.setText(String.format("FLUJO: %.1f KB/s", s.currentKbs));

            // Recursos Hardware
            lblCpuSys.setText(String.format("%.1f%%", s.systemCpuLoad));
            lblCpuProc.setText(String.format("%.1f%%", s.processCpuLoad));
            lblHeapCommit.setText(formatBytes(s.heapCommitted));
            lblUsedRam.setText(String.format("%s (%d%%)", formatBytes(s.usedPhysicalMemory), s.systemRamPercent));
            lblFreeRam.setText(formatBytes(s.freePhysicalMemory));

            // Runtime
            lblWkActivos.setText(s.bitBridgeWorkers + " Activos");
            lblTotalThreads.setText(String.valueOf(s.totalThreadsCount));
            lblThreadType.setText("Daemon: " + s.daemonThreadsCount + " / User: " + s.userThreadsCount);
            lblPeakThreads.setText(s.peakThreadsCount + " Hilos");
            lblJvmEngine.setText(cachedHandshake.vmName);
            lblArchVm.setText(cachedHandshake.osArch + " / Java " + cachedHandshake.javaVersion);
            lblThreadLoad.setText(String.format("%.1f%%", s.threadLoad));
            lblEngineStatus.setText(s.healthText);
            lblEngineStatus.setForeground(s.healthText.equals("CRÍTICO") ? STATE_RED : STATE_GREEN);

            // Métricas de Software
            lblJitTime.setText(s.jitCompileTimeMs + " ms");
            lblGcEngine.setText(s.gcName);
            lblGcActivity.setText(s.gcCollectionCount + " corridas (" + s.gcCollectionTimeMs + " ms)");

            // Métricas de Red
            lblEndpoint.setText(cachedHandshake.currentPrimaryIp + ":" + cachedHandshake.port);
            lblIoDisco.setText(String.format("R: %.1f MB/s | W: %.1f MB/s", s.diskReadSpeedMbs, s.diskWriteSpeedMbs));
            lblNioBuffers.setText(String.format("Directos: %d (%s)", s.directBufferCount, formatBytes(s.directMemoryUsed)));
            lblClasses.setText(s.totalLoadedClassCount + " clases");
            lblMsgCount.setText(String.valueOf(s.totalMessages));
            lblVolumen.setText(formatBytes(s.totalBytesTransferred));
            lblConnectedNodes.setText(s.connectedNodes.size() + " Nodos");
            lblActiveXfers.setText(s.activeTransfersCount + " Tareas");

            // Barra de Perfil RAM Interna
            ramBar.setValue(s.ramPercent);
            ramBar.setString(String.format("Asignación de Memoria JVM: %d%% (%s / %s)", s.ramPercent, formatBytes(s.heapUsed), formatBytes(cachedHandshake.maxMemory)));

            // Actualizar Barra de Estado Inferior (Dureza Visual de image_2bf5c3.png)
            lblFooterCpu.setText(String.format("CPU: %.1f%%", s.processCpuLoad));
            lblFooterRam.setText("RAM: " + formatBytes(s.heapUsed));
            lblFooterThreads.setText("TH: " + s.totalThreadsCount);
            lblFooterStatus.setText("NODE: BitBridge-v1.0   |   CONECTADO - TRANSMITIENDO TELEMETRÍA");
            lblFooterStatus.setForeground(STATE_GREEN);

            // Tablas Recurrentes
            List<Object[]> pendingThreads = new ArrayList<>();
            if (s.threadDetails != null) {
                for (TelemetryStreamPacket.ThreadLiveDTO t : s.threadDetails) {
                    pendingThreads.add(new Object[]{t.id(), t.name(), t.state(), t.priority(), t.type()});
                }
            }
            updateTableData(threadModel, pendingThreads);

            txtTelemetry.setText("");
            if (s.shortLogHistory != null) {
                for (String log : s.shortLogHistory) txtTelemetry.append(" " + log + "\n");
            }

            List<Object[]> pendingNodes = new ArrayList<>();
            if (s.connectedNodes != null) {
                for (TelemetryStreamPacket.ActiveNodeLiveDTO node : s.connectedNodes) {
                    pendingNodes.add(new Object[]{node.address(), node.nick(), node.status()});
                }
            }
            updateTableData(clientTableModel, pendingNodes);
        });
    }

    // =========================================================================
    // PARSEADORES, ESTILOS DE TABLAS Y AUXILIARES
    // =========================================================================

    private JTextField createStyledTextField(String text, int columns) {
        JTextField tf = new JTextField(text, columns);
        tf.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tf.setBackground(INPUT_BG);
        tf.setForeground(TEXT_LIGHT);
        tf.setCaretColor(ACCENT_BLUE);
        tf.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_SUBTLE, 1), new EmptyBorder(3, 5, 3, 5)));
        return tf;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_SUBTLE, 1), new EmptyBorder(4, 12, 4, 12)));
        return btn;
    }

    private JLabel createSpecLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(TEXT_LIGHT);
        return lbl;
    }

    private void addSpecRow(JPanel panel, String title, JLabel valueLabel) {
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLbl.setForeground(TEXT_MUTED);
        panel.add(titleLbl);
        panel.add(valueLabel);
    }

    private JPanel createHeaderBlock(String title, JLabel valLabel, Color color) {
        JPanel block = new JPanel(new GridLayout(2, 1, 0, 0));
        block.setOpaque(false);

        JLabel t = new JLabel(title, SwingConstants.RIGHT);
        t.setFont(new Font("SansSerif", Font.PLAIN, 10));
        t.setForeground(TEXT_MUTED);

        valLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        valLabel.setForeground(color);
        valLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        block.add(t); block.add(valLabel);
        return block;
    }

    private void styleTable(JTable table) {
        table.setBackground(PANEL_DARK);
        table.setForeground(TEXT_LIGHT);
        table.setRowHeight(24);
        table.setGridColor(BORDER_SUBTLE);
        table.setFont(new Font("SansSerif", Font.PLAIN, 11));
        table.setSelectionBackground(new Color(57, 57, 66));
        table.setFillsViewportHeight(true);
        table.setBorder(BorderFactory.createEmptyBorder());

        JTableHeader header = table.getTableHeader();
        header.setBackground(BG_DARK);
        header.setForeground(TEXT_LIGHT);
        header.setFont(new Font("SansSerif", Font.BOLD, 11));
        header.setBorder(new LineBorder(BORDER_SUBTLE, 1));
    }

    private void updateTableData(DefaultTableModel model, List<Object[]> rawData) {
        model.setRowCount(0);
        for (Object[] row : rawData) model.addRow(row);
    }

    private void renderStaticNetworkTopology(TelemetryHandshakePacket h) {
        txtNetworkTopology.setText("");
        appendPane(" — NETWORK TOPOLOGY MONITOR —\n\n", ACCENT_BLUE);

        if (h.networkTopology != null) {
            for (TelemetryHandshakePacket.NetworkInterfaceStaticDTO ni : h.networkTopology) {
                appendPane("[" + ni.typeStr() + "] ", ACCENT_ORANGE);
                appendPane(ni.name() + " (" + ni.displayName() + ") | MTU: " + ni.mtu() + "\n", TEXT_LIGHT);

                for (String ip : ni.addresses()) {
                    if (ip.equals(h.currentPrimaryIp)) {
                        appendPane("   -> IPv4 Activo: " + ip + " [CANAL NIO PRINCIPAL]\n", STATE_GREEN);
                    } else {
                        appendPane("   -  IPv4 Vinculado: " + ip + "\n", TEXT_MUTED);
                    }
                }
                appendPane("\n", TEXT_MUTED);
            }
        }
    }

    private void appendPane(String msg, Color c) {
        javax.swing.text.StyleContext sc = javax.swing.text.StyleContext.getDefaultStyleContext();
        javax.swing.text.AttributeSet aset = sc.addAttribute(javax.swing.text.SimpleAttributeSet.EMPTY, javax.swing.text.StyleConstants.Foreground, c);
        int len = txtNetworkTopology.getDocument().getLength();
        try { txtNetworkTopology.getDocument().insertString(len, msg, aset); } catch (Exception ignored) {}
    }

    private void initTelemetryService() {
        this.telemetryClient = new TelemetryClient(new TelemetryClient.TelemetryListener() {
            @Override
            public void onConnected() {
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setEnabled(false); btnDisconnect.setEnabled(true);
                    txtHost.setEnabled(false); txtPort.setEnabled(false);
                });
            }

            @Override
            public void onHandshakeReceived(TelemetryHandshakePacket packet) { processHandshake(packet); }

            @Override
            public void onStreamReceived(TelemetryStreamPacket packet) { processStreamData(packet); }

            @Override
            public void onError(String message, Throwable cause) {
                SwingUtilities.invokeLater(() -> logLocalError(message + " -> " + cause.getMessage()));
            }

            @Override
            public void onDisconnected() { resetUIState(); }
        });
    }

    private void connect(String host, int port) {
        if (telemetryClient == null) initTelemetryService();
        telemetryClient.connect(host, port);
    }

    private void disconnect() {
        if (telemetryClient != null) telemetryClient.disconnect();
        resetUIState();
    }

    private void resetUIState() {
        this.cachedHandshake = null;
        SwingUtilities.invokeLater(() -> {
            btnConnect.setEnabled(true); btnDisconnect.setEnabled(false);
            txtHost.setEnabled(true); txtPort.setEnabled(true);
            lblEngineVal.setText("OFFLINE");
            lblUptimeVal.setText("00:00:00");
            lblTrafficMonitor.setText("FLUJO: 0.0 KB/s");
            ramBar.setValue(0);
            ramBar.setString("Perfilador de Memoria: Desconectado");

            lblFooterStatus.setText("NODO: BitBridge-v1.0   |   SISTEMA INICIALIZADO / LISTO");
            lblFooterStatus.setForeground(TEXT_MUTED);
            lblFooterCpu.setText("CPU: 0%"); lblFooterRam.setText("RAM: 0 MB"); lblFooterThreads.setText("TH: 0");

            JLabel[] labelsToReset = {lblCpuCores, lblCpuSys, lblCpuProc, lblHeapCommit, lblTotalRam, lblUsedRam, lblFreeRam, lblOperator,
                    lblWkActivos, lblTotalThreads, lblThreadType, lblPeakThreads, lblJvmEngine, lblArchVm, lblThreadLoad, lblEngineStatus,
                    lblOsName, lblKernelVer, lblJitTime, lblGcEngine, lblGcActivity,
                    lblEndpoint, lblIoDisco, lblNioBuffers, lblClasses, lblMsgCount, lblVolumen, lblConnectedNodes, lblActiveXfers};
            for(JLabel l : labelsToReset) { if(l != null) l.setText("-"); }
            threadModel.setRowCount(0);
            clientTableModel.setRowCount(0);
        });
    }

    private void logLocalError(String msg) {
        txtTelemetry.append(" [!] ERROR DEL CLIENTE: " + msg + "\n");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1) + "");
    }

    static class ThreadStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String state = value != null ? value.toString() : "";
            if (state.equals("RUNNABLE")) c.setForeground(STATE_GREEN);
            else if (state.contains("WAITING") || state.contains("TIMED_WAITING")) c.setForeground(ACCENT_ORANGE);
            else c.setForeground(TEXT_MUTED);
            return c;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerDashboard().setVisible(true));
    }
}
package org.bitBridge.Tests.Gui.nicotine;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class BitBridgeNicotineUI extends JFrame {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARKER = new Color(25, 25, 25);
    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);

    // Componentes de estado global
    private JButton btnConnect;
    private JLabel lblServerStatus;
    private DefaultTableModel downloadModel;
    public BitBridgeNicotineUI() {
        setupTheme();
        setTitle("BitBridge - [P2P Network Node]");
        setSize(1350, 850);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. BARRA SUPERIOR CON CONTROL DE SERVIDOR
        add(createTopInfoBar(), BorderLayout.NORTH);

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.putClientProperty("JTabbedPane.showTabSeparators", true);

        // Pestañas principales
        mainTabs.addTab("🌐 Clientes en Red", createNetworkClientsPanel()); // NUEVA PESTAÑA
        mainTabs.addTab("🔍 Buscar Archivos", createSearchPanel());
        mainTabs.addTab("⬇ Transferencias (Core)", createCoreTransferEngine());
        mainTabs.addTab("⬇ Descargas", createAdvancedTransferPanel(true));
        mainTabs.addTab("⬆ Subidas", createAdvancedTransferPanel(false));
        mainTabs.addTab("📂 Explorar Compartidos", createSharedExplorerPanel());
        mainTabs.addTab("💬 Chat Privado", createChatPanel());
        mainTabs.addTab("👥 Amigos", createFriendsManagerPanel());
        mainTabs.addTab("⚙ Intereses", createPlaceholderPanel("✨", "Búsquedas automáticas"));
// Dentro del constructor BitBridgeNicotineUI()
        mainTabs.addTab("📦 Repositorio Global", createGlobalRepositoryPanel());
        mainTabs.addTab("📈 Rendimiento Servidor", createServerPerformancePanel());
        // 3. SECCIÓN INFERIOR (LOGS Y STATUS)
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(createLogConsole(), BorderLayout.CENTER);
        southPanel.add(createStatusBar(), BorderLayout.SOUTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainTabs, southPanel);
        mainSplit.setDividerLocation(580);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);
    }

    /**
     * EL CORAZÓN DE LA APP: Motor de Transferencia Avanzado
     */
    private JPanel createCoreTransferEngine() {
        JPanel panel = new JPanel(new BorderLayout());

        // Tabla de transferencias
        String[] cols = {"ID", "Archivo", "Usuario", "Velocidad", "Progreso", "Hilos", "Estado", "ETA"};
        downloadModel = new DefaultTableModel(cols, 0);
        downloadModel.addRow(new Object[]{"#01", "debian-12-amd64.iso", "mirror_master", "12.5 MB/s", 45, "16/16", "Descargando", "00:04:12"});
        downloadModel.addRow(new Object[]{"#02", "deep_learning_weights.bin", "ai_research", "850 KB/s", 12, "4/16", "Lento", "02:15:00"});
        downloadModel.addRow(new Object[]{"#03", "source_code.zip", "dev_node", "0 B/s", 100, "0/0", "Completado", "Done"});

        JTable table = new JTable(downloadModel);
        table.setRowHeight(35);
        table.getColumnModel().getColumn(4).setCellRenderer(new ProgressRenderer());

        // Panel de detalles (Split horizontal inferior dentro de la pestaña)
        JPanel detailPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        detailPanel.setBorder(new TitledBorder("Inspección de Socket en Tiempo Real"));

        // Simulación de gráfico de velocidad
        JPanel speedGraph = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(NICOTINE_ORANGE);
                int[] x = {0, 50, 100, 150, 200, 250, 300, 400};
                int[] y = {80, 70, 90, 40, 60, 30, 50, 20};
                g.drawPolyline(x, y, 8);
            }
        };
        speedGraph.setBackground(Color.BLACK);

        JTextArea socketInfo = new JTextArea("CHUNK ADDR: 0x4F22...01\nPROTO: TCP-DIRECT\nBUFFER: 1024KB\nENCRYPT: AES-256-GCM");
        socketInfo.setEditable(false);
        socketInfo.setBackground(new Color(35,35,35));

        detailPanel.add(new JScrollPane(socketInfo));
        detailPanel.add(speedGraph);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), detailPanel);
        split.setDividerLocation(400);

        panel.add(split, BorderLayout.CENTER);
        panel.add(createTransferToolbar(), BorderLayout.NORTH);

        return panel;
    }



    private JPanel createTransferToolbar() {
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tb.add(new JButton("▶ Iniciar"));
        tb.add(new JButton("⏸ Pausar"));
        tb.add(new JButton("⏹ Detener"));
        tb.add(new JSeparator(SwingConstants.VERTICAL));
        tb.add(new JLabel("Límite Global:"));
        tb.add(new JSpinner(new SpinnerNumberModel(100, 0, 10000, 10)));
        tb.add(new JLabel("MB/s"));
        return tb;
    }

    private JPanel createNetworkClientsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // --- 1. DASHBOARD DEL SERVIDOR CENTRAL ---
        JPanel serverDashboard = new JPanel(new GridLayout(1, 4, 10, 0));
        serverDashboard.setBackground(new Color(30, 35, 45));
        serverDashboard.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 2, 0, NICOTINE_ORANGE),
                new EmptyBorder(12, 15, 12, 15)
        ));

        serverDashboard.add(createStatCard("HOSTS ACTIVOS", "12", SUCCESS_GREEN));
        serverDashboard.add(createStatCard("IP SERVIDOR", "192.168.1.100", Color.WHITE));
        serverDashboard.add(createStatCard("CARGA DEL HUB", "15%", Color.CYAN));
        serverDashboard.add(createStatCard("TRÁFICO INTERNO", "450 Mbps", NICOTINE_ORANGE));

        // --- 2. BARRA DE HERRAMIENTAS Y FILTRO ---
        JPanel toolBar = new JPanel(new BorderLayout());
        toolBar.setBackground(BG_DARKER);
        JPanel leftTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnBroadcast = new JButton("📣 Mensaje Global");
        JButton btnScan = new JButton("🔍 Escanear LAN");
        leftTools.add(btnBroadcast);
        leftTools.add(btnScan);

        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JTextField txtSearch = new JTextField(15);
        txtSearch.putClientProperty("JTextField.placeholderText", "Buscar Host o IP...");
        rightTools.add(new JLabel("Filtrar: "));
        rightTools.add(txtSearch);

        toolBar.add(leftTools, BorderLayout.WEST);
        toolBar.add(rightTools, BorderLayout.EAST);

        // --- 3. TABLA DE NODOS DE LA LAN ---
        String[] cols = {"Hostname", "Dirección IP", "MAC Address", "Sesión", "Archivos", "Estado", "Ping"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        model.addRow(new Object[]{"WORKSTATION-01", "192.168.1.50", "00:1A:2B:3C:4D:5E", "Admin", "1,240", "Conectado", "1ms"});
        model.addRow(new Object[]{"LAPTOP-VENTAS", "192.168.1.55", "00:1A:2B:99:4D:11", "User", "45", "Conectado", "12ms"});
        model.addRow(new Object[]{"SERVER-BACKUP", "192.168.1.10", "00:1A:2B:AA:BB:CC", "System", "95,000", "Standby", "2ms"});
        model.addRow(new Object[]{"DEV-STATION", "192.168.1.60", "00:1A:2B:FF:EE:DD", "Dev", "8,902", "Conectado", "1ms"});

        JTable table = new JTable(model);
        table.setRowHeight(35);
        table.getTableHeader().setReorderingAllowed(false);

        // --- 4. ACCIÓN DE SIMULACIÓN DE ENVÍO (Lógica Central) ---
        ActionListener pushAction = e -> {
            int row = table.getSelectedRow();
            if (row == -1) return;

            String targetHost = (String) table.getValueAt(row, 0);
            boolean isFolder = e.getActionCommand().equals("DIR");

            JFileChooser chooser = new JFileChooser();
            if (isFolder) chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                simulateDataPush(targetHost, path, isFolder); // Llamada al motor de simulación
            }
        };

        // --- 5. MENÚ CONTEXTUAL ---
        JPopupMenu serverMenu = new JPopupMenu();
        serverMenu.add(new JMenuItem("📂 Explorar archivos del cliente"));
        serverMenu.addSeparator();

        JMenuItem itemPushFile = new JMenuItem("📤 Push File (Enviar archivo)");
        itemPushFile.setActionCommand("FILE");
        itemPushFile.addActionListener(pushAction);

        JMenuItem itemPushDir = new JMenuItem("📁 Push Directory (Enviar carpeta)");
        itemPushDir.setActionCommand("DIR");
        itemPushDir.addActionListener(pushAction);

        serverMenu.add(itemPushFile);
        serverMenu.add(itemPushDir);
        serverMenu.addSeparator();
        serverMenu.add(new JMenuItem("⚡ Ejecutar Ping Test"));
        serverMenu.add(new JMenuItem("🖥️ Solicitar Captura de Pantalla"));
        serverMenu.addSeparator();

        JMenuItem itemDisconnect = new JMenuItem("⚠️ Forzar Desconexión");
        itemDisconnect.setForeground(Color.RED);
        serverMenu.add(itemDisconnect);

        table.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { handleSelection(e); }
            public void mouseReleased(MouseEvent e) { handleSelection(e); }
            private void handleSelection(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row != -1) table.setRowSelectionInterval(row, row);
                if (e.isPopupTrigger() && table.getSelectedRow() != -1) {
                    serverMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        // --- 6. PANEL DE DETALLES (SIDE PANEL) ---
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setPreferredSize(new Dimension(280, 0));
        sidePanel.setBorder(new CompoundBorder(new MatteBorder(0, 1, 0, 0, Color.DARK_GRAY), new EmptyBorder(15,15,15,15)));

        JLabel lblTitle = new JLabel("PEER INSPECTOR");
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        lblTitle.setForeground(NICOTINE_ORANGE);

        JTextPane clientInfo = new JTextPane();
        clientInfo.setContentType("text/html");
        clientInfo.setEditable(false);
        clientInfo.setOpaque(false);
        clientInfo.setText("<html><body style='color:gray; font-family:sans-serif;'>Seleccione un cliente...</body></html>");

        JProgressBar healthBar = new JProgressBar(0, 100);
        healthBar.setValue(100);
        healthBar.setStringPainted(true);
        healthBar.setString("Estabilidad: 100%");
        healthBar.setForeground(SUCCESS_GREEN);
        healthBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                String host = table.getValueAt(table.getSelectedRow(), 0).toString();
                String ip = table.getValueAt(table.getSelectedRow(), 1).toString();
                clientInfo.setText("<html><body style='color:white; font-family:sans-serif;'>"
                        + "<b style='color:orange;'>HOST:</b> " + host + "<br>"
                        + "<b>IP:</b> " + ip + "<br>"
                        + "<b>OS:</b> Windows 11 Pro<br>"
                        + "<b>Uptime:</b> 12h 45m</body></html>");
            }
        });

        sidePanel.add(lblTitle);
        sidePanel.add(Box.createVerticalStrut(15));
        sidePanel.add(healthBar);
        sidePanel.add(Box.createVerticalStrut(15));
        sidePanel.add(clientInfo);
        sidePanel.add(Box.createVerticalGlue());
        sidePanel.add(new JButton("🔄 Reiniciar Conexión"));

        // ENSAMBLE
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(toolBar, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        panel.add(serverDashboard, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(sidePanel, BorderLayout.EAST);

        return panel;
    }


    /**
     * Simula el proceso de 'Push' de datos desde el servidor hacia un cliente específico.
     */
    private void simulateDataPush(String targetHost, String path, boolean isDirectory) {
        String type = isDirectory ? "DIR" : "FILE";
        String fileName = new java.io.File(path).getName();
        String transferID = "#PR-" + (int)(Math.random() * 900 + 100);

        // 1. Notificar en la consola global
        String logMsg = String.format(" [SERVER] Iniciando envío de %s [%s] hacia %s...", type, fileName, targetHost);
        // mainLogArea.append("\n" + logMsg); // Descomenta esto si tu JTextArea es accesible

        // 2. Agregar a la tabla del Transfer Engine (Simulación visual)
        // downloadModel es el DefaultTableModel de tu CoreTransferEngine
        if (downloadModel != null) {
            downloadModel.insertRow(0, new Object[]{
                    transferID,
                    fileName,
                    targetHost,
                    "15.5 MB/s",
                    0,            // Progreso inicial
                    "8/8",
                    "Enviando...",
                    "00:00:45"
            });

            // Simulación de progreso mediante un Timer de Swing
            Timer timer = new Timer(100, null);
            final int[] progress = {0};
            timer.addActionListener(e -> {
                progress[0] += (int)(Math.random() * 5 + 1);
                if (progress[0] >= 100) {
                    progress[0] = 100;
                    downloadModel.setValueAt("Completado", 0, 6);
                    downloadModel.setValueAt("Done", 0, 7);
                    timer.stop();
                }
                downloadModel.setValueAt(progress[0], 0, 4); // Actualiza la barra de progreso
            });
            timer.start();
        }
    }


    // Método auxiliar para crear "Cards" de estadísticas
    private JPanel createStatCard(String title, String value, Color color) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JLabel v = new JLabel(value);
        v.setFont(new Font("SansSerif", Font.BOLD, 18));
        v.setForeground(color);
        p.add(t, BorderLayout.NORTH);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    // --- RENDERIZADOR DE PING (COLORES) ---
    static class PingRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(t, v, s, f, r, c);
            int ping = (int) v;
            label.setText(ping + " ms");
            label.setHorizontalAlignment(SwingConstants.CENTER);

            if (ping < 60) label.setForeground(SUCCESS_GREEN);
            else if (ping < 150) label.setForeground(Color.YELLOW);
            else label.setForeground(Color.RED);

            return label;
        }
    }

    private JPanel createGlobalRepositoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Barra de acciones del Repositorio
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnUpload = new JButton("📤 Subir al Servidor");
        JButton btnDownload = new JButton("📥 Descargar a mi PC");
        JButton btnDelete = new JButton("🗑 Eliminar Activo");
        JTextField searchField = new JTextField(20);
        searchField.putClientProperty("JTextField.placeholderText", "Filtrar archivos...");

        toolBar.add(btnUpload);
        toolBar.add(btnDownload);
        toolBar.add(new JSeparator(SwingConstants.VERTICAL));
        toolBar.add(btnDelete);
        toolBar.add(Box.createHorizontalStrut(20));
        toolBar.add(new JLabel("🔍"));
        toolBar.add(searchField);

        // Tabla de Archivos en el Servidor
        String[] cols = {"Nombre del Archivo", "Versión", "Tamaño", "Tipo", "Subido por", "Descargas", "Hash SHA-256"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);

        // Datos de ejemplo del Repositorio Central
        model.addRow(new Object[]{"Setup_Workstation_v2.exe", "2.1.0", "450 MB", "Ejecutable", "Admin_Sist", "124", "A8B2...F1"});
        model.addRow(new Object[]{"Politicas_Seguridad_2026.pdf", "1.0", "2.4 MB", "Documento", "HR_Dept", "89", "C4D1...E2"});
        model.addRow(new Object[]{"Driver_Pack_LAN.zip", "2026.1", "1.2 GB", "Archivo", "Admin_Sist", "45", "99E1...B0"});

        JTable table = new JTable(model);
        table.setRowHeight(30);

        // Panel inferior con detalles del archivo seleccionado
        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setPreferredSize(new Dimension(0, 120));
        detailsPanel.setBorder(new TitledBorder("Metadatos del Servidor"));
        JTextPane infoPane = new JTextPane();
        infoPane.setContentType("text/html");
        infoPane.setText("<html><body style='color:gray; font-family:sans-serif;'>"
                + "<b>Ruta en Servidor:</b> C:\\BitBridge\\Storage\\Public\\... <br>"
                + "<b>Permisos:</b> Solo Lectura para Clientes Estándar <br>"
                + "<b>Última Sincronización:</b> Hoy, 10:15 AM</body></html>");
        infoPane.setEditable(false);
        infoPane.setOpaque(false);
        detailsPanel.add(new JScrollPane(infoPane));

        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(detailsPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createServerPerformancePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 1. Tarjetas de métricas en tiempo real
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        metricsPanel.add(createStatCard("CPU LOAD", "12%", SUCCESS_GREEN));
        metricsPanel.add(createStatCard("RAM USAGE", "1.4 GB / 8 GB", Color.CYAN));
        metricsPanel.add(createStatCard("CONEXIONES TCP", "42", Color.WHITE));
        metricsPanel.add(createStatCard("LATENCIA HUB", "0.5 ms", Color.YELLOW));

        // 2. Gráfico Dinámico (Simulación de carga de red)
        JPanel chartContainer = new JPanel(new BorderLayout());
        chartContainer.setBorder(new CompoundBorder(
                new TitledBorder("Tráfico Entrante/Saliente del Servidor (Mbps)"),
                new EmptyBorder(10,10,10,10)
        ));

        JPanel realTimeChart = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Dibujar Rejilla
                g2.setColor(new Color(50, 50, 50));
                for(int i=0; i<w; i+=50) g2.drawLine(i, 0, i, h);
                for(int i=0; i<h; i+=30) g2.drawLine(0, i, w, i);

                // Simular línea de tráfico de Red (Mbps)
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(NICOTINE_ORANGE);
                int[] data = {h-20, h-40, h-35, h-100, h-80, h-150, h-130, h-180, h-100, h-20};
                int step = w / (data.length - 1);
                for(int i=0; i<data.length-1; i++) {
                    g2.drawLine(i*step, data[i], (i+1)*step, data[i+1]);
                }
            }
        };
        realTimeChart.setBackground(new Color(20, 20, 20));
        chartContainer.add(realTimeChart, BorderLayout.CENTER);

        // 3. Log de System Events (Específicos del servidor)
        JTextArea serverLog = new JTextArea(6, 0);
        serverLog.setEditable(false);
        serverLog.setBackground(new Color(30, 30, 30));
        serverLog.setForeground(new Color(150, 150, 150));
        serverLog.setFont(new Font("Monospaced", Font.PLAIN, 11));
        serverLog.setText(" [INFO] Buffer de red optimizado para 10Gbps\n"
                + " [WARN] El cliente 192.168.1.55 ha intentado 3 conexiones simultáneas\n"
                + " [SYS] Backup automático del índice completado satisfactoriamente");

        panel.add(metricsPanel, BorderLayout.NORTH);
        panel.add(chartContainer, BorderLayout.CENTER);
        panel.add(new JScrollPane(serverLog), BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createTopInfoBar() {
        //JPanel panel = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(35, 35, 35));
        header.setBorder(new EmptyBorder(8, 15, 8, 15));

        // Título y Conexión
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftPanel.setOpaque(false);

        lblServerStatus = new JLabel("● DESCONECTADO");
        lblServerStatus.setForeground(Color.RED);
        lblServerStatus.setFont(new Font("Monospaced", Font.BOLD, 12));

        btnConnect = new JButton("CONECTAR AL HUB");
        btnConnect.setBackground(NICOTINE_ORANGE.darker());
        btnConnect.setFocusPainted(false);

        // Simulación de conexión
        btnConnect.addActionListener(e -> {
            lblServerStatus.setText("○ CONECTANDO...");
            lblServerStatus.setForeground(Color.YELLOW);
            Timer timer = new Timer(1500, ev -> {
                lblServerStatus.setText("● CENTRAL ONLINE");
                lblServerStatus.setForeground(SUCCESS_GREEN);
                btnConnect.setText("DESCONECTAR");
                btnConnect.setBackground(new Color(60, 60, 60));
            });
            timer.setRepeats(false);
            timer.start();
        });

        leftPanel.add(new JLabel("BitBridge v3.0"));
        leftPanel.add(lblServerStatus);
        leftPanel.add(btnConnect);

        // Buscador Global
        JPanel searchContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        searchContainer.setOpaque(false);
        JTextField topSearch = new JTextField(25);
        topSearch.putClientProperty("JTextField.placeholderText", "Búsqueda rápida en red...");
        searchContainer.add(new JLabel("👤"));
        searchContainer.add(topSearch);

        header.add(leftPanel, BorderLayout.WEST);
        header.add(searchContainer, BorderLayout.CENTER);
        header.add(new JButton("☰"), BorderLayout.EAST);

        // Menú desplegable para el botón de configuración
        JPopupMenu configMenu = new JPopupMenu();
        configMenu.add("Ajustes de Red (UPnP)");
        configMenu.add("Directorios Compartidos");
        configMenu.add("Seguridad y Cifrado");
        configMenu.addSeparator();
        configMenu.add("Ver Logs Crudos");

        // Derecha: Acciones y Configuración
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightActions.setOpaque(false);

        JButton btnConfig = new JButton("⚙ CONFIGURACIÓN");
        btnConfig.putClientProperty("JButton.buttonType", "roundRect");
        btnConfig.setBackground(new Color(60, 60, 60));

        btnConfig.addActionListener(e -> configMenu.show(btnConfig, 0, btnConfig.getHeight()));

        rightActions.add(new JLabel("● ONLINE (Hub: bitbridge.net)"));
        rightActions.add(btnConfig);
        rightActions.add(new JButton("👤"));

        //header.add(logo, BorderLayout.WEST);
        header.add(rightActions, BorderLayout.EAST);

        return header;
    }

    private JPanel createAdvancedTransferPanel(boolean isDownload) {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"Archivo", "Usuario", "Estado", "Velocidad", "Progreso", "Tamaño", "Prioridad"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);

        // Datos Mock con porcentajes (Integer para el renderer)
        if(isDownload) {
            model.addRow(new Object[]{"linux_distro_2026.iso", "fedora_boss", "Recibiendo", "12.5 MB/s", 45, "4.2 GB", "Alta"});
            model.addRow(new Object[]{"project_data.zip", "cris_node", "En cola", "0 B/s", 0, "850 MB", "Normal"});
        } else {
            model.addRow(new Object[]{"vacaciones.mp4", "user_remote", "Subiendo", "2.1 MB/s", 88, "120 MB", "Baja"});
        }

        JTable table = new JTable(model);
        table.setRowHeight(30);

        // Renderizador de Barra de Progreso en la columna 4
        table.getColumnModel().getColumn(4).setCellRenderer(new ProgressRenderer());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(new JButton("▶ Reanudar"));
        actions.add(new JButton("⏸ Pausar Todo"));
        actions.add(new JButton("✕ Limpiar Terminados"));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createFriendsManagerPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"Usuario", "Estado", "Velocidad Promedio", "Archivos Compartidos", "Última vez"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{"fedora_master", "🟢 Online", "15 MB/s", "1,240", "Ahora"});
        model.addRow(new Object[]{"cris_dev", "🔴 Offline", "2 MB/s", "45", "hace 2 horas"});
        model.addRow(new Object[]{"linux_ninja", "🟡 Ausente", "8 MB/s", "890", "hace 5 min"});

        JTable table = new JTable(model);
        table.setRowHeight(35);

        JPanel sideTools = new JPanel();
        sideTools.setLayout(new BoxLayout(sideTools, BoxLayout.Y_AXIS));
        sideTools.setBorder(new EmptyBorder(10, 10, 10, 10));
        sideTools.add(new JButton("➕ Añadir Amigo"));
        sideTools.add(Box.createVerticalStrut(10));
        sideTools.add(new JButton("💬 Iniciar Chat"));
        sideTools.add(Box.createVerticalStrut(10));
        sideTools.add(new JButton("📂 Explorar"));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(sideTools, BorderLayout.EAST);
        return panel;
    }

    // --- RENDERIZADOR DE BARRAS DE PROGRESO ---
    static class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressRenderer() {
            super(0, 100);
            setStringPainted(true);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int val = (int) value;
            setValue(val);
            if (val < 100) {
                setForeground(NICOTINE_ORANGE.darker());
            } else {
                setForeground(SUCCESS_GREEN.darker());
            }
            return this;
        }
    }

    // (Otros métodos como createSearchPanel, createSharedExplorerPanel, etc. se mantienen igual de tu estructura base)
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JTextField searchTxt = new JTextField(35);
        searchTxt.putClientProperty("JTextField.placeholderText", "Ej: linux_iso, soundtrack...");
        JButton searchBtn = new JButton("Buscar");
        searchBtn.setBackground(NICOTINE_ORANGE.darker());
        header.add(new JLabel("Término:"));
        header.add(searchTxt);
        header.add(new JComboBox<>(new String[]{"Todo", "Audio", "Video", "Imágenes"}));
        header.add(searchBtn);
        String[] cols = {"Archivo", "Usuario", "Tamaño", "Velocidad", "Slots", "IP"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{"ubuntu-22.04.iso", "fedora_master", "3.4 GB", "50 MB/s", "2/5", "192.168.1.50"});
        JTable table = new JTable(model);
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSharedExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Usuarios en Red");
        DefaultMutableTreeNode user1 = new DefaultMutableTreeNode("fedora_master (Compartido)");
        user1.add(new DefaultMutableTreeNode("Descargas"));
        user1.add(new DefaultMutableTreeNode("Documentos"));
        root.add(user1);
        JTree tree = new JTree(new DefaultTreeModel(root));
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setPreferredSize(new Dimension(250, 0));
        String[] cols = {"Nombre", "Tamaño", "Extensión", "Hash SHA-256"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{"video_tutorial.mp4", "150 MB", "mp4", "a8f2...9c"});
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, new JScrollPane(new JTable(model)));
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultListModel<String> listModel = new DefaultListModel<>();
        listModel.addElement("● fedora_master");
        listModel.addElement("○ cris_dev");
        JList<String> userList = new JList<>(listModel);
        userList.setPreferredSize(new Dimension(180, 0));
        userList.setBackground(BG_DARKER);
        JPanel chatArea = new JPanel(new BorderLayout());
        JTextArea messages = new JTextArea();
        messages.setEditable(false);
        messages.setText(" [16:20] fedora_master: ¿Tienes el archivo de ayer?\n [16:21] Tú: Sí, te lo mando por 'Explorar Compartidos'");
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField inputField = new JTextField();
        JButton sendBtn = new JButton("Enviar");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        chatArea.add(new JScrollPane(messages), BorderLayout.CENTER);
        chatArea.add(inputPanel, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(userList), chatArea);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane createLogConsole() {
        JTextArea log = new JTextArea();
        log.setBackground(BG_DARKER);
        log.setForeground(new Color(180, 180, 180));
        log.setFont(new Font("Monospaced", Font.PLAIN, 11));
        log.setEditable(false);
        log.setText(" 13/01/26 16:18:54 BitBridge Node Iniciado...\n 13/01/26 16:20:10 Escaneo de carpetas locales finalizado.\n 13/01/26 16:21:05 Conectado a 'Fedora-PC' vía P2P.");
        JScrollPane scroll = new JScrollPane(log);
        scroll.setPreferredSize(new Dimension(0, 100));
        return scroll;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(2, 10, 2, 10));
        JLabel right = new JLabel("⬇ 4.2 MB/s | ⬆ 1.1 MB/s | ● En línea: Port 2233");
        right.setForeground(NICOTINE_ORANGE);
        status.add(new JLabel("Listo para transferir"), BorderLayout.WEST);
        status.add(right, BorderLayout.EAST);
        return status;
    }

    /*private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(5, 10, 5, 10));
        JLabel traffic = new JLabel("⬇ 13.4 MB/s | ⬆ 2.1 MB/s | Paquetes: 104,201");
        traffic.setForeground(NICOTINE_ORANGE);
        status.add(new JLabel("Motor P2P: Activo | RAM: 156MB"), BorderLayout.WEST);
        status.add(traffic, BorderLayout.EAST);
        return status;
    }*/

    private JPanel createPlaceholderPanel(String icon, String text) {
        JPanel p = new JPanel(new GridBagLayout());
        JLabel l = new JLabel("<html><center><font size='60'>" + icon + "</font><br><br>" + text + "</center></html>");
        l.setForeground(Color.GRAY);
        p.add(l);
        return p;
    }

    private void setupTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("TabbedPane.selectedBackground", NICOTINE_ORANGE.darker());
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            UIManager.put("ProgressBar.arc", 0); // Estilo Nicotine es cuadrado
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BitBridgeNicotineUI().setVisible(true));
    }
}
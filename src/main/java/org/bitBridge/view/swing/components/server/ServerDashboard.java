package org.bitBridge.view.swing.components.server;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.Server;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.utils.NetworkManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class ServerDashboard extends JFrame {
    private final ServerStats stats;
    private final int port;
    private final String localIp;
    private final String startTimeStr;

    // Métricas
    private JLabel lblClientCount, lblMsgCount, lblRAM, lblBytes, lblThreads, lblActiveTransfers;
    private DefaultTableModel tableModel;
    private DefaultTableModel threadModel;
    private JTextArea txtHistory;
    private Timer guiTimer;

    public ServerDashboard(Server server) {
        this.stats = server.getStats();
        this.port = server.getPORT();
        this.localIp = NetworkManager.getLocalIp();
        this.startTimeStr = new java.util.Date().toString();

        setTitle("BitBridge | Server Management Console");
        setSize(1200, 850);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(15, 18, 22));
        setLayout(new BorderLayout(10, 10));

        initUI();
        startMonitoring();
    }

    private void initUI() {
        // --- 1. PANEL NORTE: HEADER & CONEXIÓN ---
        JPanel headerPanel = new JPanel(new BorderLayout(20, 0));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(20, 20, 10, 20));

        JPanel connInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        connInfo.setBackground(new Color(25, 30, 35));
        connInfo.setBorder(new LineBorder(new Color(45, 50, 60), 1));

        connInfo.add(createDetailLabel("IP SERVIDOR", localIp, "#00FF7F"));
        connInfo.add(createDetailLabel("PUERTO", String.valueOf(port), "#FFD700"));
        connInfo.add(createDetailLabel("ESTADO", "ONLINE", "#00BFFF"));

        headerPanel.add(connInfo, BorderLayout.WEST);
        headerPanel.add(new JLabel("<html><font color='gray'>JVM: " + System.getProperty("java.version") + "<br>Iniciado: " + startTimeStr + "</font></html>"), BorderLayout.EAST);

        // --- 2. PANEL SUR: MÉTRICAS RÁPIDAS ---
        JPanel metricsPanel = new JPanel(new GridLayout(1, 6, 10, 0));
        metricsPanel.setOpaque(false);
        metricsPanel.setBorder(new EmptyBorder(10, 20, 20, 20));

        lblClientCount = createMetricCard("Nodos");
        lblMsgCount = createMetricCard("Mensajes");
        lblRAM = createMetricCard("Uso RAM");
        lblBytes = createMetricCard("Tráfico");
        lblThreads = createMetricCard("Hilos");
        lblActiveTransfers = createMetricCard("Transferencias");

        metricsPanel.add(lblClientCount); metricsPanel.add(lblMsgCount); metricsPanel.add(lblRAM);
        metricsPanel.add(lblBytes); metricsPanel.add(lblThreads); metricsPanel.add(lblActiveTransfers);

        // --- 3. PANEL CENTRAL: TABLAS E INSPECTOR ---
        JPanel mainGrid = new JPanel(new GridLayout(2, 2, 15, 15));
        mainGrid.setOpaque(false);
        mainGrid.setBorder(new EmptyBorder(0, 20, 0, 20));

        // A. Tabla de Clientes
        // En initUI, cambia la forma en que creas la tabla de clientes:
        tableModel = new DefaultTableModel(new String[]{"IP", "Uptime", "Nick", "Role"}, 0);
        JTable clientTable = new JTable(tableModel); // Creamos la tabla explícitamente con el modelo
        mainGrid.add(createTablePanel(clientTable, " CLIENTES ACTIVOS ")); // Usamos el helper de JTable

        // B. Inspector de Hilos (NUEVO)
        threadModel = new DefaultTableModel(new String[]{"Nombre del Hilo", "Estado", "Tipo"}, 0);
        JTable tTable = new JTable(threadModel);
        tTable.setDefaultRenderer(Object.class, new ThreadStatusRenderer()); // Colores!
        mainGrid.add(createTablePanel(tTable, " MONITOR DE HILOS (JVM) "));

        // C. Log de Actividad
        txtHistory = new JTextArea();
        txtHistory.setEditable(false);
        txtHistory.setBackground(new Color(10, 12, 14));
        txtHistory.setForeground(new Color(0, 255, 65));
        txtHistory.setFont(new Font("Monospaced", Font.PLAIN, 12));
        mainGrid.add(new JScrollPane(txtHistory) {{
            setBorder(createTitledBorder(" LOG DE EVENTOS CRÍTICOS "));
        }});

        // D. Quick Actions / System Stats
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 40));
        actionsPanel.setOpaque(false);
        JButton btnGC = new JButton("FORZAR GC (Limpiar Memoria)");
        btnGC.addActionListener(e -> System.gc());
        actionsPanel.add(btnGC);
        mainGrid.add(actionsPanel);

        add(headerPanel, BorderLayout.NORTH);
        add(mainGrid, BorderLayout.CENTER);
        add(metricsPanel, BorderLayout.SOUTH);
    }

    // --- HELPER UI METHODS ---

    private JPanel createTablePanel(DefaultTableModel model, String title) {
        return createTablePanel(new JTable(model), title);
    }

    private JPanel createTablePanel(JTable table, String title) {
        table.setBackground(new Color(30, 35, 40));
        table.setForeground(Color.WHITE);
        table.setGridColor(Color.DARK_GRAY);
        table.setRowHeight(25);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(createTitledBorder(title));

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(scroll);
        return p;
    }

    private TitledBorder createTitledBorder(String title) {
        return BorderFactory.createTitledBorder(new LineBorder(Color.DARK_GRAY), title, TitledBorder.LEFT, TitledBorder.TOP, null, Color.GRAY);
    }

    private JLabel createDetailLabel(String title, String val, String hex) {
        return new JLabel("<html><center><font color='gray' size='3'>" + title + "</font><br>" +
                "<font color='" + hex + "' size='5'><b>" + val + "</b></font></center></html>");
    }

    private JLabel createMetricCard(String title) {
        JLabel label = new JLabel();
        label.setOpaque(true);
        label.setBackground(new Color(25, 30, 35));
        label.setBorder(new LineBorder(new Color(45, 50, 60)));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    private void updateMetricText(JLabel label, String title, String value, Color color) {
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        label.setText("<html><center><font color='#adb5bd'>" + title + "</font><br>" +
                "<font color='" + hex + "' size='5'><b>" + value + "</b></font></center></html>");
    }

    // --- MONITORING LOGIC ---

    private void refreshData() {



        // 1. Métricas Base
        updateMetricText(lblClientCount, "Nodos", String.valueOf(stats.getClientCount()), Color.WHITE);
        updateMetricText(lblMsgCount, "Mensajes", String.valueOf(stats.getTotalMessages()), Color.WHITE);
        updateMetricText(lblRAM, "RAM", stats.getMemoryUsageFormat(), new Color(255, 105, 180));
        updateMetricText(lblBytes, "Tráfico", stats.formatBytes(stats.getTotalBytes()), Color.CYAN);

        // 2. Lógica de Hilos
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
        threadModel.setRowCount(0);
        int msgThreads = 0;
        int fileThreads = 0;

        for (Thread t : allThreads.keySet()) {
            String name = t.getName();
            // Contamos solo los que están realmente trabajando (RUNNABLE)
            if (name.startsWith("MSG-Worker") && t.getState() == Thread.State.RUNNABLE) {
                msgThreads++;
            } else if (name.startsWith("FT-Pool") && t.getState() == Thread.State.RUNNABLE) {
                fileThreads++;
            }

            threadModel.addRow(new Object[]{ name, t.getState().toString(), t.isDaemon() ? "SYSTEM" : "USER" });
        }

// Actualizar etiquetas individuales en la UI
        updateMetricText(lblMsgCount, "Mensajeros Activos", String.valueOf(msgThreads), Color.WHITE);
        updateMetricText(lblActiveTransfers, "Archivos en Curso", String.valueOf(fileThreads), new Color(0, 255, 127));
        updateMetricText(lblThreads, "Hilos JVM", String.valueOf(allThreads.size()), Color.ORANGE);

        // 3. Tabla de Clientes
        tableModel.setRowCount(0);
        // Actualizar tabla de clientes
        List<ClientInfo> clients = stats.getConnectedClients();
        if (tableModel.getRowCount() != clients.size()) { // Solo refrescar si el conteo cambió
            tableModel.setRowCount(0);
            for (ClientInfo client : clients) {
                tableModel.addRow(new Object[]{
                        client.getAddress(),
                        stats.formatUptime(client.getConnectionTime()),
                        client.getNick(),
                        "ACTIVE"
                });
            }
        }

        // 4. Log
        txtHistory.setText("");
        List<String> history = stats.getMessageHistory();
        int start = Math.max(0, history.size() - 15);
        for (int i = start; i < history.size(); i++) {
            txtHistory.append(" [SYSTEM] > " + history.get(i) + "\n");
        }
    }

    private void startMonitoring() {
        guiTimer = new Timer(1000, e -> refreshData());
        guiTimer.start();
    }

    // --- RENDERER PARA COLOREAR HILOS ---
    static class ThreadStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String state = table.getValueAt(row, 1).toString();
            String name = table.getValueAt(row, 0).toString();

            if (state.equals("RUNNABLE")) c.setForeground(new Color(0, 255, 127));
            else if (state.startsWith("WAITING") || state.contains("TIMED")) c.setForeground(Color.YELLOW);
            else if (state.equals("BLOCKED")) c.setForeground(Color.RED);
            else c.setForeground(Color.GRAY);

            if (name.startsWith("FT-Pool")) setFont(getFont().deriveFont(Font.BOLD));
            else setFont(getFont().deriveFont(Font.PLAIN));

            return c;
        }
    }
}
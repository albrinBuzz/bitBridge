package org.bitBridge.view.swing.components.server;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.server.core.Server;
import org.bitBridge.utils.NetworkManager; // Asumiendo que tienes esta utilidad

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ServerDashboard extends JFrame {
    private final ServerStats stats;
    private final int port;
    private final String localIp;
    private final String startTimeStr;

    private JLabel lblIpValue, lblPortValue;
    private JLabel lblClientCount, lblMsgCount, lblUptime, lblRAM, lblBytes, lblThreads;
    private DefaultTableModel tableModel;
    private JTextArea txtHistory;
    private Timer guiTimer;

    public ServerDashboard(Server server) {
        this.stats = server.getStats();
        this.port = server.getPORT();
        this.localIp = NetworkManager.getLocalIp(); // IP actual de la máquina
        this.startTimeStr = new java.util.Date().toString();

        setTitle("BitBridge Dashboard | Monitor de Red");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(20, 25, 29));
        setLayout(new BorderLayout(10, 10));

        initUI();
        startMonitoring();
    }

    private void initUI() {
        // --- 1. PANEL SUPERIOR: INFO DE CONEXIÓN (HIGHLIGHT) ---
        JPanel topPanel = new JPanel(new BorderLayout(15, 0));
        topPanel.setOpaque(false);
        topPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Card de Conexión Manual
        JPanel connectionCard = new JPanel(new GridLayout(1, 2, 20, 0));
        connectionCard.setBackground(new Color(32, 38, 44));
        connectionCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(0, 191, 255), 1),
                new EmptyBorder(10, 20, 10, 20)
        ));

        lblIpValue = createConnectionDetail("DIRECCIÓN IP", localIp, new Color(0, 255, 127));
        lblPortValue = createConnectionDetail("PUERTO ACTIVO", String.valueOf(port), new Color(255, 215, 0));

        connectionCard.add(lblIpValue);
        connectionCard.add(lblPortValue);

        // Info de sistema secundaria (Derecha)
        JPanel systemInfo = new JPanel(new GridLayout(2, 1));
        systemInfo.setOpaque(false);
        JLabel lblJava = new JLabel("Java: " + System.getProperty("java.version"));
        lblJava.setForeground(Color.GRAY);
        JLabel lblStart = new JLabel("Iniciado: " + startTimeStr);
        lblStart.setForeground(Color.GRAY);
        systemInfo.add(lblJava);
        systemInfo.add(lblStart);

        topPanel.add(connectionCard, BorderLayout.CENTER);
        topPanel.add(systemInfo, BorderLayout.EAST);

        // --- 2. PANEL DE MÉTRICAS (GRID) ---
        JPanel statusPanel = new JPanel(new GridLayout(1, 6, 10, 0));
        statusPanel.setOpaque(false);
        statusPanel.setBorder(new EmptyBorder(0, 15, 15, 15));

        lblClientCount = createMetricLabel("Clientes", "0", Color.CYAN);
        lblMsgCount = createMetricLabel("Mensajes", "0", Color.CYAN);
        lblUptime = createMetricLabel("Uptime", "0s", Color.CYAN);
        lblRAM = createMetricLabel("RAM", "0MB", Color.CYAN);
        lblBytes = createMetricLabel("Tráfico", "0B", Color.CYAN);
        lblThreads = createMetricLabel("Hilos", "0", Color.CYAN);

        statusPanel.add(lblClientCount); statusPanel.add(lblMsgCount); statusPanel.add(lblUptime);
        statusPanel.add(lblRAM); statusPanel.add(lblBytes); statusPanel.add(lblThreads);

        // --- 3. CUERPO CENTRAL (TABLA Y LOG) ---
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 15));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(0, 15, 15, 15));

        // Tabla
        String[] columns = {"IP Cliente", "Tiempo Conexión", "Nick", "Estado"};
        tableModel = new DefaultTableModel(columns, 0);
        JTable table = new JTable(tableModel);
        table.setRowHeight(25);
        JScrollPane scrollTable = new JScrollPane(table);
        scrollTable.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.DARK_GRAY), " NODOS CONECTADOS "));

        // Log
        txtHistory = new JTextArea();
        txtHistory.setEditable(false);
        txtHistory.setBackground(new Color(10, 12, 15));
        txtHistory.setForeground(new Color(0, 255, 65));
        txtHistory.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollHistory = new JScrollPane(txtHistory);
        scrollHistory.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.DARK_GRAY), " LOG DE ACTIVIDAD "));

        centerPanel.add(scrollTable);
        centerPanel.add(scrollHistory);

        add(topPanel, BorderLayout.NORTH);
        add(statusPanel, BorderLayout.SOUTH); // Métricas abajo para dar peso a la conexión arriba
        add(centerPanel, BorderLayout.CENTER);
    }

    private JLabel createConnectionDetail(String title, String value, Color valueColor) {
        JLabel label = new JLabel("<html><body style='text-align: center;'>" +
                "<font color='#adb5bd' size='3'>" + title + "</font><br>" +
                "<font color='" + toHex(valueColor) + "' size='6'><b>" + value + "</b></font>" +
                "</body></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    private JLabel createMetricLabel(String title, String value, Color color) {
        JLabel label = new JLabel("<html><center><font color='gray'>" + title + "</font><br>" +
                "<font color='white'>" + value + "</font></center></html>");
        label.setOpaque(true);
        label.setBackground(new Color(40, 44, 52));
        label.setBorder(new LineBorder(Color.DARK_GRAY));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    private void refreshData() {
        // Actualizar Métricas
        updateMetricText(lblClientCount, "Clientes", String.valueOf(stats.getClientCount()));
        updateMetricText(lblMsgCount, "Mensajes", String.valueOf(stats.getTotalMessages()));
        updateMetricText(lblUptime, "Uptime", stats.getUptime());
        updateMetricText(lblRAM, "RAM", stats.getMemoryUsageFormat());
        updateMetricText(lblBytes, "Tráfico", stats.formatBytes(stats.getTotalBytes()));
        updateMetricText(lblThreads, "Hilos", String.valueOf(Thread.activeCount()));

        // Actualizar Tabla
        tableModel.setRowCount(0);
        for (ClientInfo client : stats.getConnectedClients()) {
            tableModel.addRow(new Object[]{
                    client.getAddress(),
                    stats.formatUptime(client.getConnectionTime()),
                    client.getNick(),
                    "ACTIVO"
            });
        }

        // Actualizar Log
        txtHistory.setText("");
        List<String> history = stats.getMessageHistory();
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            txtHistory.append(" > " + history.get(i) + "\n");
        }
    }

    private void updateMetricText(JLabel label, String title, String value) {
        label.setText("<html><center><font color='#adb5bd'>" + title + "</font><br>" +
                "<font color='white' size='4'><b>" + value + "</b></font></center></html>");
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void startMonitoring() {
        guiTimer = new Timer(1000, e -> refreshData());
        guiTimer.start();
    }
}
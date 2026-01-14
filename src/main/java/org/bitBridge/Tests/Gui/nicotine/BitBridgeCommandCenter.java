package org.bitBridge.Tests.Gui.nicotine;



import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

public class BitBridgeCommandCenter extends JFrame {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color TERMINAL_GREEN = new Color(0, 255, 65);
    private static final Color BG_DARK = new Color(18, 18, 18);

    public BitBridgeCommandCenter() {
        setupTheme();
        setTitle("BitBridge Pro :: Command Center [Admin Mode]");
        setSize(1500, 950);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- 1. DASHBOARD DE TELEMETRÍA (SUPERIOR) ---
        add(createTelemetryDashboard(), BorderLayout.NORTH);

        // --- 2. ÁREA CENTRAL (GESTIÓN DE COLAS Y EXPLORACIÓN) ---
        JSplitPane centralSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centralSplit.setDividerLocation(300);
        centralSplit.setLeftComponent(createNodeInspector()); // Lista de nodos con stats

        JTabbedPane operationsTabs = new JTabbedPane();
        operationsTabs.addTab("📥 Cola de Prioridad", createAdvancedQueuePanel());
        operationsTabs.addTab("📊 Analizador de Tráfico", createTrafficAnalyzer());
        operationsTabs.addTab("🛡️ Seguridad & Hashes", createSecurityPanel());

        centralSplit.setRightComponent(operationsTabs);
        add(centralSplit, BorderLayout.CENTER);

        // --- 3. CONSOLA DE SOCKETS (INFERIOR - REDIMENSIONABLE) ---
        add(createSocketConsole(), BorderLayout.SOUTH);
    }

    /**
     * DASHBOARD DE TELEMETRÍA: Datos en tiempo real del motor P2P
     */
    private JPanel createTelemetryDashboard() {
        JPanel dashboard = new JPanel(new GridLayout(1, 5, 10, 0));
        dashboard.setBackground(BG_DARK);
        dashboard.setBorder(new EmptyBorder(15, 15, 15, 15));

        dashboard.add(createMetricCard("NODOS CONECTADOS", "42", "Global Mesh"));
        dashboard.add(createMetricCard("ANCHO DE BANDA", "850 MB/s", "Pico actual"));
        dashboard.add(createMetricCard("ESTADO DEL PUERTO", "OPEN", "Port: 2233 (TCP)"));
        dashboard.add(createMetricCard("SESIÓN TOTAL", "1.2 TB", "Up: 400GB | Down: 800GB"));
        dashboard.add(createMetricCard("LATENCIA (AVG)", "12ms", "Estabilidad: 99.9%"));

        return dashboard;
    }

    /**
     * COLA DE PRIORIDAD: Gestión avanzada de archivos con Drag & Drop
     */
    private JPanel createAdvancedQueuePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"Prioridad", "Archivo", "Nodo Destino", "Progreso", "Hilos", "Buffer", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);

        model.addRow(new Object[]{"⚡ CRÍTICA", "database_prod.sql", "Fedora-Server", "88%", "16 hilos", "256KB", "Transfiriendo"});
        model.addRow(new Object[]{"🟡 NORMAL", "project_video.mp4", "Cris-Mac", "12%", "4 hilos", "64KB", "En espera"});

        JTable table = new JTable(model);
        table.setRowHeight(35);
        table.getTableHeader().setReorderingAllowed(true);

        // Panel de control lateral para la cola
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(new EmptyBorder(10,10,10,10));
        controls.add(new JButton("🔼 Subir Prioridad"));
        controls.add(Box.createVerticalStrut(5));
        controls.add(new JButton("🔽 Bajar Prioridad"));
        controls.add(Box.createVerticalStrut(15));
        controls.add(new JButton("🛑 Force Stop"));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.EAST);
        return panel;
    }

    /**
     * INSPECTOR DE NODOS: Ver especificaciones de otros usuarios
     */
    private JPanel createNodeInspector() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("NODOS ACTIVOS EN MALLA"));

        DefaultListModel<String> nodes = new DefaultListModel<>();
        nodes.addElement("<html><b>Fedora-Master</b><br><font color='gray'>OS: Linux | RAM: 32GB</font></html>");
        nodes.addElement("<html><b>Cris-Laptop</b><br><font color='gray'>OS: Windows | RAM: 16GB</font></html>");

        JList<String> list = new JList<>(nodes);
        list.setFixedCellHeight(50);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        return panel;
    }

    /**
     * CONSOLA DE SOCKETS: Registro crudo de bytes y handshakes
     */
    private JPanel createSocketConsole() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(0, 180));

        JTextArea console = new JTextArea();
        console.setBackground(Color.BLACK);
        console.setForeground(TERMINAL_GREEN);
        console.setFont(new Font("Monospaced", Font.PLAIN, 12));
        console.setText("> [SOCKET] Handshake iniciado con 192.168.1.50:2233\n" +
                "> [ACK] Paquete de sincronización recibido (Seq: 1024)\n" +
                "> [DATA] Enviando Bloque #45 - 64KB - Checksum: OK\n" +
                "> [SYSTEM] Buffer overflow prevenido. Ajustando ventana de recepción...");

        JScrollPane scroll = new JScrollPane(console);
        scroll.setBorder(new TitledBorder(null, " RAW SOCKET TELEMETRY ", 0, 0, null, Color.DARK_GRAY));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /**
     * ANALIZADOR DE TRÁFICO: Gráficos y flujos de datos
     */
    private JPanel createTrafficAnalyzer() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel("<html><center><font size='5'>📊</font><br>Analizador de Espectro de Red en construcción...<br>Monitoreando puertos UDP/TCP</center></html>"));
        return panel;
    }

    private JPanel createSecurityPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JCheckBox("Verificación Forzada de SHA-256 en cada bloque"));
        panel.add(new JCheckBox("Cifrado AES-256 de extremo a extremo (E2EE)"));
        return panel;
    }

    // --- MÉTODOS DE ESTILO ---
    private JPanel createMetricCard(String title, String value, String sub) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(30, 30, 30));
        card.setBorder(new CompoundBorder(new LineBorder(Color.DARK_GRAY), new EmptyBorder(10, 10, 10, 10)));

        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.BOLD, 10));
        t.setForeground(Color.GRAY);

        JLabel v = new JLabel(value);
        v.setFont(new Font("SansSerif", Font.BOLD, 20));
        v.setForeground(NICOTINE_ORANGE);

        JLabel s = new JLabel(sub);
        s.setFont(new Font("SansSerif", Font.PLAIN, 9));
        s.setForeground(Color.DARK_GRAY);

        card.add(t, BorderLayout.NORTH);
        card.add(v, BorderLayout.CENTER);
        card.add(s, BorderLayout.SOUTH);
        return card;
    }

    private void setupTheme() {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception e) {}
        UIManager.put("Table.alternateRowColor", new Color(25, 25, 25));
        UIManager.put("ScrollBar.showButtons", true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BitBridgeCommandCenter().setVisible(true));
    }
}
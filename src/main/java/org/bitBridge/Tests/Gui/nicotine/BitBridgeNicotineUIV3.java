package org.bitBridge.Tests.Gui.nicotine;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
public class BitBridgeNicotineUIV3
        extends JFrame {

    // Paleta de colores técnica
    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARK = new Color(25, 25, 25);
    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);
    private static final Color TERMINAL_BLACK = new Color(15, 15, 15);

    private JLabel lblServerStatus;
    private JTextArea mainLogArea;

    public BitBridgeNicotineUIV3() {
        setupTheme();
        setTitle("BitBridge Pro - Node Identity: [Node-7742]");
        setSize(1450, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. HEADER (Barra superior)
        add(createFullHeader(), BorderLayout.NORTH);

        // 2. CUERPO (Tabs con todas las funciones)
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.putClientProperty("JTabbedPane.showTabSeparators", true);

        mainTabs.addTab("🔍 Buscar Archivos", createSearchPanel());
        mainTabs.addTab("⚡ Transfer Engine", createCoreTransferEngine());
        mainTabs.addTab("📂 Explorar Compartidos", createSharedExplorerPanel());
        mainTabs.addTab("👥 Red de Amigos", createFriendsManagerPanel());
        mainTabs.addTab("💬 Chat Privado", createChatPanel());

        // 3. PIE (Consola y Status)
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(createLogConsole(), BorderLayout.CENTER);
        southPanel.add(createStatusBar(), BorderLayout.SOUTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainTabs, southPanel);
        mainSplit.setDividerLocation(600);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);
    }

    // --- VISTA 1: BÚSQUEDA CON FILTROS LATERALES ---
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Filtros laterales
        JPanel sideFilters = new JPanel();
        sideFilters.setLayout(new BoxLayout(sideFilters, BoxLayout.Y_AXIS));
        sideFilters.setPreferredSize(new Dimension(200, 0));
        sideFilters.setBorder(new CompoundBorder(new MatteBorder(0, 0, 0, 1, Color.DARK_GRAY), new EmptyBorder(10,10,10,10)));

        sideFilters.add(new JLabel("FILTROS"));
        sideFilters.add(new JCheckBox("Solo con Slots", true));
        sideFilters.add(new JCheckBox("Bitrate > 128kbps"));
        sideFilters.add(Box.createVerticalStrut(10));
        sideFilters.add(new JLabel("Tipo:"));
        sideFilters.add(new JComboBox<>(new String[]{"Cualquiera", "Audio", "Video", "ISO"}));

        // Barra superior de búsqueda
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchTxt = new JTextField(40);
        searchTxt.putClientProperty("JTextField.placeholderText", "Escriba su búsqueda aquí...");
        JButton btnSearch = new JButton("Buscar en Red");
        btnSearch.setBackground(NICOTINE_ORANGE.darker());
        topBar.add(searchTxt); topBar.add(btnSearch);

        // Tabla de resultados
        String[] cols = {"Archivo", "Usuario", "Tamaño", "Velocidad", "Slots", "IP/Host"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{"kali-linux-2026.iso", "fedora_master", "3.9 GB", "12 MB/s", "2/5", "192.168.1.50"});
        model.addRow(new Object[]{"project_alpha_source.zip", "dev_zero", "150 MB", "2 MB/s", "1/2", "10.0.0.12"});

        panel.add(sideFilters, BorderLayout.WEST);
        panel.add(topBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        return panel;
    }

    // --- VISTA 2: MOTOR DE TRANSFERENCIA (EL CORAZÓN) ---
    private JPanel createCoreTransferEngine() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"ID", "Archivo", "Usuario", "Velocidad", "Progreso", "Hilos", "Estado", "ETA"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{"#01", "shodan_v3.bin", "mirror_01", "15.4 MB/s", 68, "16/16", "Descargando", "00:02:15"});
        model.addRow(new Object[]{"#02", "backup_site.tar.gz", "admin_host", "0 B/s", 12, "0/16", "En espera", "Pendiente"});

        JTable table = new JTable(model);
        table.setRowHeight(35);
        table.getColumnModel().getColumn(4).setCellRenderer(new ProgressRenderer());

        // Telemetría Inferior
        JPanel telemetry = new JPanel(new GridLayout(1, 2, 5, 0));
        telemetry.setPreferredSize(new Dimension(0, 180));

        JPanel graphMock = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g); g.setColor(NICOTINE_ORANGE);
                int[] points = {100, 80, 120, 60, 140, 50, 90, 40};
                for(int i=0; i<points.length-1; i++) g.drawLine(i*50, points[i], (i+1)*50, points[i+1]);
            }
        };
        graphMock.setBackground(TERMINAL_BLACK);
        graphMock.setBorder(new TitledBorder("BANDWIDTH TELEMETRY"));

        JTextArea threadLog = new JTextArea(" [SOCKET-1] Offset 0x0 -> ACK\n [SOCKET-2] Chunk #42 Received\n [SOCKET-3] Validating Checksum...");
        threadLog.setBackground(TERMINAL_BLACK); threadLog.setForeground(SUCCESS_GREEN);
        threadLog.setFont(new Font("Monospaced", Font.PLAIN, 11));

        telemetry.add(graphMock); telemetry.add(new JScrollPane(threadLog));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), telemetry);
        split.setDividerLocation(380);

        panel.add(createTransferToolbar(), BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    // --- VISTA 3: EXPLORADOR DE ARCHIVOS COMPARTIDOS ---
    private JPanel createSharedExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Red P2P (Nodos)");
        DefaultMutableTreeNode user = new DefaultMutableTreeNode("fedora_master [Compartiendo]");
        user.add(new DefaultMutableTreeNode("Software"));
        user.add(new DefaultMutableTreeNode("Documents"));
        root.add(user);

        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setBackground(BG_DARK);

        String[] cols = {"Nombre", "Tamaño", "Extensión", "Checksum"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{"patch_v4.exe", "12 MB", "exe", "882F...A1"});

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), new JScrollPane(new JTable(model)));
        split.setDividerLocation(250);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    // --- VISTA 4: GESTOR DE AMIGOS ---
    private JPanel createFriendsManagerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"Amigo", "Estado", "Download Spd", "Upload Spd", "Última Conexión"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{"cris_dev", "🟢 ONLINE", "4.2 MB/s", "1.1 MB/s", "Ahora"});
        model.addRow(new Object[]{"linux_boss", "🔴 OFFLINE", "---", "---", "12/01/26"});

        JTable table = new JTable(model);
        table.setRowHeight(35);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(new JButton("➕ Añadir Nodo"));
        btnPanel.add(new JButton("💬 Iniciar Chat"));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // --- VISTA 5: CHAT PRIVADO ---
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultListModel<String> userListModel = new DefaultListModel<>();
        userListModel.addElement("● fedora_master");
        userListModel.addElement("○ cris_dev");
        JList<String> list = new JList<>(userListModel);
        list.setBackground(TERMINAL_BLACK);

        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(BG_DARK);
        chatArea.setText(" [17:00] <fedora_master> Hola, ¿tienes el ISO de Debian?\n [17:01] <Tú> Sí, está en mi carpeta /Software");

        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField input = new JTextField();
        JButton send = new JButton("Enviar");
        inputPanel.add(input, BorderLayout.CENTER); inputPanel.add(send, BorderLayout.EAST);

        JPanel chatContainer = new JPanel(new BorderLayout());
        chatContainer.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        chatContainer.add(inputPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), chatContainer);
        split.setDividerLocation(180);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    // --- COMPONENTES AUXILIARES (HEADER, FOOTER, LOGS) ---
    private JPanel createFullHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(35, 35, 35));
        header.setBorder(new EmptyBorder(10, 15, 10, 15));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        left.setOpaque(false);
        lblServerStatus = new JLabel("● OFFLINE"); lblServerStatus.setForeground(Color.RED);
        JButton btnConnect = new JButton("CONECTAR AL HUB");
        btnConnect.addActionListener(e -> simulateConnection());

        left.add(new JLabel("BITBRIDGE V4.0 FULL")); left.add(lblServerStatus); left.add(btnConnect);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        JButton btnConfig = new JButton("⚙ CONFIGURACIÓN");
        btnConfig.addActionListener(e -> showConfigDialog());
        right.add(new JLabel("RAM: 124MB / CPU: 5%")); right.add(btnConfig);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private void showConfigDialog() {
        JDialog d = new JDialog(this, "Ajustes Técnicos", true);
        d.setSize(500, 400); d.setLocationRelativeTo(this);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Red", new JLabel("Mapeo de Puerto: 2233"));
        tabs.addTab("Carpetas", new JLabel("Descargas: /home/user/bitbridge"));
        tabs.addTab("Cifrado", new JLabel("Fuerza: AES-256-GCM"));
        d.add(tabs); d.setVisible(true);
    }

    private JScrollPane createLogConsole() {
        mainLogArea = new JTextArea();
        mainLogArea.setBackground(TERMINAL_BLACK);
        mainLogArea.setForeground(SUCCESS_GREEN);
        mainLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        mainLogArea.setText(" > Node ID: BitBridge-7742 Online\n > Scanning shared folders... Done.\n > Mapping UPnP Port 2233... Success.");
        return new JScrollPane(mainLogArea);
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(5, 15, 5, 15));
        JLabel speed = new JLabel("⬇ 15.4 MB/s | ⬆ 1.1 MB/s | Pairs: 42");
        speed.setForeground(NICOTINE_ORANGE);
        status.add(new JLabel("Listo para transmisión de datos"), BorderLayout.WEST);
        status.add(speed, BorderLayout.EAST);
        return status;
    }

    private JPanel createTransferToolbar() {
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tb.add(new JButton("▶ Iniciar")); tb.add(new JButton("⏸ Pausar")); tb.add(new JButton("🗑 Limpiar"));
        return tb;
    }

    private void simulateConnection() {
        lblServerStatus.setText("○ CONECTANDO..."); lblServerStatus.setForeground(Color.YELLOW);
        new Timer(1500, e -> {
            lblServerStatus.setText("● CENTRAL ONLINE"); lblServerStatus.setForeground(SUCCESS_GREEN);
        }).start();
    }

    private void setupTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("TabbedPane.selectedBackground", NICOTINE_ORANGE.darker());
            UIManager.put("ProgressBar.foreground", NICOTINE_ORANGE);
        } catch (Exception e) {}
    }

    static class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressRenderer() { super(0, 100); setStringPainted(true); }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            setValue((int)v); return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BitBridgeNicotineUIV3().setVisible(true));
    }
}

package org.bitBridge.Tests.Gui.nicotine;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Vector;

public class BitBridgeNicotineUIV2
        extends JFrame {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARKER = new Color(25, 25, 25);
    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);

    public BitBridgeNicotineUIV2() {
        setupTheme();
        setTitle("BitBridge Pro - Advanced P2P Management");
        setSize(1350, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- 1. BARRA DE ESTADO DE RED SUPERIOR ---
        add(createAdvancedHeader(), BorderLayout.NORTH);

        // --- 2. SISTEMA DE PESTAÑAS COMPLEJO ---
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.putClientProperty("JTabbedPane.showTabSeparators", true);

        mainTabs.addTab("🔍 Búsqueda Global", createAdvancedSearchPanel());
        mainTabs.addTab("📥 Cola de Descargas", createComplexTransferPanel(true));
        mainTabs.addTab("📤 Monitor de Subidas", createComplexTransferPanel(false));
        mainTabs.addTab("📂 Mi Biblioteca", createLibraryManagementPanel());
        mainTabs.addTab("📊 Estadísticas", createStatsDashboard());
        mainTabs.addTab("💬 Mensajería", createChatPanel());

        // --- 3. PANEL DIVIDIDO PARA LOGS TÉCNICOS ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setTopComponent(mainTabs);
        mainSplit.setBottomComponent(createTechnicalLogPanel());
        mainSplit.setDividerLocation(650);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    // --- CONTENIDO: BÚSQUEDA CON FILTROS LATERALES ---
    private JPanel createAdvancedSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Filtros laterales (Estilo Soulseek/Nicotine)
        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setPreferredSize(new Dimension(200, 0));
        filterPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        filterPanel.add(new JLabel("FILTRAR POR:"));
        filterPanel.add(new JCheckBox("Solo usuarios con slots"));
        filterPanel.add(new JCheckBox("Calidad alta (> 320kbps)"));
        filterPanel.add(new JSeparator());
        filterPanel.add(new JLabel("TAMAÑO MÍNIMO (MB):"));
        filterPanel.add(new JSpinner(new SpinnerNumberModel(0, 0, 10000, 10)));

        // Tabla de resultados con más columnas
        String[] cols = {"Archivo", "Usuario", "Tamaño", "Bitrate", "Velocidad", "Ruta", "IP"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        model.addRow(new Object[]{"kernel_linux.tar.gz", "linus_node", "1.2 GB", "--", "100 MB/s", "/source/kernel", "192.168.1.1"});

        panel.add(filterPanel, BorderLayout.WEST);
        panel.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        return panel;
    }

    // --- CONTENIDO: EXPLORADOR DE BIBLIOTECA LOCAL ---
    private JPanel createLibraryManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Mi Contenido Compartido");
        root.add(new DefaultMutableTreeNode("Musica (150 archivos)"));
        root.add(new DefaultMutableTreeNode("Software (12 proyectos)"));

        JTree tree = new JTree(new DefaultTreeModel(root));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(new JButton("Añadir Carpeta"));
        toolbar.add(new JButton("Escanear de nuevo"));

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(tree), BorderLayout.CENTER);
        return panel;
    }

    // --- CONTENIDO: DASHBOARD DE ESTADÍSTICAS ---
    private JPanel createStatsDashboard() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        panel.add(createStatBox("Uptime del Nodo", "14d 05h 22m", NICOTINE_ORANGE));
        panel.add(createStatBox("Total Descargado", "1.4 Terabytes", SUCCESS_GREEN));
        panel.add(createStatBox("Promedio Global", "45.8 MB/s", Color.CYAN));
        panel.add(createStatBox("Peticiones Bloqueadas", "142", Color.RED));

        return panel;
    }

    // --- CONTENIDO: COLA DE TRANSFERENCIAS COMPLEJA ---
    private JPanel createComplexTransferPanel(boolean isDownload) {
        JPanel panel = new JPanel(new BorderLayout());
        String[] columns = {"Prioridad", "Estado", "Archivo", "Usuario", "Progreso", "Velocidad", "Chunks", "ETA"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);

        model.addRow(new Object[]{"Alta", "Descargando", "project_omega.iso", "user_alpha", "78%", "12 MB/s", "142/200", "00:01:15"});
        model.addRow(new Object[]{"Normal", "En cola", "asset_pack.zip", "user_beta", "0%", "---", "0/50", "Incierto"});

        JTable table = new JTable(model);
        table.setRowHeight(30);

        // Panel de información de archivo seleccionado (Metadatos)
        JPanel detailPanel = new JPanel(new GridLayout(1, 3));
        detailPanel.setPreferredSize(new Dimension(0, 100));
        detailPanel.setBorder(BorderFactory.createTitledBorder("Metadatos de Transferencia"));
        detailPanel.add(new JLabel("<html><b>Hash SHA-256:</b><br>e3b0c44298fc1c149afbf4c...</html>"));
        detailPanel.add(new JLabel("<html><b>Protocolo:</b> TCP/Socket Direct<br><b>Buffer:</b> 64 KB</html>"));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(detailPanel, BorderLayout.SOUTH);
        return panel;
    }

    // --- COMPONENTES DE SOPORTE ---
    private JPanel createAdvancedHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_DARKER);
        header.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel title = new JLabel("BitBridge :: P2P Master Node");
        title.setFont(new Font("SansSerif", Font.ITALIC, 18));
        title.setForeground(NICOTINE_ORANGE);

        JPanel info = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        info.setOpaque(false);
        info.add(new JLabel("CPU: 12% | RAM: 256MB | CONEXIONES: 1,402"));

        header.add(title, BorderLayout.WEST);
        header.add(info, BorderLayout.EAST);
        return header;
    }

    private JPanel createTechnicalLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea area = new JTextArea();
        area.setBackground(Color.BLACK);
        area.setForeground(SUCCESS_GREEN);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setText("[DEBUG] Iniciando handshake con 142.12.5.4...\n" +
                "[INFO] Chunk #452 recibido correctamente. Hash validado.\n" +
                "[WARN] El usuario 'cris_dev' ha cerrado la conexión inesperadamente.");

        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createTitledBorder(null, " CONSOLA DE EVENTOS DEL SISTEMA ", 0, 0, null, Color.GRAY));
        return panel;
    }

    private JPanel createStatBox(String title, String val, Color c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_DARKER);
        p.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        JLabel t = new JLabel(title, SwingConstants.CENTER);
        JLabel v = new JLabel(val, SwingConstants.CENTER);
        v.setFont(new Font("SansSerif", Font.BOLD, 22));
        v.setForeground(c);
        p.add(t, BorderLayout.NORTH);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    // ... (ChatPanel, StatusBar y setupTheme se mantienen de la versión anterior)

    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        DefaultListModel<String> users = new DefaultListModel<>();
        users.addElement("BitBridge-Lobby (Public)");
        users.addElement("fedora-master (Private)");
        users.addElement("dev-room (Channel)");

        JList<String> list = new JList<>(users);
        list.setBackground(BG_DARKER);

        JPanel msgArea = new JPanel(new BorderLayout());
        JTextArea history = new JTextArea("BIENVENIDO AL SISTEMA DE MENSAJERÍA\n\n[System] Conectado a la sala global.");
        history.setEditable(false);
        JTextField input = new JTextField();

        msgArea.add(new JScrollPane(history), BorderLayout.CENTER);
        msgArea.add(input, BorderLayout.SOUTH);

        split.setLeftComponent(new JScrollPane(list));
        split.setRightComponent(msgArea);
        split.setDividerLocation(200);

        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(5, 10, 5, 10));
        JLabel left = new JLabel("P2P Activo | Verificando Puertos... [UPnP OK]");
        JLabel right = new JLabel("⬇ 140.2 MB/s | ⬆ 12.5 MB/s | PUERTO: 2233  ● ONLINE ");
        right.setForeground(NICOTINE_ORANGE);
        status.add(left, BorderLayout.WEST);
        status.add(right, BorderLayout.EAST);
        return status;
    }

    private void setupTheme() {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception e) {}
        UIManager.put("TabbedPane.selectedBackground", NICOTINE_ORANGE.darker());
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("Table.alternateRowColor", new Color(40, 40, 40));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BitBridgeNicotineUIV2().setVisible(true));
    }
}

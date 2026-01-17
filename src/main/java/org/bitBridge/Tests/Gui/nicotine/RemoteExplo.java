package org.bitBridge.Tests.Gui.nicotine;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RemoteExplo extends JFrame {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARKER = new Color(20, 20, 20);
    private static final Color ACCENT_GREEN = new Color(40, 167, 69);

    private DefaultTableModel fileModel;
    private DefaultTableModel queueModel;
    private JTable fileTable;
    private JTable queueTable;
    private JLabel lblPath;

    public RemoteExplo() {
        setupTheme();
        setTitle("BitBridge Pro - Terminal de Gestión de Activos Remotos");
        setSize(1500, 950);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. Barra superior de conexión
        add(createGlobalToolBar(), BorderLayout.NORTH);

        // 2. Contenedor Principal (Tabs)
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("🌐 Explorador de Nodos", createRemoteExplorerPanel());
        mainTabs.addTab("📊 Estadísticas de Red", createTrafficMonitorPanel());

        add(mainTabs, BorderLayout.CENTER);

        // 3. Barra de estado inferior
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createRemoteExplorerPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // --- SUB-PANEL SUPERIOR: Dirección y Navegación ---
        JPanel navPanel = new JPanel(new BorderLayout());
        navPanel.setBackground(BG_DARKER);
        navPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel addressBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addressBar.setOpaque(false);
        addressBar.add(new JLabel("📍 Nodo:"));
        addressBar.add(new JComboBox<>(new String[]{"STATION-ALPHA-01", "DATACENTER-CORE", "REMOTE-LAPTOP"}));
        addressBar.add(Box.createHorizontalStrut(20));
        addressBar.add(new JLabel("Ruta:"));
        lblPath = new JLabel("/home/admin/storage/secure_vault/");
        lblPath.setForeground(NICOTINE_ORANGE);
        addressBar.add(lblPath);

        navPanel.add(addressBar, BorderLayout.WEST);
        navPanel.add(new JTextField("🔍 Buscar en directorio...", 20), BorderLayout.EAST);

        // --- DIVISOR PRINCIPAL (Vertical: Explorador arriba, Cola abajo) ---
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setDividerLocation(600);
        verticalSplit.setResizeWeight(0.7);

        // --- PARTE SUPERIOR: Árbol + Tabla + Info ---
        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        horizontalSplit.setDividerLocation(250);

        // A. Árbol
        JTree tree = createStyledTree();
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(new TitledBorder("Estructura de Red"));

        // B. Tabla de Archivos
        JPanel centerPanel = createFileTablePanel();

        horizontalSplit.setLeftComponent(treeScroll);
        horizontalSplit.setRightComponent(centerPanel);

        // --- PARTE INFERIOR: Cola de Transferencia ---
        JPanel queuePanel = createTransferQueuePanel();

        verticalSplit.setTopComponent(horizontalSplit);
        verticalSplit.setBottomComponent(queuePanel);

        mainPanel.add(navPanel, BorderLayout.NORTH);
        mainPanel.add(verticalSplit, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createFileTablePanel() {
        JPanel p = new JPanel(new BorderLayout());
        String[] columns = {"Nombre", "Tamaño", "Tipo", "Modificado", "SHA-1 Hash"};
        fileModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        fileTable = new JTable(fileModel);
        fileTable.setRowHeight(32);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setShowGrid(false);
        fileTable.setIntercellSpacing(new Dimension(0, 0));

        // Renderizado personalizado de filas
        fileTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                setBorder(new EmptyBorder(0, 10, 0, 0));
                if (!s) comp.setBackground(r % 2 == 0 ? new Color(30,30,30) : new Color(35,35,35));
                return comp;
            }
        });

        populateMockFiles();

        // Menú contextual
        JPopupMenu menu = new JPopupMenu();
        JMenuItem itemDl = new JMenuItem("📥 Descargar (Pull)", new javax.swing.ImageIcon());
        itemDl.addActionListener(e -> addToQueue(fileTable.getSelectedRow(), "DOWNLOAD"));
        menu.add(itemDl);
        menu.add(new JMenuItem("📤 Subir aquí (Push)"));
        menu.addSeparator();
        menu.add(new JMenuItem("🔗 Copiar enlace Magnet"));
        menu.add(new JMenuItem("❌ Eliminar del Nodo"));

        fileTable.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        p.add(new JScrollPane(fileTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel createTransferQueuePanel() {
        JPanel p = new JPanel(new BorderLayout());
        String[] columns = {"Archivo", "Operación", "Progreso", "Velocidad", "Estado"};
        queueModel = new DefaultTableModel(columns, 0);
        queueTable = new JTable(queueModel);
        queueTable.setRowHeight(30);

        // Renderizador de Barra de Progreso
        queueTable.getColumnModel().getColumn(2).setCellRenderer(new TableProgressRenderer());

        JScrollPane scroll = new JScrollPane(queueTable);
        scroll.setBorder(new TitledBorder("Cola de Transferencias Activa (Multi-thread)"));

        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JTree createStyledTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Infraestructura BitBridge");
        DefaultMutableTreeNode node1 = new DefaultMutableTreeNode("Producción-Srv");
        node1.add(new DefaultMutableTreeNode("Logs"));
        node1.add(new DefaultMutableTreeNode("Backups_SQL"));
        root.add(node1);
        root.add(new DefaultMutableTreeNode("Nodos-Peers (12)"));
        return new JTree(root);
    }

    private void populateMockFiles() {
        fileModel.addRow(new Object[]{"📦 core_dump_v2.zip", "450 MB", "Archivo", "Hace 2 min", "FD22...90"});
        fileModel.addRow(new Object[]{"📁 Documentos_Legales", "--", "Carpeta", "12/01/2026", "DIR"});
        fileModel.addRow(new Object[]{"📄 config.json", "2 KB", "JSON", "Ayer", "AA12...11"});
        fileModel.addRow(new Object[]{"🎬 training_video.mp4", "1.2 GB", "Video", "01/01/2026", "CC45...00"});
    }

    private void addToQueue(int row, String type) {
        if(row == -1) return;
        String name = fileTable.getValueAt(row, 0).toString();
        queueModel.addRow(new Object[]{name, type, 0, "Calcular...", "Iniciando..."});

        // Simulación de progreso
        int lastRow = queueModel.getRowCount() - 1;
        new Thread(() -> {
            try {
                for(int i=0; i<=100; i+=10) {
                    Thread.sleep(300);
                    queueModel.setValueAt(i, lastRow, 2);
                    queueModel.setValueAt((10 + i/2) + " MB/s", lastRow, 3);
                    queueModel.setValueAt(i < 100 ? "Transfiriendo..." : "Completado", lastRow, 4);
                }
            } catch (Exception e) {}
        }).start();
    }

    private JPanel createGlobalToolBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setBackground(BG_DARKER);
        JButton btnConnect = new JButton("⚡ Conexión Directa");
        btnConnect.setBackground(NICOTINE_ORANGE);
        btnConnect.setForeground(Color.BLACK);
        bar.add(btnConnect);
        bar.add(new JButton("📡 Escanear Red"));
        bar.add(new JSeparator(SwingConstants.VERTICAL));
        bar.add(new JLabel("Latencia: 24ms"));
        return bar;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(BG_DARKER);
        status.setBorder(new EmptyBorder(5, 12, 5, 12));
        JLabel left = new JLabel("Listo | Cifrado AES-256 Activo");
        JLabel right = new JLabel("▼ 1.2 MB/s ▲ 0.8 MB/s | Peers: 5");
        right.setForeground(ACCENT_GREEN);
        status.add(left, BorderLayout.WEST);
        status.add(right, BorderLayout.EAST);
        return status;
    }

    private JPanel createTrafficMonitorPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.add(new JLabel("Visualización de Tráfico en Tiempo Real (Placeholder)"));
        return p;
    }

    private void setupTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("ProgressBar.foreground", NICOTINE_ORANGE);
            UIManager.put("ProgressBar.selectionForeground", Color.BLACK);
            UIManager.put("ProgressBar.selectionBackground", Color.BLACK);
            UIManager.put("Table.selectionBackground", new Color(60, 60, 60));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Renderizador interno para la barra de progreso de la tabla
    class TableProgressRenderer extends JProgressBar implements javax.swing.table.TableCellRenderer {
        public TableProgressRenderer() {
            super(0, 100);
            setStringPainted(true);
            setBorderPainted(false);
        }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            setValue((Integer) v);
            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RemoteExplo().setVisible(true));
    }

}

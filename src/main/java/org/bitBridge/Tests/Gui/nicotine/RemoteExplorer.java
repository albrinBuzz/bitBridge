package org.bitBridge.Tests.Gui.nicotine;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;

public class RemoteExplorer extends JFrame {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARKER = new Color(20, 20, 20);
    private static final Color ACCENT_GREEN = new Color(50, 200, 50);

    private DefaultTableModel fileModel;
    private JTable fileTable;

    public RemoteExplorer() {
        //setupTheme();
        FlatOneDarkIJTheme.setup();

        setTitle("BitBridge Pro - Advanced Remote Assets Explorer");
        setSize(1500, 950);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. TOOLBAR SUPERIOR
        add(createGlobalToolBar(), BorderLayout.NORTH);

        // 2. PANEL CENTRAL (Split lateral)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(250);
        mainSplit.setLeftComponent(createSidePanel());
        mainSplit.setRightComponent(createMainExplorationTabs());

        add(mainSplit, BorderLayout.CENTER);

        // 3. BARRA DE ESTADO
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private void setupTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private JPanel createGlobalToolBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setBackground(BG_DARKER);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, Color.DARK_GRAY));

        bar.add(new JButton("🔌 Conectar a Nodo"));
        bar.add(new JButton("📁 Compartir Local"));
        bar.add(new JSeparator(SwingConstants.VERTICAL));

        JLabel sysStats = new JLabel(" CPU: 12% | RAM: 1.2GB/64GB | Latencia: 15ms ");
        sysStats.setForeground(Color.GRAY);
        bar.add(sysStats);

        return bar;
    }

    private JPanel createSidePanel() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(BG_DARKER);

        // Marcadores
        DefaultListModel<String> favModel = new DefaultListModel<>();
        favModel.addElement("⭐ Servidor Principal i7");
        favModel.addElement("⭐ Backup Gigabyte B760");
        favModel.addElement("📁 /home/user/logs");
        favModel.addElement("📁 /var/www/assets");

        JList<String> favList = new JList<>(favModel);
        favList.setBackground(BG_DARKER);
        favList.setFixedCellHeight(35);
        favList.setBorder(new TitledBorder(new LineBorder(Color.DARK_GRAY), "Favoritos"));

        // Árbol
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Infraestructura");
        DefaultMutableTreeNode node1 = new DefaultMutableTreeNode("NODO-REMOTO-01");
        node1.add(new DefaultMutableTreeNode("BitBridge-Shared"));
        node1.add(new DefaultMutableTreeNode("System-Backups"));
        root.add(node1);

        JTree tree = new JTree(root);
        tree.setBackground(BG_DARKER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(favList), new JScrollPane(tree));
        split.setDividerLocation(200);

        side.add(split, BorderLayout.CENTER);
        return side;
    }

    private JTabbedPane createMainExplorationTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("🌐 Explorador Remoto", createRemoteExplorerPanel());
        tabs.addTab("📥 Cola de Transferencias", createQueuePanel());
        return tabs;
    }

    private JPanel createRemoteExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARKER);

        // --- BARRA DE NAVEGACIÓN (FILE MANAGER STYLE) ---
        JPanel navContainer = new JPanel(new BorderLayout());
        navContainer.setOpaque(false);

        JToolBar actions = new JToolBar();
        actions.setFloatable(false);
        actions.setBackground(BG_DARKER);

        actions.add(createNavButton("➕ Nueva Carpeta", "Crear"));
        actions.add(createNavButton("📤 Subir (Push)", "Upload"));
        actions.addSeparator();
        actions.add(createNavButton("✂️ Cortar", null));
        actions.add(createNavButton("📋 Pegar", null));
        actions.add(createNavButton("🗑️ Eliminar", null));
        actions.add(Box.createHorizontalGlue());

        JTextField search = new JTextField(15);
        search.putClientProperty("JTextField.placeholderText", "🔍 Filtrar archivos...");
        actions.add(new JLabel("Filtro: "));
        actions.add(search);

        // Breadcrumbs
        JPanel breadcrumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        breadcrumbs.setBackground(new Color(30, 30, 30));
        String[] bPath = {"Root", "remote", "gigabyte_server", "backups"};
        for(String p : bPath) {
            JButton b = new JButton(p + " >");
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            breadcrumbs.add(b);
        }

        navContainer.add(actions, BorderLayout.NORTH);
        navContainer.add(breadcrumbs, BorderLayout.SOUTH);

        // --- TABLA DE ARCHIVOS ---
        String[] columns = {"Nombre", "Tamaño", "Tipo", "Modificado", "Estado"};
        fileModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        fileTable = new JTable(fileModel);
        setupFileTable();
        populateAdvancedMockData();

        // --- SPLIT CENTRAL ---
        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(fileTable), createAdvancedInspector());
        contentSplit.setDividerLocation(950);
        contentSplit.setResizeWeight(0.8);

        panel.add(navContainer, BorderLayout.NORTH);
        panel.add(contentSplit, BorderLayout.CENTER);

        return panel;
    }

    private void setupFileTable() {
        fileTable.setRowHeight(35);
        fileTable.setShowGrid(false);
        fileTable.setIntercellSpacing(new Dimension(0, 0));
        fileTable.setSelectionBackground(new Color(60, 60, 60));

        // 1. RENDERER DE ESTADO (Ya lo tienes, mantenlo igual)
        fileTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, f, r, c);
                String val = String.valueOf(v);
                if ("Ready".equals(val)) l.setForeground(ACCENT_GREEN);
                else if ("Busy".equals(val)) l.setForeground(Color.CYAN);
                else if ("Error".equals(val)) l.setForeground(Color.RED);
                return l;
            }
        });

        // 2. CREACIÓN DEL MENÚ CONTEXTUAL DE ARCHIVOS
        JPopupMenu fileMenu = new JPopupMenu();

        // --- SECCIÓN: ACCIONES DE TRANSFERENCIA ---
        JMenuItem itemPull = new JMenuItem("📥 Descargar (PULL)");
        itemPull.setFont(new Font("SansSerif", Font.BOLD, 12));

        JMenu menuSmartPull = new JMenu("⚡ Descarga Inteligente");
        menuSmartPull.add(new JMenuItem("Sincronización Delta (Solo cambios)"));
        menuSmartPull.add(new JMenuItem("Descarga Comprimida (LZ4)"));
        menuSmartPull.add(new JMenuItem("Prioridad Crítica (Max Bandwidth)"));

        // --- SECCIÓN: OPERACIONES DE ARCHIVO ---
        JMenuItem itemRename = new JMenuItem("✏️ Renombrar");
        JMenuItem itemDelete = new JMenuItem("🗑️ Eliminar");
        itemDelete.setForeground(new Color(255, 80, 80));

        // --- SECCIÓN: INTEGRIDAD Y DATA ---
        JMenuItem itemHash = new JMenuItem("🔍 Verificar SHA-1 Remoto");
        JMenuItem itemCopyPath = new JMenuItem("📋 Copiar Ruta Absoluta");

        fileMenu.add(itemPull);
        fileMenu.add(menuSmartPull);
        fileMenu.addSeparator();
        fileMenu.add(itemRename);
        fileMenu.add(itemDelete);
        fileMenu.addSeparator();
        fileMenu.add(itemHash);
        fileMenu.add(itemCopyPath);

        // 3. LISTENERS (CLIC DERECHO Y DOBLE CLIC)
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handlePopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handlePopup(e); }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = fileTable.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        fileTable.setRowSelectionInterval(row, row);
                        fileMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = fileTable.getSelectedRow();
                    String name = fileTable.getValueAt(row, 0).toString();
                    if (name.startsWith("📁")) {
                        System.out.println("Navegando a: " + name);
                        // navigateTo(currentPath + "/" + name.substring(2));
                    }
                }
            }
        });
    }

    private JPanel createAdvancedInspector() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        p.setBackground(new Color(25, 25, 25));
        p.setMinimumSize(new Dimension(320, 0));

        // --- SECCIÓN: ENCABEZADO Y PREVIEW ---
        JLabel icon = new JLabel("🗄️");
        icon.setFont(new Font("Serif", Font.PLAIN, 80));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel name = new JLabel("global_assets_2026.tar.gz");
        name.setFont(new Font("SansSerif", Font.BOLD, 15));
        name.setForeground(NICOTINE_ORANGE);
        name.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(icon);
        p.add(Box.createVerticalStrut(10));
        p.add(name);
        p.add(Box.createVerticalStrut(25));

        // --- SECCIÓN: METADATOS TÉCNICOS ---
        JLabel titleMeta = new JLabel("METADATOS DEL ASSET");
        titleMeta.setFont(new Font("SansSerif", Font.BOLD, 10));
        titleMeta.setForeground(Color.DARK_GRAY);
        p.add(titleMeta);
        p.add(Box.createVerticalStrut(10));
        p.add(new JSeparator());
        p.add(Box.createVerticalStrut(15));

        // Propiedades mejoradas
        p.add(createPropLabel("📂 Tipo", "Gzip Archive"));
        p.add(createPropLabel("⚖️ Tamaño", "8.52 GB"));
        p.add(createPropLabel("👤 Dueño", "admin (uid: 1000)"));
        p.add(createPropLabel("🔑 SHA-1", "8F2B9901AC99..."));

        p.add(Box.createVerticalStrut(20));

        // --- SECCIÓN: ACCIONES RÁPIDAS ---
        JButton btnCopyHash = new JButton("📋 Copiar Hash");
        btnCopyHash.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnCopyHash.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(btnCopyHash);

        p.add(Box.createVerticalGlue());

        // Botón de acción principal
        JButton btnPull = new JButton("📥 INICIAR DOWNLOAD PULL");
        btnPull.setBackground(NICOTINE_ORANGE);
        btnPull.setForeground(Color.BLACK);
        btnPull.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnPull.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        btnPull.setCursor(new Cursor(Cursor.HAND_CURSOR));

        p.add(btnPull);
        return p;
    }

    // Helper mejorado con mejor espaciado y fuentes
    private JPanel createPropLabel(String key, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(300, 30)); // Altura fija para consistencia

        JLabel kl = new JLabel(key);
        kl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        kl.setForeground(new Color(150, 150, 150)); // Gris medio

        JLabel vl = new JLabel(value);
        vl.setFont(new Font("Monospaced", Font.BOLD, 12)); // Monospaced para datos técnicos
        vl.setForeground(Color.WHITE);

        row.add(kl, BorderLayout.WEST);
        row.add(vl, BorderLayout.EAST);

        // Añadimos un pequeño margen inferior
        row.setBorder(new EmptyBorder(0, 0, 5, 0));
        return row;
    }

    private void populateAdvancedMockData() {
        fileModel.setRowCount(0);
        fileModel.addRow(new Object[]{"📁 bin", "--", "Sistema", "Hace 2 días", "Ready"});
        fileModel.addRow(new Object[]{"📁 logs_produccion", "--", "Logs", "Hace 10 min", "Ready"});
        fileModel.addRow(new Object[]{"📁 renders_4k", "--", "Multimedia", "2026-01-25", "Ready"});
        fileModel.addRow(new Object[]{"⚙️ kernel_patch.sh", "45 KB", "Script Bash", "Hace 1h", "Ready"});
        fileModel.addRow(new Object[]{"🎦 teaser_final.mp4", "1.2 GB", "Video", "2026-01-20", "Ready"});
        fileModel.addRow(new Object[]{"🗄️ backup_db.tar.gz", "8.5 GB", "Archivo", "Hace 5h", "Busy"});
        fileModel.addRow(new Object[]{"📄 nodes.json", "128 KB", "JSON", "Justo ahora", "Ready"});
        fileModel.addRow(new Object[]{"⚠️ temp_dump.tmp", "0 KB", "Temp", "Error", "Error"});
    }

    private JPanel createQueuePanel() {
        JPanel p = new JPanel(new BorderLayout());
        String[] cols = {"Archivo", "Progreso", "Velocidad", "Estado"};
        DefaultTableModel m = new DefaultTableModel(cols, 0);
        m.addRow(new Object[]{"movie.mkv", "45%", "120 MB/s", "Descargando..."});
        m.addRow(new Object[]{"backup.zip", "100%", "0 MB/s", "Completado"});

        JTable t = new JTable(m);
        t.setRowHeight(30);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private JButton createNavButton(String text, String tt) {
        JButton b = new JButton(text);
        b.setToolTipText(tt);
        return b;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(BG_DARKER);
        status.setBorder(new EmptyBorder(5, 10, 5, 10));
        JLabel left = new JLabel("BitBridge Engine v2.6.0-PRO | Sockets: 156 OK");
        JLabel right = new JLabel(" DL: 850 Mbps | UL: 120 Mbps ");
        right.setForeground(NICOTINE_ORANGE);
        status.add(left, BorderLayout.WEST);
        status.add(right, BorderLayout.EAST);
        return status;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RemoteExplorer().setVisible(true));
    }
}
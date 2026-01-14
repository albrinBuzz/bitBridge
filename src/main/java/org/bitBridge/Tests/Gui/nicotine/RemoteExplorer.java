package org.bitBridge.Tests.Gui.nicotine;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RemoteExplorer extends JFrame {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARKER = new Color(25, 25, 25);
    private DefaultTableModel fileModel;
    private JLabel lblPath;

    public RemoteExplorer() {
        setupTheme();
        setTitle("BitBridge Pro - Advanced Remote Assets Explorer");
        setSize(1450, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. TOOLBAR GLOBAL
        add(createGlobalToolBar(), BorderLayout.NORTH);

        // 2. TABS PRINCIPALES
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("🌐 Explorador Remoto", createRemoteExplorerPanel());
        mainTabs.addTab("📈 Monitor de Tráfico", createTrafficMonitorPanel());
        mainTabs.addTab("⚙️ Configuración Nodo", createSettingsPanel());

        add(mainTabs, BorderLayout.CENTER);

        // 3. BARRA DE ESTADO
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createRemoteExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // --- ENCABEZADO: Selector de Nodo y Barra de Direcciones ---
        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        headerPanel.setBackground(BG_DARKER);

        // Fila 1: Selector
        JPanel userSelector = new JPanel(new FlowLayout(FlowLayout.LEFT));
        userSelector.setOpaque(false);
        userSelector.add(new JLabel("Nodo Destino:"));
        JComboBox<String> nodeCombo = new JComboBox<>(new String[]{
                "WORKSTATION-01 (192.168.1.50)",
                "SERVER-BACKUP (192.168.1.10)",
                "DEV-STATION (192.168.1.60)"
        });
        userSelector.add(nodeCombo);
        userSelector.add(new JButton("🔄 Refrescar"));
        userSelector.add(new JSeparator(SwingConstants.VERTICAL));
        userSelector.add(new JLabel("Buscador:"));
        JTextField txtFilter = new JTextField(15);
        userSelector.add(txtFilter);

        // Fila 2: Breadcrumbs / Path
        JPanel pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pathPanel.setOpaque(false);
        pathPanel.add(new JLabel("📁 Directorio Actual:"));
        lblPath = new JLabel("root/home/user/compartido/Backups");
        lblPath.setForeground(NICOTINE_ORANGE);
        lblPath.setFont(new Font("Monospaced", Font.BOLD, 12));
        pathPanel.add(lblPath);

        headerPanel.add(userSelector);
        headerPanel.add(pathPanel);

        // --- CUERPO: SplitPane Triple ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(280);

        // A. Árbol de Directorios (Lado Izquierdo)
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("NODO-REMOTO");
        DefaultMutableTreeNode home = new DefaultMutableTreeNode("C:/BitBridgeShared");
        home.add(new DefaultMutableTreeNode("Multimedia"));
        home.add(new DefaultMutableTreeNode("Documentos_Corp"));
        home.add(new DefaultMutableTreeNode("System_Images"));
        root.add(home);

        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setBackground(new Color(30, 30, 30));
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(new TitledBorder("Estructura de Carpetas"));

        // B. Tabla de Archivos (Centro)
        String[] columns = {"Nombre", "Tamaño", "Modificado", "Estado", "Checksum SHA-1"};
        fileModel = new DefaultTableModel(columns, 0);
        populateMockFiles(); // Llenar con datos

        JTable table = new JTable(fileModel);
        table.setRowHeight(35); // Aumentado para mejor visualización
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        // --- LÓGICA DE CLIC DERECHO (MENÚ CONTEXTUAL) ---
        JPopupMenu contextMenu = new JPopupMenu();

        JMenuItem downloadItem = new JMenuItem("📥 Descargar Archivo (PULL)");
        downloadItem.setFont(new Font("SansSerif", Font.BOLD, 12));

        JMenuItem priorityDownload = new JMenuItem("⚡ Descarga Prioritaria (High Speed)");
        JMenuItem verifyItem = new JMenuItem("🔍 Verificar Integridad SHA-1");
        JMenuItem copyPathItem = new JMenuItem("📋 Copiar Ruta Remota");

        contextMenu.add(downloadItem);
        contextMenu.add(priorityDownload);
        contextMenu.addSeparator();
        contextMenu.add(verifyItem);
        contextMenu.add(copyPathItem);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showMenu(e);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showMenu(e);
            }
            private void showMenu(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < table.getRowCount()) {
                    table.setRowSelectionInterval(row, row); // Selecciona la fila automáticamente
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        // Acciones del Menú
        downloadItem.addActionListener(e -> {
            String file = table.getValueAt(table.getSelectedRow(), 0).toString();
            JOptionPane.showMessageDialog(this, "Iniciando descarga estándar de: " + file);
        });

        priorityDownload.addActionListener(e -> {
            String file = table.getValueAt(table.getSelectedRow(), 0).toString();
            JOptionPane.showMessageDialog(this, "⚡ Prioridad alta asignada a: " + file);
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(new TitledBorder("Contenido del Directorio"));

        // C. Panel de Detalles (Derecha)
        JPanel detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setPreferredSize(new Dimension(300, 0));
        detailPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel lblPreview = new JLabel("VISTA PREVIA");
        lblPreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblPreview.setForeground(NICOTINE_ORANGE);

        JPanel fileIconBox = new JPanel();
        fileIconBox.setPreferredSize(new Dimension(150, 150));
        fileIconBox.setBackground(new Color(45, 45, 45));
        fileIconBox.add(new JLabel("📄"));

        JTextPane txtMeta = new JTextPane();
        txtMeta.setContentType("text/html");
        txtMeta.setEditable(false);
        txtMeta.setOpaque(false);
        txtMeta.setText("<html><body style='color:gray; font-family:sans-serif;'>" +
                "<b>Archivo:</b> data_backup.tar.gz<br>" +
                "<b>Tamaño:</b> 1.4 GB<br>" +
                "<b>Propietario:</b> System_Admin<br>" +
                "<b>Permisos:</b> Read-Only<br><br>" +
                "<font color='#FFA500'>ID Único: BB-9982-X</font></body></html>");

        JButton btnPull = new JButton("📥 Iniciar Transferencia PULL");
        btnPull.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnPull.setBackground(NICOTINE_ORANGE.darker());
        btnPull.setForeground(Color.WHITE);

        detailPanel.add(lblPreview);
        detailPanel.add(Box.createVerticalStrut(10));
        detailPanel.add(fileIconBox);
        detailPanel.add(Box.createVerticalStrut(20));
        detailPanel.add(txtMeta);
        detailPanel.add(Box.createVerticalGlue());
        detailPanel.add(btnPull);

        // Integración de Splits
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailPanel);
        rightSplit.setDividerLocation(650);

        mainSplit.setLeftComponent(treeScroll);
        mainSplit.setRightComponent(rightSplit);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(mainSplit, BorderLayout.CENTER);

        return panel;
    }

    private void populateMockFiles() {
        fileModel.addRow(new Object[]{"kernel_update_v4.bin", "150 MB", "2026-01-10", "Ready", "8F2B...11"});
        fileModel.addRow(new Object[]{"user_manual.pdf", "12 MB", "2025-12-15", "Ready", "A1B2...99"});
        fileModel.addRow(new Object[]{"marketing_assets/", "--", "2026-01-01", "Folder", "DIR"});
        fileModel.addRow(new Object[]{"database_dump.sql", "2.8 GB", "Hace 2h", "Busy", "E4E4...00"});
    }

    private JPanel createTrafficMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        JPanel graph = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15, 15, 15));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.setColor(NICOTINE_ORANGE);
                int[] points = {180, 160, 170, 100, 120, 80, 110};
                for(int i=0; i<points.length-1; i++) {
                    g2.drawLine(i*150, points[i], (i+1)*150, points[i+1]);
                }
            }
        };
        graph.setBorder(new TitledBorder("Throughput de Red Interna (Gbps)"));
        panel.add(graph, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createGlobalToolBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setBackground(BG_DARKER);
        bar.setPreferredSize(new Dimension(0, 40));
        JButton btnReconnect = new JButton("🔌 Forzar Reconexión");
        btnReconnect.setFont(new Font("SansSerif", Font.BOLD, 11));
        bar.add(btnReconnect);
        bar.add(new JButton("📁 Publicar Directorio"));
        bar.add(new JSeparator(SwingConstants.VERTICAL));
        JLabel lblStatus = new JLabel("MODO: ADMINISTRADOR CENTRAL");
        lblStatus.setForeground(Color.GRAY);
        bar.add(lblStatus);
        return bar;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(BG_DARKER);
        status.setBorder(new EmptyBorder(5, 10, 5, 10));
        JLabel stats = new JLabel("DL: 850 Mbps | UL: 120 Mbps | Nodes: 14 | Sockets OK");
        stats.setForeground(NICOTINE_ORANGE);
        status.add(new JLabel("BitBridge Engine v2.6.0-PRO"), BorderLayout.WEST);
        status.add(stats, BorderLayout.EAST);
        return status;
    }

    private JPanel createSettingsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.add(new JLabel("Configuración de Cifrado AES-256 y Puertos de Escucha..."));
        return p;
    }

    private void setupTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("TabbedPane.selectedBackground", NICOTINE_ORANGE);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("SplitPane.dividerSize", 10);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RemoteExplorer().setVisible(true));
    }
}
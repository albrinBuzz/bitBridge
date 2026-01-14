package org.bitBridge.Tests.Gui;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class SQLDeveloperUltra extends JFrame {

    // Paleta de colores Oracle Modern
    private final Color HEADER_BLUE = new Color(43, 87, 154);
    private final Color BG_LIGHT = new Color(245, 245, 245);
    private final Color EDITOR_BG = Color.WHITE;
    private final Color BORDER_COLOR = new Color(200, 200, 200);

    public SQLDeveloperUltra() {
        setTitle("Oracle SQL Developer - IDE Pro");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 850);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- 1. TOOLBAR SUPERIOR ESTILIZADA ---
        add(createEnhancedToolBar(), BorderLayout.NORTH);

        // --- 2. PANEL IZQUIERDO (CONEXIONES) ---
        JPanel leftPanel = createSidebar("Conexiones", new String[]{"Tablas", "Vistas", "Índices", "Paquetes"});

        // --- 3. PANEL DERECHO (SNIPPETS / AYUDA) ---
        JPanel rightPanel = createSidebar("Snippets", new String[]{"Date Funcs", "Aggregate", "Joins"});
        rightPanel.setPreferredSize(new Dimension(180, 0));

        // --- 4. ÁREA CENTRAL (EL "WORKBOOK") ---
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("Hoja de Trabajo SQL: LocalDB", createEditorFullPanel());

        // --- 5. ENSAMBLAJE CON SPLITS ---
        JSplitPane splitRight = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainTabs, rightPanel);
        splitRight.setDividerLocation(850);
        splitRight.setResizeWeight(1.0);
        splitRight.setBorder(null);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, splitRight);
        mainSplit.setDividerLocation(250);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);

        // --- 6. BARRA DE ESTADO PROFESIONAL ---
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createEditorFullPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Barra de herramientas interna del editor (Ejecución)
        JToolBar execBar = new JToolBar();
        execBar.setFloatable(false);
        execBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        String[] btns = {"▶ Ejecutar", "▶⚡ Script", "💾", "❌", "✅ Commit", "🔄 Rollback"};
        for (String b : btns) {
            JButton btn = new JButton(b);
            btn.setFocusable(false);
            btn.setMargin(new Insets(2, 8, 2, 8));
            execBar.add(btn);
        }

        // Editor con números de línea (Simulado)
        JTextArea editor = new JTextArea();
        editor.setFont(new Font("Consolas", Font.PLAIN, 14));
        editor.setText("-- Consulta de ejemplo\nSELECT u.id, u.nombre, p.perfil \nFROM usuarios u \nJOIN perfiles p ON u.id_perfil = p.id \nWHERE u.estado = 'ACTIVO';");
        editor.setMargin(new Insets(10, 10, 10, 10));

        // Panel de Resultados inferior
        JTabbedPane resultsTab = new JTabbedPane(JTabbedPane.BOTTOM);
        resultsTab.addTab("Salida de Script", createTablePlaceholder());
        resultsTab.addTab("Resultados de Consulta", new JScrollPane(new JTable(20, 10)));

        JSplitPane editorSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(editor), resultsTab);
        editorSplit.setDividerLocation(350);

        panel.add(execBar, BorderLayout.NORTH);
        panel.add(editorSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSidebar(String title, String[] items) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JLabel header = new JLabel(" " + title);
        header.setOpaque(true);
        header.setBackground(HEADER_BLUE);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 28));
        header.setFont(new Font("SansSerif", Font.BOLD, 12));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(title.equals("Conexiones") ? "Oracle Local" : "Categorías");
        for (String item : items) root.add(new DefaultMutableTreeNode(item));

        JTree tree = new JTree(root);
        tree.setBackground(BG_LIGHT);

        p.add(header, BorderLayout.NORTH);
        p.add(new JScrollPane(tree), BorderLayout.CENTER);
        return p;
    }

    private JToolBar createEnhancedToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        String[] icons = {"➕ Nueva Conexión", "📂 Abrir", "💾 Guardar Todo", "|", "⚙ Preferencias"};
        for (String s : icons) {
            if (s.equals("|")) tb.addSeparator();
            else tb.add(new JButton(s));
        }
        return tb;
    }

    private JPanel createStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setPreferredSize(new Dimension(0, 25));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));

        JLabel left = new JLabel("  Conectado: Oracle Database 21c Express Edition");
        JLabel right = new JLabel("Línea: 4, Col: 12  |  UTF-8    ");

        left.setFont(new Font("SansSerif", Font.PLAIN, 11));
        right.setFont(new Font("SansSerif", Font.PLAIN, 11));

        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JScrollPane createTablePlaceholder() {
        JTextArea log = new JTextArea("SQL> SELECT * FROM dual;\n1 row selected.\nSQL>");
        log.setBackground(new Color(240, 240, 240));
        log.setEditable(false);
        return new JScrollPane(log);
    }

    public static void main(String[] args) {
        // Intentar apariencia GTK/Windows o FlatLaf si estuviera disponible
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new SQLDeveloperUltra().setVisible(true));
    }
}
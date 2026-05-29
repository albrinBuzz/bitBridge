package org.bitBridge.Tests.Gui;

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class SQLDeveloperUltra extends JFrame {

    // Paleta adaptada a One Dark
    private final Color ACCENT_BLUE = new Color(82, 139, 255);
    private final Color SUCCESS_COLOR = new Color(152, 195, 121); // Verde One Dark
    private final Color WARNING_COLOR = new Color(229, 192, 123); // Ámbar One Dark

    public SQLDeveloperUltra() {
        setTitle("Oracle SQL Developer 23c - Professional Edition (One Dark)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 900);
        setLocationRelativeTo(null);

        // Layout Principal
        setLayout(new BorderLayout());

        // 1. TOOLBAR SUPERIOR (Estilo Flat)
        add(createMainToolBar(), BorderLayout.NORTH);

        // 2. CONTENIDO CENTRAL
        JPanel leftPanel = createSidebar("Conexiones", new String[]{"Tablas", "Vistas", "Procedimientos", "Funciones"});

        JTabbedPane mainWorkArea = new JTabbedPane();
        // Configuramos el estilo de las pestañas para que sea más moderno
        mainWorkArea.putClientProperty("JTabbedPane.showTabSeparators", true);
        mainWorkArea.addTab("Hoja de Trabajo: HR_CONNECTION", createEditorStack());

        JPanel rightPanel = createSidebar("Snippets", new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"});
        rightPanel.setPreferredSize(new Dimension(200, 0));

        // Splits con bordes invisibles (FlatLaf los maneja mejor)
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainWorkArea, rightPanel);
        rightSplit.setResizeWeight(0.8);
        rightSplit.setDividerSize(5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setResizeWeight(0.15);
        mainSplit.setDividerSize(5);

        add(mainSplit, BorderLayout.CENTER);

        // 3. BARRA DE ESTADO
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createEditorStack() {
        JPanel container = new JPanel(new BorderLayout());

        // Barra de acciones del editor (Transparente para heredar el color del tema)
        JToolBar editorBar = new JToolBar();
        editorBar.setFloatable(false);
        editorBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));

        editorBar.add(createIconButton("▶", "Ejecutar (Ctrl+Enter)", SUCCESS_COLOR));
        editorBar.add(createIconButton("▶⚡", "Ejecutar Script (F5)", ACCENT_BLUE));
        editorBar.addSeparator();
        editorBar.add(createIconButton("💾", "Guardar", Color.LIGHT_GRAY));
        editorBar.add(createIconButton("🔄", "Limpiar Hoja", WARNING_COLOR));

        // ÁREA DEL EDITOR
        JPanel editorPanel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 14)); // Tipografía recomendada
        textArea.setText("-- HR Schema Query\nSELECT \n    e.first_name, \n    d.department_name \nFROM employees e\nJOIN departments d ON e.department_id = d.department_id\nWHERE salary > 5000;");
        textArea.setMargin(new Insets(10, 10, 10, 10));
        textArea.setCaretColor(ACCENT_BLUE);

        // Números de línea adaptados al tema oscuro
        JTextArea lineNumbers = new JTextArea(" 1 \n 2 \n 3 \n 4 \n 5 \n 6 \n 7 \n 8 ");
        lineNumbers.setBackground(UIManager.getColor("EditorPane.inactiveBackground"));
        lineNumbers.setForeground(new Color(92, 99, 112)); // Gris comentario
        lineNumbers.setEditable(false);
        lineNumbers.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        lineNumbers.setBorder(new EmptyBorder(10, 8, 10, 8));

        editorPanel.add(lineNumbers, BorderLayout.WEST);
        editorPanel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        // Panel de Resultados inferior
        JTabbedPane resultsPane = new JTabbedPane();
        resultsPane.putClientProperty("JTabbedPane.tabType", "card");
        resultsPane.addTab("Resultados de la Consulta", new JScrollPane(new JTable(50, 12)));
        resultsPane.addTab("Salida del Script", new JScrollPane(new JTextArea("SQL> Ejecución terminada correctamente.")));

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, resultsPane);
        verticalSplit.setResizeWeight(0.6);
        verticalSplit.setDividerSize(7);

        container.add(editorBar, BorderLayout.NORTH);
        container.add(verticalSplit, BorderLayout.CENTER);

        return container;
    }

    private JPanel createSidebar(String title, String[] items) {
        JPanel p = new JPanel(new BorderLayout());

        // Cabecera estilizada usando los colores del UI Manager para consistencia
        JLabel header = new JLabel("  " + title.toUpperCase());
        header.setFont(new Font("Segoe UI", Font.BOLD, 10));
        header.setOpaque(true);
        header.setBackground(UIManager.getColor("MenuBar.background"));
        header.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.setPreferredSize(new Dimension(0, 26));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(title);
        for (String item : items) root.add(new DefaultMutableTreeNode(item));
        JTree tree = new JTree(root);
        tree.setBorder(new EmptyBorder(8, 8, 8, 8));

        p.add(header, BorderLayout.NORTH);
        p.add(new JScrollPane(tree), BorderLayout.CENTER);
        return p;
    }

    private JToolBar createMainToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(new EmptyBorder(3, 5, 3, 5));

        // En One Dark, los botones de la toolbar suelen ser más discretos
        String[] actions = {"📄 Nuevo", "📂 Abrir", "💾 Guardar", "|", "🔌 Conectar", "⚙ Config"};
        for (String a : actions) {
            if (a.equals("|")) tb.addSeparator();
            else {
                JButton b = new JButton(a);
                b.putClientProperty("JButton.buttonType", "toolBarButton");
                tb.add(b);
            }
        }
        return tb;
    }

    private JButton createIconButton(String text, String tooltip, Color color) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFont(new Font("Segoe UI Emoji", Font.BOLD, 14));
        b.setForeground(color);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        return b;
    }

    private JPanel createStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(2, 10, 2, 10));

        JLabel info = new JLabel("Connected: Oracle 21c (One Dark Mode active)");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        info.setForeground(ACCENT_BLUE);

        JLabel stats = new JLabel("UTF-8 | Line 1, Col 1    ");
        stats.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        p.add(info, BorderLayout.WEST);
        p.add(stats, BorderLayout.EAST);
        return p;
    }

    public static void main(String[] args) {
        // Configuración de FlatLaf ANTES de iniciar la GUI
        FlatOneDarkIJTheme.setup();

        // Optimización de UI
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("ScrollBar.showButtons", true);
        UIManager.put("SplitPane.dividerSize", 5);

        SwingUtilities.invokeLater(() -> {
            SQLDeveloperUltra gui = new SQLDeveloperUltra();
            gui.setVisible(true);
        });
    }
}
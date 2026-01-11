package org.bitBridge.view.swing;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainView extends JFrame {
    private JTextField ipField, puertoField, searchUser;
    private JLabel serverStatusLabel, connectionStatusLabel, connectionText;
    private JButton startServerBtn, btnAbrirSwing;
    private JComboBox<String> temaCombo, idiomaCombo;

    // Colores de tu estilo JavaFX
    private final Color HEADER_BG = new Color(44, 62, 80);
    private final Color CONNECTION_BG = new Color(52, 73, 94);

    public MainView() {
        setupLookAndFeel();
        initComponents();
        configureWindow();
    }

    private void setupLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void configureWindow() {
        setTitle("App de Transferencias - Vista Principal (Swing)");
        setSize(1100, 850);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // AGRUPAR CONTROLES SUPERIORES (Igual que mainBox en tu FX)
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));

        topContainer.add(createHeader());
        topContainer.add(createConnectionStatus());
        topContainer.add(createTopMenu());

        add(topContainer, BorderLayout.NORTH);

        // --- CUERPO CENTRAL (Center Box) ---
        JPanel centerBox = new JPanel();
        centerBox.setLayout(new BoxLayout(centerBox, BoxLayout.Y_AXIS));
        centerBox.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Paneles simulados (Aquí irían tus HostsPanel, ChatPanel, etc.)
        centerBox.add(createPlaceholderPanel("Hosts Panel (HostsPanel)", 150));
        centerBox.add(Box.createVerticalStrut(10));
        centerBox.add(createPlaceholderPanel("Chat Panel (ChatPanel)", 200));
        centerBox.add(Box.createVerticalStrut(10));
        centerBox.add(createPlaceholderPanel("Envío Avanzado (EnvioAvanzadoPanel)", 250));

        JScrollPane scrollPane = new JScrollPane(centerBox);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // --- SIDEBAR (Usuarios Conectados) ---
        add(createUsuariosSidebar(), BorderLayout.WEST);

        // --- BOTTOM (Estado y Seguridad) ---
        JPanel bottomContainer = new JPanel();
        bottomContainer.setLayout(new GridLayout(2, 1));
        bottomContainer.add(createEstadoSistemaBox());
        bottomContainer.add(createSeguridadBox());
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(10, 15, 10, 15));

        // Info Usuario
        JPanel userInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        userInfo.setOpaque(false);
        JLabel userLabel = new JLabel("👤 Juan Ortega");
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        userLabel.setForeground(Color.WHITE);

        JLabel statusLabel = new JLabel("[🟢 Offline ▼]");
        statusLabel.setForeground(Color.RED);

        userInfo.add(userLabel);
        userInfo.add(statusLabel);

        // Botones Derecha
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(new JButton("⚙️ Configuración"));
        buttons.add(new JButton("🔒 Seguridad"));
        buttons.add(new JButton("🚪 Cerrar sesión"));

        header.add(userInfo, BorderLayout.WEST);
        header.add(buttons, BorderLayout.EAST);
        return header;
    }

    private JPanel createConnectionStatus() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        bar.setBackground(CONNECTION_BG);
        bar.setBorder(new EmptyBorder(5, 10, 5, 10));

        connectionText = new JLabel("🔌 Conexión:");
        connectionStatusLabel = new JLabel("[🟢 Desconectado]");
        connectionStatusLabel.setForeground(Color.RED);

        ipField = new JTextField("192.168.100.111", 10);
        puertoField = new JTextField("8080", 5);

        startServerBtn = new JButton("🔛 Iniciar Servidor");
        btnAbrirSwing = new JButton("📱 Panel QR");
        btnAbrirSwing.setBackground(new Color(41, 128, 185));
        btnAbrirSwing.setForeground(Color.WHITE);

        bar.add(connectionText);
        bar.add(connectionStatusLabel);
        bar.add(new JLabel("IP:")); bar.add(ipField);
        bar.add(new JLabel("Port:")); bar.add(puertoField);
        bar.add(new JLabel("Latencia: 35ms"));
        bar.add(new JButton("🔄 Conectar"));
        bar.add(Box.createHorizontalStrut(20));
        bar.add(startServerBtn);
        bar.add(btnAbrirSwing);

        return bar;
    }

    private JPanel createTopMenu() {
        JPanel menu = new JPanel(new BorderLayout());
        menu.setBorder(new EmptyBorder(10, 15, 10, 15));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchUser = new JTextField(15);
        searchUser.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Buscar usuario...");

        leftActions.add(searchUser);
        leftActions.add(new JButton("📁 Mis archivos"));
        leftActions.add(new JButton("📤 Enviar archivo"));
        leftActions.add(new JButton("📤 Transferencias"));

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        temaCombo = new JComboBox<>(new String[]{"Claro", "Oscuro"});
        idiomaCombo = new JComboBox<>(new String[]{"Español", "Inglés"});

        rightActions.add(new JLabel("Tema:")); rightActions.add(temaCombo);
        rightActions.add(new JButton("⚙️ Personalizar"));
        rightActions.add(new JLabel("Idioma:")); rightActions.add(idiomaCombo);

        menu.add(leftActions, BorderLayout.WEST);
        menu.add(rightActions, BorderLayout.EAST);
        return menu;
    }

    private JPanel createUsuariosSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.DARK_GRAY));
        sidebar.setBorder(new EmptyBorder(10,10,10,10));

        JLabel title = new JLabel("👥 Usuarios Conectados");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("🟢 Ana Torres [en línea]");
        model.addElement("🟡 Pablo Gil [ausente]");
        model.addElement("🟢 Carlos Méndez [en línea]");

        JList<String> list = new JList<>(model);
        list.setBackground(new Color(30, 30, 30));

        sidebar.add(title, BorderLayout.NORTH);
        sidebar.add(new JScrollPane(list), BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel createEstadoSistemaBox() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        p.add(new JLabel("📦 Uso: 5.9 GB / 6 GB"));
        p.add(new JLabel("📈 Velocidad: 4.2 MB/s"));
        p.add(new JLabel("CPU: 35%"));
        p.add(new JLabel("RAM: 62%"));
        return p;
    }

    private JPanel createSeguridadBox() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        p.add(new JLabel("🛡️ Usuarios Bloqueados: 2"));
        p.add(new JLabel("🔐 2FA: Activado"));
        return p;
    }

    // Método de ayuda para crear bloques visuales
    private JPanel createPlaceholderPanel(String title, int height) {
        JPanel p = new JPanel(new BorderLayout());
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        p.setPreferredSize(new Dimension(500, height));
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainView().setVisible(true));
    }
}
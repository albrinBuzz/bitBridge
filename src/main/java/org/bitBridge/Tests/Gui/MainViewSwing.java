package org.bitBridge.Tests.Gui;

import com.formdev.flatlaf.FlatDarkLaf;
import org.bitBridge.Client.core.Client;
import org.bitBridge.server.core.Server;
import org.bitBridge.view.core.ConnectionState;
import org.bitBridge.view.core.IMainView;
import org.bitBridge.view.core.MainController;
import org.bitBridge.view.core.ServerState;
import org.bitBridge.view.swing.components.hosts.ChatPanelSwing;
import org.bitBridge.view.swing.components.hosts.HostsPanelSwing;
import org.bitBridge.view.swing.components.transfers.TransferenciasViewSwing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class MainViewSwing extends JFrame implements IMainView {

    private MainController controller;
    private Client client;
    private Server server;

    private ChatPanelSwing chatPanel;
    private HostsPanelSwing hostsPanel;
    private TransferenciasViewSwing transferenciasView;

    private JLabel serverStatusLabel, connectionStatusLabel;
    private JButton startServerBtn, connectBtn, autoConnectBtn;
    private JTextField ipField, portField;
    private JProgressBar discoveryProgress;

    public MainViewSwing() {
        configureTheme();
        setupDependencies();
        initGUI();
        setupShutdownHook();
    }

    private void configureTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
        } catch (Exception ignored) {}
    }

    private void setupDependencies() {
        this.client = new Client();
        this.server = Server.getInstance();
        this.controller = new MainController(this, client, server);

        this.chatPanel = new ChatPanelSwing(client);
        this.hostsPanel = new HostsPanelSwing(client);
        this.transferenciasView = new TransferenciasViewSwing();

        this.client.addObserver(chatPanel);
        this.client.getTransferenciaController().setTransferencesObserver(transferenciasView);
    }

    private void initGUI() {
        setTitle("FileTalk P2P - Console Pro");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1450, 900);
        setLayout(new BorderLayout());

        // --- 1. NORTE: TOOLBAR DE CONEXIÓN REAL ---
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(createHeader(), BorderLayout.NORTH);
        northPanel.add(createUnifiedControlBar(), BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // --- 2. CENTRO: TRIPLE PANEL DISTRIBUIDO ---
        // Split Derecho: [Área Trabajo | Panel de Info]
        JSplitPane workAndInfoSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        workAndInfoSplit.setDividerLocation(850);
        workAndInfoSplit.setResizeWeight(1.0);

        // Área Trabajo (Vertical): [Chat | Monitor Transferencias]
        JSplitPane chatTransfersSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        chatTransfersSplit.setDividerLocation(450);
        chatTransfersSplit.setTopComponent(chatPanel);
        chatTransfersSplit.setBottomComponent(transferenciasView);
        chatTransfersSplit.setResizeWeight(0.6);

        workAndInfoSplit.setLeftComponent(chatTransfersSplit);
        workAndInfoSplit.setRightComponent(createNodeInfoPanel());

        // Split Principal: [Lista Hosts | workAndInfoSplit]
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(280);
        mainSplit.setLeftComponent(hostsPanel);
        mainSplit.setRightComponent(workAndInfoSplit);

        add(mainSplit, BorderLayout.CENTER);

        // --- 3. SUR: DASHBOARD DE ESTADÍSTICAS ---
        add(createStatsDashboard(), BorderLayout.SOUTH);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { exitApp(); }
        });
        setLocationRelativeTo(null);
    }

    // --- COMPONENTES DE LA INTERFAZ ---

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 25, 29));
        header.setBorder(new EmptyBorder(8, 20, 8, 20));
        JLabel logo = new JLabel("FILETALK P2P");
        logo.setFont(new Font("SansSerif", Font.BOLD, 18));
        logo.setForeground(new Color(0, 191, 255));
        header.add(logo, BorderLayout.WEST);
        return header;
    }

    private JPanel createUnifiedControlBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBackground(new Color(35, 41, 46));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);

        connectionStatusLabel = new JLabel("● OFF");
        connectionStatusLabel.setForeground(Color.RED);
        ipField = new JTextField("127.0.0.1", 8);
        portField = new JTextField("8080", 4);
        connectBtn = new JButton("Conectar");
        connectBtn.addActionListener(e -> handleConnection());

        autoConnectBtn = new JButton("🔍 Radar");
        autoConnectBtn.addActionListener(e -> startDiscovery());
        discoveryProgress = new JProgressBar();
        discoveryProgress.setIndeterminate(true);
        discoveryProgress.setVisible(false);
        discoveryProgress.setPreferredSize(new Dimension(50, 4));

        serverStatusLabel = new JLabel("○ NODO: OFF");
        startServerBtn = new JButton("Activar Servidor");
        startServerBtn.addActionListener(e -> handleServer());

        gbc.gridx = 0; bar.add(connectionStatusLabel, gbc);
        gbc.gridx = 1; bar.add(ipField, gbc);
        gbc.gridx = 2; bar.add(portField, gbc);
        gbc.gridx = 3; bar.add(connectBtn, gbc);
        gbc.gridx = 4; bar.add(autoConnectBtn, gbc);
        gbc.gridx = 5; bar.add(discoveryProgress, gbc);
        gbc.gridx = 6; gbc.weightx = 1.0; bar.add(Box.createGlue(), gbc);
        gbc.gridx = 7; gbc.weightx = 0; bar.add(serverStatusLabel, gbc);
        gbc.gridx = 8; bar.add(startServerBtn, gbc);

        return bar;
    }

    private JPanel createNodeInfoPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(45, 52, 54));
        p.setBorder(new EmptyBorder(20, 15, 20, 15));
        p.setPreferredSize(new Dimension(300, 0));

        JLabel name = new JLabel("Información del Nodo", SwingConstants.CENTER);
        name.setFont(new Font("SansSerif", Font.BOLD, 14));
        name.setAlignmentX(CENTER_ALIGNMENT);

        JPanel details = new JPanel(new GridLayout(4, 1, 0, 10));
        details.setOpaque(false);
        details.setBorder(new TitledBorder("Detalles"));
        details.add(new JLabel("IP: --"));
        details.add(new JLabel("Latencia: -- ms"));
        details.add(new JLabel("Archivos Compartidos: 0"));

        p.add(name);
        p.add(Box.createVerticalStrut(20));
        p.add(details);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel createStatsDashboard() {
        JPanel dash = new JPanel(new GridLayout(1, 3, 20, 0));
        dash.setBackground(new Color(25, 25, 25));
        dash.setPreferredSize(new Dimension(0, 60));
        dash.setBorder(new EmptyBorder(10, 20, 10, 20));

        dash.add(createStatUnit("TRÁFICO RED", "0.0 KB/s", Color.CYAN));
        dash.add(createStatUnit("SESIÓN", "0 MB", Color.GREEN));
        dash.add(createStatUnit("ESTADO P2P", "Protegido", Color.WHITE));

        return dash;
    }

    private JPanel createStatUnit(String t, String v, Color c) {
        JPanel p = new JPanel(new GridLayout(2, 1));
        p.setOpaque(false);
        JLabel lblT = new JLabel(t); lblT.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JLabel lblV = new JLabel(v); lblV.setFont(new Font("Monospaced", Font.BOLD, 14)); lblV.setForeground(c);
        p.add(lblT); p.add(lblV);
        return p;
    }

    // --- MÉTODOS DE LÓGICA (IMainView) ---

    private void handleConnection() {
        if (connectBtn.getText().equals("Desconectar")) {
            controller.disconnectServer();
        } else {
            controller.connectServer(ipField.getText(), portField.getText());
        }
    }

    private void handleServer() {
        if (server.isRunning()) controller.stopServer(); else controller.startServer();
    }

    private void startDiscovery() {
        autoConnectBtn.setEnabled(false);
        discoveryProgress.setVisible(true);
        new Thread(() -> {
            try { client.conexionAutomatica(); } catch (Exception ignored) {}
            finally { SwingUtilities.invokeLater(() -> {
                discoveryProgress.setVisible(false);
                autoConnectBtn.setEnabled(true);
            }); }
        }).start();
    }

    @Override public void updateServerUI(ServerState s, String e) {
        SwingUtilities.invokeLater(() -> {
            boolean r = (s == ServerState.RUNNING);
            serverStatusLabel.setText(r ? "● ONLINE" : "○ OFF");
            serverStatusLabel.setForeground(r ? Color.GREEN : Color.GRAY);
            startServerBtn.setText(r ? "Apagar" : "Activar");
        });
    }

    @Override public void updateConnectionUI(ConnectionState s, String d) {
        SwingUtilities.invokeLater(() -> {
            boolean c = (s == ConnectionState.CONNECTED);
            connectionStatusLabel.setText(c ? "● CONECTADO" : "● OFF");
            connectionStatusLabel.setForeground(c ? Color.GREEN : Color.RED);
            connectBtn.setText(c ? "Desconectar" : "Conectar");
        });
    }

    private void exitApp() { if(JOptionPane.showConfirmDialog(this, "¿Salir?") == 0) System.exit(0); }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) server.stopServer();
            if (client != null) client.desconect();
        }));
    }

    @Override public void showAlert(String t, String c) { JOptionPane.showMessageDialog(this, c, t, 1); }
    @Override public void updateTheme(String t) {}

    public static void main(String[] args) {
        // Esto evita que Java verifique los módulos de JavaFX al arrancar
        //Main.main(args);

        SwingUtilities.invokeLater(() -> new MainViewSwing().setVisible(true));
    }
}
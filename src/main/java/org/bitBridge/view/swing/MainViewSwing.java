package org.bitBridge.view.swing;

import com.formdev.flatlaf.FlatDarkLaf;
import org.bitBridge.Client.ConfiguracionCliente;
import org.bitBridge.Client.UtilidadesCliente;
import org.bitBridge.Client.core.Client;
import org.bitBridge.server.core.Server;
import org.bitBridge.view.core.ConnectionState;
import org.bitBridge.view.core.IMainView;
import org.bitBridge.view.core.MainController;
import org.bitBridge.view.core.ServerState;
import org.bitBridge.view.swing.components.hosts.ChatPanelSwing;
import org.bitBridge.view.swing.components.hosts.HostsPanelSwing;
import org.bitBridge.view.swing.components.transfers.TransferenciasViewSwing;
import org.bitBridge.web.serverGui.ServidorLauncher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.regex.Pattern;

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

    private final Color COLOR_SUCCESS = new Color(46, 204, 113);
    private final Color COLOR_DANGER = new Color(231, 76, 60);
    private final Color COLOR_PRIMARY = new Color(52, 152, 219);
    private final Color COLOR_WARNING = new Color(241, 196, 15);

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
            UIManager.put("TextComponent.arc", 12);
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
        setTitle("FileTalk P2P - Secure Network Console");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1300, 850);
        setLayout(new BorderLayout());

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(createHeader(), BorderLayout.NORTH);
        northPanel.add(createUnifiedControlBar(), BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        horizontalSplit.setDividerLocation(300);
        horizontalSplit.setLeftComponent(hostsPanel);

        JSplitPane verticalWorkSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalWorkSplit.setDividerLocation(450);
        verticalWorkSplit.setTopComponent(chatPanel);
        verticalWorkSplit.setBottomComponent(transferenciasView);
        verticalWorkSplit.setResizeWeight(0.7);

        horizontalSplit.setRightComponent(verticalWorkSplit);
        add(horizontalSplit, BorderLayout.CENTER);

        add(createFooterStatus(), BorderLayout.SOUTH);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { exitApplication(); }
        });

        setLocationRelativeTo(null);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 25, 29));
        header.setBorder(new EmptyBorder(10, 20, 10, 20));
        JLabel logo = new JLabel("FILETALK P2P");
        logo.setFont(new Font("SansSerif", Font.BOLD, 18));
        logo.setForeground(COLOR_PRIMARY);
        JPanel navActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        navActions.setOpaque(false);
        JButton btnDownloads = new JButton("📁 Abrir Descargas");
        btnDownloads.addActionListener(e -> openDownloadsFolder());
        JButton btnPortal = new JButton("📱 Puente Móvil");
        btnPortal.setBackground(new Color(60, 63, 65));
        btnPortal.addActionListener(e -> new ServidorLauncher().setVisible(true));
        navActions.add(btnDownloads);
        navActions.add(btnPortal);
        header.add(logo, BorderLayout.WEST);
        header.add(navActions, BorderLayout.EAST);
        return header;
    }

    private JPanel createUnifiedControlBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBackground(new Color(35, 41, 46));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);

        connectionStatusLabel = new JLabel("● DESCONECTADO");
        connectionStatusLabel.setForeground(COLOR_DANGER);
        ipField = new JTextField("127.0.0.1", 10);
        portField = new JTextField("8080", 5);
        connectBtn = new JButton("Conectar");
        connectBtn.addActionListener(e -> handleConnectionAction());

        autoConnectBtn = new JButton("🔍 Escaneo Automático");
        autoConnectBtn.addActionListener(e -> startAutoDiscovery());
        discoveryProgress = new JProgressBar();
        discoveryProgress.setIndeterminate(true);
        discoveryProgress.setPreferredSize(new Dimension(80, 4));
        discoveryProgress.setVisible(false);

        serverStatusLabel = new JLabel("○ NODO LOCAL: OFF");
        startServerBtn = new JButton("Activar Servidor");
        startServerBtn.addActionListener(e -> handleServerAction());

        gbc.gridx = 0; bar.add(connectionStatusLabel, gbc);
        gbc.gridx = 1; bar.add(ipField, gbc);
        gbc.gridx = 2; bar.add(portField, gbc);
        gbc.gridx = 3; bar.add(connectBtn, gbc);
        gbc.gridx = 4; gbc.insets = new Insets(0, 20, 0, 20);
        bar.add(new JSeparator(JSeparator.VERTICAL) {{ setPreferredSize(new Dimension(2, 25)); }}, gbc);
        gbc.gridx = 5; gbc.insets = new Insets(8, 10, 8, 10);
        bar.add(autoConnectBtn, gbc);
        gbc.gridx = 6; bar.add(discoveryProgress, gbc);
        gbc.gridx = 7; gbc.weightx = 1.0; bar.add(Box.createGlue(), gbc);
        gbc.gridx = 8; gbc.weightx = 0; bar.add(serverStatusLabel, gbc);
        gbc.gridx = 9; bar.add(startServerBtn, gbc);

        return bar;
    }

    // --- LÓGICA DE VALIDACIÓN AVANZADA ---

    private void handleConnectionAction() {
        if (connectBtn.getText().equals("Desconectar")) {
            int confirm = JOptionPane.showConfirmDialog(this, "¿Cerrar la conexión actual?", "Confirmar", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) controller.disconnectServer();
        } else {
            String host = ipField.getText().trim();
            String portStr = portField.getText().trim();

            // 1. Validar campos vacíos
            if (host.isEmpty() || portStr.isEmpty()) {
                highlightFieldsError("Campos obligatorios vacíos.");
                return;
            }

            // 2. Validar formato de IP (IPv4 o localhost)
            // 2. Regex combinada: Soporta IPv4 Y Dominios (RFC 1035)
// Acepta: 192.168.1.1, 8.8.8.8, localhost, mi.dominio.com, servidor-p2p.net
            String hostRegex = "^(?i)(" +
                    "localhost|" + // Localhost
                    "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])|" + // IPv4
                    "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])" + // Dominios
                    ")$";

            if (!Pattern.matches(hostRegex, host)) {
                highlightFieldsError("Destino inválido. Usa una IP o un nombre de dominio.");
                return;
            }

            // 3. Validar Puerto (Numérico y rango)
            try {
                int port = Integer.parseInt(portStr);
                if (port < 1024 || port > 65535) {
                    highlightFieldsError("Puerto fuera de rango (1024-65535).");
                    return;
                }
            } catch (NumberFormatException e) {
                highlightFieldsError("El puerto debe ser un número.");
                return;
            }

            connectBtn.setEnabled(false); // Prevenir spam
            controller.connectServer(host, portStr);
        }
    }

    private void handleServerAction() {
        if (server.isRunning()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Se cerrarán todas las conexiones. ¿Apagar servidor?",
                    "Atención", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                startServerBtn.setEnabled(false);
                controller.stopServer();
            }
        } else {
            // Validar si el puerto configurado está disponible podría ir aquí,
            // pero el núcleo (Server) suele lanzar el error que capturamos en updateServerUI.
            startServerBtn.setEnabled(false);
            controller.startServer();
        }
    }

    private void highlightFieldsError(String message) {
        ipField.setBorder(BorderFactory.createLineBorder(COLOR_DANGER));
        portField.setBorder(BorderFactory.createLineBorder(COLOR_DANGER));

        Timer timer = new Timer(2000, e -> {
            ipField.setBorder(UIManager.getBorder("TextField.border"));
            portField.setBorder(UIManager.getBorder("TextField.border"));
        });
        timer.setRepeats(false);
        timer.start();

        showAlert("Error de Validación", message);
    }

    private void startAutoDiscovery() {
        autoConnectBtn.setEnabled(false);
        discoveryProgress.setVisible(true);
        new Thread(() -> {
            try {
                client.conexionAutomatica();
                SwingUtilities.invokeLater(() -> {
                    discoveryProgress.setVisible(false);
                    autoConnectBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    discoveryProgress.setVisible(false);
                    autoConnectBtn.setEnabled(true);
                    showAlert("Red", "No se detectó el servidor en la red local.");
                });
            }
        }).start();
    }

    private void exitApplication() {
        int confirm = JOptionPane.showConfirmDialog(this, "¿Cerrar FileTalk?", "Salir", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) System.exit(0);
    }

    // --- FEEDBACK DE ERRORES DE RED DESDE EL NÚCLEO ---
    @Override
    public void updateServerUI(ServerState state, String error) {
        SwingUtilities.invokeLater(() -> {
            startServerBtn.setEnabled(true);
            boolean isRunning = (state == ServerState.RUNNING);

            if (state == ServerState.ERROR && error != null) {
                // Mostramos el mensaje detallado que viene desde el throw del Server
                showAlert("Fallo en Nodo Local", "<html><body style='width: 250px;'>" + error + "</body></html>");
            }

            serverStatusLabel.setText(isRunning ? "● NODO LOCAL: ONLINE" : "○ NODO LOCAL: OFF");
            serverStatusLabel.setForeground(isRunning ? COLOR_SUCCESS : Color.GRAY);
            startServerBtn.setText(isRunning ? "Apagar Servidor" : "Activar Servidor");
        });
    }



    @Override
    public void updateConnectionUI(ConnectionState state, String detail) {
        SwingUtilities.invokeLater(() -> {
            connectBtn.setEnabled(state != ConnectionState.CONNECTING);

            if (state == ConnectionState.CONNECTION_ERROR) {
                showAlert("Error de Conexión", "No se pudo establecer el enlace: " + detail);
            }

            switch (state) {
                case CONNECTED -> {
                    connectionStatusLabel.setText("● ONLINE");
                    connectionStatusLabel.setForeground(COLOR_SUCCESS);
                    connectBtn.setText("Desconectar");
                    connectBtn.setBackground(COLOR_DANGER);
                    ipField.setEnabled(false);
                    portField.setEnabled(false);
                }
                case DISCONNECTED, CONNECTION_ERROR -> {
                    connectionStatusLabel.setText("● OFF");
                    connectionStatusLabel.setForeground(COLOR_DANGER);
                    connectBtn.setText("Conectar");
                    connectBtn.setBackground(COLOR_PRIMARY);
                    ipField.setEnabled(true);
                    portField.setEnabled(true);
                }
                case CONNECTING -> {
                    connectionStatusLabel.setText("● BUSCANDO...");
                    connectionStatusLabel.setForeground(COLOR_WARNING);
                }
            }
        });
    }

    private JPanel createFooterStatus() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(20, 25, 29));
        footer.setBorder(new EmptyBorder(5, 15, 5, 15));
        JLabel status = new JLabel("Red P2P Activa | Verificando Puertos...");
        status.setFont(new Font("SansSerif", Font.PLAIN, 10));
        status.setForeground(Color.GRAY);
        footer.add(status, BorderLayout.WEST);
        return footer;
    }

    private void openDownloadsFolder() {
        UtilidadesCliente.abrirDirectorioDescargas(new ConfiguracionCliente().obtener("cliente.directorio_descargas"));
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) server.stopServer();
            if (client != null) client.desconect();
        }));
    }

    @Override public void showAlert(String t, String c) { JOptionPane.showMessageDialog(this, c, t, 1); }
    @Override public void updateTheme(String t) {}
}
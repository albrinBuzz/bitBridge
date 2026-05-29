package org.bitBridge.view.swing;


import com.formdev.flatlaf.FlatClientProperties;
import org.bitBridge.Client.UtilidadesCliente;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.server.core.Server;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.view.core.ConnectionState;
import org.bitBridge.view.core.MainController;
import org.bitBridge.view.core.ServerState;
import org.bitBridge.view.swing.components.server.ConfiguracionView;
import org.bitBridge.view.swing.components.server.ServerDashboard;
import org.bitBridge.web.serverGui.ServidorLauncher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.regex.Pattern;

public class HeaderPanel extends JPanel {
    // Colores de identidad
    private static final Color ACCENT_COLOR = new Color(0, 122, 255); // Azul eléctrico (estilo macOS/modern)
    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);
    private static final Color DANGER_RED = new Color(231, 76, 60);
    private static final Color BG_DARK = new Color(28, 28, 30); // Fondo tipo "Slate" profundo
    private static final Color TEXT_SECONDARY = new Color(174, 174, 178);
    private final Color COLOR_WARNING = new Color(241, 196, 15);

    // Componentes que MainView podría necesitar consultar
    private JLabel lblConnectionStatus, lblHubStatus;
    private JTextField txtIp, txtPort;
    private JButton btnConnect, btnAutoConnect, btnStartHub;
    private JProgressBar discoveryProgress;

    private final MainController controller;
    private long connectionStartTime;

    public HeaderPanel(MainController controller) {
        this.controller = controller;
        this.setLayout(new BorderLayout());
        this.setBackground(new Color(35, 35, 35));
        this.setBorder(new EmptyBorder(8, 15, 8, 15));
        initComponents();
    }

    private void initComponents() {
        // --- PANEL IZQUIERDO (CLIENTE) ---
        add(createClientPanel(), BorderLayout.WEST);

        // --- CENTRO (BUSCADOR) ---
        add(createSearchPanel(), BorderLayout.CENTER);

        // --- PANEL DERECHO (SERVIDOR / HUB) ---
        add(createServerPanel(), BorderLayout.EAST);
    }

    private JPanel createClientPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        panel.setOpaque(false);

        JLabel lblBrand = new JLabel("BITBRIDGE");
        lblBrand.setFont(new Font("Inter", Font.BOLD, 16));
        lblBrand.setForeground(ACCENT_COLOR);

        lblConnectionStatus = new JLabel("● OFFLINE");
        lblConnectionStatus.setForeground(DANGER_RED);
        lblConnectionStatus.setFont(new Font("Monospaced", Font.BOLD, 11));

        txtIp = new JTextField("127.0.0.1", 8);
        txtPort = new JTextField("8080", 4);
        txtIp.setText("127.0.0.1");
        txtPort.setText("8080");

        btnConnect = new JButton("CONECTAR");
        btnConnect.setBackground(ACCENT_COLOR.darker());
        btnConnect.addActionListener(e -> handleConnection());

        btnAutoConnect = new JButton("⚡ Conexion Automatica");
        btnAutoConnect.setForeground(Color.CYAN);
        btnAutoConnect.setBackground(new Color(40, 40, 40));
        //btnAutoConnect.addActionListener(e -> {handleConnection();});

        btnAutoConnect.addActionListener(e -> startAutoDiscovery());
        discoveryProgress = new JProgressBar();
        discoveryProgress.setIndeterminate(true);
        discoveryProgress.setPreferredSize(new Dimension(80, 4));
        discoveryProgress.setVisible(false);
        discoveryProgress.setIndeterminate(true);


        panel.add(lblBrand);
        panel.add(new JSeparator(SwingConstants.VERTICAL));
        panel.add(lblConnectionStatus);
        panel.add(new JLabel("Host:"));
        panel.add(txtIp);
        panel.add(new JLabel("Puerto :"));
        panel.add(txtPort);
        panel.add(btnConnect);
        panel.add(btnAutoConnect);
        panel.add(discoveryProgress);
        return panel;
    }

    private JPanel createSearchPanel() {
        JPanel container = new JPanel(new FlowLayout(FlowLayout.CENTER));
        container.setOpaque(false);
        JTextField search = new JTextField(20);
        search.putClientProperty("JTextField.placeholderText", "Búsqueda en red...");
        container.add(new JLabel("🔍"));
        container.add(search);
        return container;
    }

    private JPanel createServerPanel() {
        // Usamos GridBagLayout para tener control total sobre los espacios
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);

        // --- GRUPO 1: ESTADO DEL HUB ---
        // Estado inicial: Gris y texto descriptivo de apagado
        lblHubStatus = new JLabel("HUB: OFFLINE");
        lblHubStatus.setFont(new Font("Monospaced", Font.BOLD, 11));
        lblHubStatus.setForeground(Color.GRAY); // Color neutro inicial

        btnStartHub = new JButton("ENCENDER HUB");
        btnStartHub.setCursor(new Cursor(Cursor.HAND_CURSOR));
// Estilo inicial: Gris oscuro (Carbono)
        btnStartHub.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; background: #353b48; foreground: #ffffff; borderWidth: 0; focusWidth: 0; margin: 4,12,4,12");
        btnStartHub.addActionListener(e -> handleServerAction());

        // --- GRUPO 2: ACCIONES SECUNDARIAS (Monitor y Descargas) ---
        JButton btnDashboard = createStyledNavButton("📊Rendimiento", "Ver estadísticas en tiempo real");
        btnDashboard.addActionListener(e -> new ServerDashboard(Server.getInstance()).setVisible(true));

        JButton btnDownloads = createStyledNavButton("📂 Descargas", "Abrir carpeta de archivos");
        btnDownloads.addActionListener(e -> openDownloadsFolder());

        // --- GRUPO 3: PUENTE Y AJUSTES (Iconos) ---
        // Botón especial para el Puente Móvil con degradado o color sólido distintivo
        JButton btnPortal = createIconButton("📱Web", "Interfaz Web");
        btnPortal.setForeground(new Color(162, 155, 254)); // Un color lila moderno
        btnPortal.addActionListener(e -> new ServidorLauncher().setVisible(true));

        JButton btnConfig = createIconButton("⚙", "Configuración del Sistema");
        btnConfig.addActionListener(e -> {
            Window parent = SwingUtilities.getWindowAncestor(this);
            if (parent instanceof Frame) new ConfiguracionView((Frame) parent).setVisible(true);
        });

        JButton btnProfile = createIconButton("👤", "Mi Perfil");

        // Construcción del Panel con separadores visuales
        panel.add(lblHubStatus);
        panel.add(btnStartHub);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(btnDashboard);
        panel.add(btnDownloads);
        panel.add(createVerticalSeparator());
        panel.add(btnPortal);
        panel.add(btnConfig);
        panel.add(btnProfile);

        return panel;
    }

// --- MÉTODOS DE APOYO PARA ESTILO ---

    private JButton createStyledNavButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // Estilo sutil: Fondo gris oscuro, texto claro, bordes redondeados
        btn.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; background: #353b48; foreground: #f5f6fa; borderWidth: 0; margin: 4,10,4,10");
        return btn;
    }

    private JSeparator createVerticalSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 20));
        sep.setForeground(new Color(60, 60, 60));
        return sep;
    }

// --- MÉTODOS AUXILIARES PARA UX ---

    private JButton createIconButton(String icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // Botón estilo "ToolBar" (sin bordes pesados)
        btn.putClientProperty(FlatClientProperties.STYLE, "arc: 999; borderWidth: 0; focusWidth: 0; background: null");
        btn.setPreferredSize(new Dimension(32, 32));
        return btn;
    }



    private void handleConnection() {

        if (btnConnect.getText().equals("Desconectar")) {
            int confirm = JOptionPane.showConfirmDialog(this, "¿Cerrar la conexión actual?", "Confirmar", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) controller.disconnectServer();
        } else {
            String host = txtIp.getText().trim();
            String portStr = txtPort.getText().trim();

            // 1. Validar campos vacíos
            if (host.isEmpty() || portStr.isEmpty()) {
                //highlightFieldsError("Campos obligatorios vacíos.");
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
                //highlightFieldsError("Destino inválido. Usa una IP o un nombre de dominio.");
                return;
            }

            // 3. Validar Puerto (Numérico y rango)
            try {
                int port = Integer.parseInt(portStr);
                if (port < 1024 || port > 65535) {
                    //highlightFieldsError("Puerto fuera de rango (1024-65535).");
                    return;
                }
            } catch (NumberFormatException e) {
                //highlightFieldsError("El puerto debe ser un número.");
                return;
            }

            btnConnect.setEnabled(false); // Prevenir spam
            this.connectionStartTime = System.currentTimeMillis();
            controller.connectServer(host, portStr);
        }
    }

    private void startAutoDiscovery() {
        btnAutoConnect.setEnabled(false);
        discoveryProgress.setVisible(true);
        controller.startAutoDiscovery();
    }

    private void handleServerAction() {
        if (btnStartHub.getText().equals("ENCENDER HUB")||btnStartHub.getText().equals("REINTENTAR")) {
            btnStartHub.setEnabled(false);
            controller.startServer();

        } else {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Se cerrarán todas las conexiones. ¿Apagar servidor?",
                    "Atención", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                btnStartHub.setEnabled(false);
                controller.stopServer();
            }
        }
    }

    public void updateConnectionStatus(ConnectionState state, String detail) {
        boolean isConnecting = (state == ConnectionState.CONNECTING);
        btnConnect.setEnabled(!isConnecting);
        btnAutoConnect.setEnabled(!isConnecting);

        switch (state) {
            case CONNECTED -> {
                long duration = System.currentTimeMillis() - connectionStartTime;
                double seconds = duration / 1000.0;
                lblConnectionStatus.setText("● ONLINE");
                lblConnectionStatus.setForeground(SUCCESS_GREEN);
                //System.out.println("Conexión establecida en: " + seconds + "S");
                btnConnect.setText("Desconectar");
                btnConnect.setBackground(DANGER_RED);
                toggleInputs(false);
                Timer fadeOut = new Timer(100, e -> discoveryProgress.setVisible(false));
                fadeOut.setRepeats(false);
                fadeOut.start();

            }
            case DISCONNECTED, CONNECTION_ERROR -> {
                lblConnectionStatus.setText(state == ConnectionState.CONNECTION_ERROR ? "● ERROR" : "● OFF");
                lblConnectionStatus.setForeground(DANGER_RED);
                btnConnect.setText("Conectar");
                btnConnect.setBackground(new Color(52, 152, 219));
                toggleInputs(true);
                discoveryProgress.setVisible(false);

                // LANZAR POP-UP SI HAY DETALLE
                if (detail != null && !detail.isEmpty()) {
                    showDetailPopup(state, detail);
                }
            }
            case CONNECTING -> {
                lblConnectionStatus.setText("● BUSCANDO...");
                lblConnectionStatus.setForeground(COLOR_WARNING);
                discoveryProgress.setVisible(true);
                discoveryProgress.setIndeterminate(true);
            }
        }
    }

    private void showDetailPopup(ConnectionState state, String detail) {
        String title = (state == ConnectionState.CONNECTION_ERROR) ? "Error de Conexión" : "Estado de Red";
        int messageType = (state == ConnectionState.CONNECTION_ERROR) ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE;

        // JOptionPane usa el estilo de FlatLaf automáticamente si ya está configurado
        JOptionPane.showMessageDialog(this, detail, title, messageType);
    }


    public void updateServerStatus(ServerState state, String errorMessage) {
        // Rehabilitar el botón a menos que esté en proceso de inicio
        btnStartHub.setEnabled(state != ServerState.STARTING);

        switch (state) {
            case RUNNING -> {
                btnStartHub.setText("APAGAR HUB");
                btnStartHub.setBackground(SUCCESS_GREEN.darker());
                btnStartHub.setForeground(Color.WHITE);
                lblHubStatus.setText("ESTADO HUB: ACTIVO");
                lblHubStatus.setForeground(SUCCESS_GREEN);
                // Si quieres usar el estilo FlatLaf dinámicamente:
                btnStartHub.putClientProperty(FlatClientProperties.STYLE, "background: #27ae60; foreground: #ffffff");
            }
            case ERROR -> {
                btnStartHub.setText("REINTENTAR");
                btnStartHub.setBackground(DANGER_RED.darker());
                btnStartHub.setForeground(Color.WHITE);
                lblHubStatus.setText("HUB: ERROR DE PUERTO");
                lblHubStatus.setForeground(DANGER_RED);

                // Opcional: mostrar un tooltip con el mensaje de error específico
                if (errorMessage != null) {
                    btnStartHub.setToolTipText(errorMessage);
                }
            }
            case STARTING -> {
                btnStartHub.setText("INICIANDO...");
                lblHubStatus.setText("HUB: CARGANDO...");
                lblHubStatus.setForeground(COLOR_WARNING);
            }
            default -> { // STOPPED / OFF
                btnStartHub.setText("ENCENDER HUB");
                btnStartHub.setBackground(new Color(45, 45, 45));
                btnStartHub.setForeground(Color.LIGHT_GRAY);
                lblHubStatus.setText("ESTADO HUB: OFF");
                lblHubStatus.setForeground(Color.GRAY);
                btnStartHub.setToolTipText("Iniciar el servidor local");
            }
        }
    }

    private void toggleInputs(boolean enabled) {
        txtIp.setEnabled(enabled);
        txtPort.setEnabled(enabled);
    }
    private void openDownloadsFolder() {

        UtilidadesCliente.abrirDirectorioDescargas(ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR));
    }
    // Getters para que el controlador obtenga los datos de los campos
    public String getIp() { return txtIp.getText(); }
    public String getPort() { return txtPort.getText(); }
}
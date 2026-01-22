package org.bitBridge.Tests.Gui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import org.bitBridge.view.core.ConnectionState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Random;

/**
 * Vista de Conexión Global BitBridge.
 * Diseñada para representar conexiones WAN (Internet) con visualización de nodos.
 */
public class GlobalConnectionHeader extends JPanel {

    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);
    private static final Color GLOBAL_BLUE = new Color(52, 152, 219);
    private static final Color WARNING_ORANGE = new Color(230, 126, 34);

    private JComboBox<String> comboGlobalId;
    private JButton btnConnect;
    private JLabel lblLinkPath, lblLatency, lblRegion, lblSecureIcon;
    private JPanel centerCardPanel;
    private CardLayout centerLayout;
    private JProgressBar globalProgress;

    public GlobalConnectionHeader() {
        this.setLayout(new BorderLayout());
        this.setBackground(new Color(25, 25, 25));
        this.setBorder(new EmptyBorder(12, 25, 12, 25));

        initComponents();
        setupGlobalLogic();
    }

    private void initComponents() {
        // --- IZQUIERDA: Acceso por Identificador Único (BitBridge ID) ---
        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftGroup.setOpaque(false);

        JLabel lblGlobe = new JLabel("󰖟"); // Icono de mundo
        lblGlobe.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
        lblGlobe.setForeground(GLOBAL_BLUE);

        // En redes globales no usas IPs, usas IDs o Dominios
        comboGlobalId = new JComboBox<>(new String[]{"cris-office-linux", "relay.bitbridge.net", "home-nas-01"});
        comboGlobalId.setEditable(true);
        comboGlobalId.setPreferredSize(new Dimension(250, 35));
        comboGlobalId.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Introduce BitBridge ID o DNS...");
        comboGlobalId.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: #1e1e1e");

        btnConnect = new JButton("ESTABLECER VÍNCULO");
        btnConnect.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: #3498db; foreground: #ffffff; borderWidth: 0");

        leftGroup.add(lblGlobe);
        leftGroup.add(comboGlobalId);
        leftGroup.add(btnConnect);

        // --- CENTRO: Visualización de Ruta Global (WAN) ---
        centerLayout = new CardLayout();
        centerCardPanel = new JPanel(centerLayout);
        centerCardPanel.setOpaque(false);

        // Vista de carga global
        JPanel searchingPanel = new JPanel(new BorderLayout());
        searchingPanel.setOpaque(false);
        globalProgress = new JProgressBar();
        globalProgress.setIndeterminate(true);
        globalProgress.setPreferredSize(new Dimension(200, 2));
        JLabel lblSearching = new JLabel("Resolviendo ruta global...");
        lblSearching.setFont(new Font("SansSerif", Font.ITALIC, 11));
        lblSearching.setHorizontalAlignment(SwingConstants.CENTER);
        searchingPanel.add(lblSearching, BorderLayout.NORTH);
        searchingPanel.add(globalProgress, BorderLayout.CENTER);

        // Vista de Conexión Establecida (Nodos Geográficos)
        JPanel routePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        routePanel.setOpaque(false);

        lblRegion = new JLabel("[CL]"); // Flag o Región
        lblRegion.setToolTipText("Región detectada: Chile");

        lblSecureIcon = new JLabel("󰦝"); // Icono de candado (Encrypted)
        lblSecureIcon.setForeground(SUCCESS_GREEN);

        lblLinkPath = new JLabel("Local ───󰖟─── Remote");
        lblLinkPath.setFont(new Font("Monospaced", Font.BOLD, 12));

        lblLatency = new JLabel("--- ms");
        lblLatency.setForeground(WARNING_ORANGE);

        routePanel.add(lblRegion);
        routePanel.add(lblSecureIcon);
        routePanel.add(lblLinkPath);
        routePanel.add(lblLatency);

        centerCardPanel.add(searchingPanel, "SEARCHING");
        centerCardPanel.add(routePanel, "CONNECTED");
        centerCardPanel.add(new JLabel(""), "IDLE");
        centerLayout.show(centerCardPanel, "IDLE");

        // --- DERECHA: Status de Relay y Seguridad ---
        JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightGroup.setOpaque(false);

        JLabel lblEncryption = new JLabel("AES-256");
        lblEncryption.setFont(new Font("SansSerif", Font.BOLD, 9));
        lblEncryption.setForeground(Color.DARK_GRAY);

        JButton btnTunnel = new JButton("TUNNEL PRO");
        btnTunnel.putClientProperty(FlatClientProperties.STYLE, "arc: 20; background: #2c3e50; foreground: #95a5a6; font: 10");

        rightGroup.add(lblEncryption);
        rightGroup.add(btnTunnel);

        add(leftGroup, BorderLayout.WEST);
        add(centerCardPanel, BorderLayout.CENTER);
        add(rightGroup, BorderLayout.EAST);
    }

    private void setupGlobalLogic() {
        btnConnect.addActionListener(e -> {
            if (btnConnect.getText().contains("ESTABLECER")) {
                startGlobalHandshake();
            } else {
                resetView();
            }
        });
    }

    private void startGlobalHandshake() {
        btnConnect.setEnabled(false);
        centerLayout.show(centerCardPanel, "SEARCHING");

        // Simulación de los pasos de una conexión global real
        Timer timer = new Timer(800, null);
        final int[] step = {0};
        timer.addActionListener(e -> {
            step[0]++;
            switch(step[0]) {
                case 1 -> comboGlobalId.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Buscando en servidor de señales...");
                case 2 -> comboGlobalId.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Realizando NAT Traversal (Hole Punching)...");
                case 3 -> {
                    timer.stop();
                    completeConnection();
                }
            }
        });
        timer.start();
    }

    private void completeConnection() {
        centerLayout.show(centerCardPanel, "CONNECTED");
        btnConnect.setText("CORTAR VÍNCULO");
        btnConnect.setEnabled(true);
        btnConnect.setBackground(new Color(192, 57, 43));

        // Simular latencia de red WAN (más alta que local)
        new Timer(1000, e -> {
            int lat = new Random().nextInt(150) + 40; // 40ms a 190ms (realista para internet)
            lblLatency.setText(lat + " ms");
            lblLinkPath.setText("Local ──󰁔 " + (lat < 100 ? "DIRECT" : "RELAY") + " 󰁔 Remote");
        }).start();
    }

    private void resetView() {
        centerLayout.show(centerCardPanel, "IDLE");
        btnConnect.setText("ESTABLECER VÍNCULO");
        btnConnect.setBackground(GLOBAL_BLUE);
        comboGlobalId.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Introduce BitBridge ID...");
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        JFrame f = new JFrame("BitBridge Global Node");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(1100, 140);
        f.add(new GlobalConnectionHeader());
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
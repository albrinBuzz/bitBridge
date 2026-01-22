

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import org.bitBridge.view.core.ConnectionState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class HybridBridgeHeader extends JPanel {

    // Colores Pro
    private static final Color ACCENT_BLUE = new Color(0, 122, 255);
    private static final Color LAN_PURPLE = new Color(155, 89, 182);

    // Componentes
    private JTextField txtTarget;
    private JLabel lblLinkStatus, lblNetworkType, lblEncryption;
    private JButton btnConnect;
    private JCheckBox chkProMode;
    private JPanel advancedPanel;

    public HybridBridgeHeader() {
        setLayout(new BorderLayout(20, 0));
        setBackground(new Color(20, 20, 20));
        setBorder(new EmptyBorder(15, 25, 15, 25));

        initSimpleUI();
        initAdvancedUI();
        setupLogic();
    }

    private void initSimpleUI() {
        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftGroup.setOpaque(false);

        JLabel lblLogo = new JLabel("󰒄"); // Icono Bridge
        lblLogo.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 24));
        lblLogo.setForeground(ACCENT_BLUE);

        txtTarget = new JTextField(15);
        txtTarget.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Introduce ID o IP...");
        txtTarget.putClientProperty(FlatClientProperties.STYLE, "arc: 15; background: #2b2b2b");

        btnConnect = new JButton("CONECTAR");
        btnConnect.putClientProperty(FlatClientProperties.STYLE, "arc: 15; background: #007aff; foreground: #ffffff; borderWidth: 0");

        leftGroup.add(lblLogo);
        leftGroup.add(txtTarget);
        leftGroup.add(btnConnect);
        add(leftGroup, BorderLayout.WEST);
    }

    private void initAdvancedUI() {
        advancedPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        advancedPanel.setOpaque(false);
        advancedPanel.setVisible(false); // Oculto por defecto

        lblNetworkType = new JLabel("MODO: LAN");
        lblNetworkType.setForeground(LAN_PURPLE);

        lblEncryption = new JLabel("TLS 1.3");
        lblEncryption.setFont(new Font("Monospaced", Font.PLAIN, 10));

        JComboBox<String> comboProtocol = new JComboBox<>(new String[]{"Auto-Select", "UDP (Fast)", "TCP (Stable)"});
        comboProtocol.putClientProperty(FlatClientProperties.STYLE, "arc: 10; font: 10");

        advancedPanel.add(lblNetworkType);
        advancedPanel.add(new JSeparator(JSeparator.VERTICAL));
        advancedPanel.add(lblEncryption);
        advancedPanel.add(comboProtocol);

        // Checkbox para activar modo Pro
        chkProMode = new JCheckBox("PRO");
        chkProMode.setOpaque(false);

        JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightGroup.setOpaque(false);
        rightGroup.add(advancedPanel);
        rightGroup.add(chkProMode);

        add(rightGroup, BorderLayout.EAST);
    }

    private void setupLogic() {
        chkProMode.addActionListener(e -> {
            advancedPanel.setVisible(chkProMode.isSelected());
            revalidate();
            repaint();
        });

        btnConnect.addActionListener(e -> {
            String input = txtTarget.getText();
            if (input.contains(".")) {
                lblNetworkType.setText("MODO: LAN (IP)");
                lblNetworkType.setForeground(LAN_PURPLE);
            } else {
                lblNetworkType.setText("MODO: WAN (P2P)");
                lblNetworkType.setForeground(ACCENT_BLUE);
            }
            simulateConnection();
        });
    }

    private void simulateConnection() {
        btnConnect.setEnabled(false);
        txtTarget.putClientProperty(FlatClientProperties.OUTLINE, "warning");

        Timer t = new Timer(1500, e -> {
            btnConnect.setEnabled(true);
            btnConnect.setText("DESCONECTAR");
            btnConnect.setBackground(new Color(231, 76, 60));
            txtTarget.putClientProperty(FlatClientProperties.OUTLINE, "success");
        });
        t.setRepeats(false);
        t.start();
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        JFrame f = new JFrame("BitBridge Hybrid Engine");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new HybridBridgeHeader());
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
package org.bitBridge.Tests.tls;

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import org.bitBridge.shared.core.secure.CryptoGeneratorController;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class SecurityGeneratorSwingView extends JFrame {

    private final JTextField txtRutaDestino;
    private final JPasswordField txtPassword;
    private final JTextField txtCN;
    private final JTextField txtOrganizacion;
    private final JSpinner spinnerValidez;
    private final JPanel panelInterfaces;
    private final JButton btnGenerar;
    private final JTextArea txtConsoleLog;
    private final JProgressBar progressBar;

    private final List<JCheckBox> checkBoxesIPs = new ArrayList<>();
    private final CryptoGeneratorController controller;

    public SecurityGeneratorSwingView(CryptoGeneratorController controller) {
        this.controller = controller;

        setTitle("🔒 BitBridge - Inicializador Criptográfico");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(650, 680);
        setLocationRelativeTo(null);

        Color bgOscuro = new Color(43, 43, 43);
        Color bgConsola = new Color(30, 30, 30);
        Color fgTexto = new Color(223, 223, 223);
        Color fgLogs = new Color(169, 183, 198);
        Color btnColor = new Color(0, 122, 204);

        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        root.setBackground(bgOscuro);
        setContentPane(root);

        JLabel lblTitulo = new JLabel("🔒 CONFIGURACIÓN Y GENERACIÓN CRIPTOGRÁFICA DE BITBRIDGE");
        lblTitulo.setFont(new Font("Monospaced", Font.BOLD, 15));
        lblTitulo.setForeground(Color.WHITE);
        root.add(lblTitulo, BorderLayout.NORTH);

        JPanel panelCentral = new JPanel();
        panelCentral.setLayout(new BoxLayout(panelCentral, BoxLayout.Y_AXIS));
        panelCentral.setBackground(bgOscuro);

        JPanel gridForm = new JPanel(new GridBagLayout());
        gridForm.setBackground(bgOscuro);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        gridForm.add(createSectionLabel("Rutas de Destino:"), gbc);

        gbc.gridwidth = 1; gbc.gridy = 1; gbc.gridx = 0;
        gridForm.add(createLabel("Carpeta Base:", fgTexto), gbc);

        txtRutaDestino = new JTextField("./build/test_certs");
        txtRutaDestino.setPreferredSize(new Dimension(300, 26));
        gbc.gridx = 1;
        gridForm.add(txtRutaDestino, gbc);

        JButton btnBuscar = new JButton("Examinar...");
        btnBuscar.addActionListener(e -> seleccionarCarpeta());
        gbc.gridx = 2;
        gridForm.add(btnBuscar, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        gridForm.add(createLabel("Clave Keystore:", fgTexto), gbc);

        txtPassword = new JPasswordField("123");
        gbc.gridx = 1; gbc.gridwidth = 2;
        gridForm.add(txtPassword, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        gridForm.add(Box.createVerticalStrut(10), gbc);

        gbc.gridy = 4;
        gridForm.add(createSectionLabel("Parámetros del Certificado (X.509):"), gbc);

        gbc.gridwidth = 1; gbc.gridy = 5; gbc.gridx = 0;
        gridForm.add(createLabel("Sujeto (CN):", fgTexto), gbc);
        txtCN = new JTextField("BitBridgeLocalNode");
        gbc.gridx = 1; gbc.gridwidth = 2;
        gridForm.add(txtCN, gbc);

        gbc.gridwidth = 1; gbc.gridy = 6; gbc.gridx = 0;
        gridForm.add(createLabel("Organización:", fgTexto), gbc);
        txtOrganizacion = new JTextField("BitBridgeBeta");
        gbc.gridx = 1; gbc.gridwidth = 2;
        gridForm.add(txtOrganizacion, gbc);

        gbc.gridwidth = 1; gbc.gridy = 7; gbc.gridx = 0;
        gridForm.add(createLabel("Validez:", fgTexto), gbc);

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(365, 1, 3650, 1);
        spinnerValidez = new JSpinner(spinnerModel);
        JPanel panelSpinner = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelSpinner.setBackground(bgOscuro);
        panelSpinner.add(spinnerValidez);
        panelSpinner.add(createLabel("  días", fgTexto));
        gbc.gridx = 1; gbc.gridwidth = 2;
        gridForm.add(panelSpinner, gbc);

        panelCentral.add(gridForm);
        panelCentral.add(Box.createVerticalStrut(15));

        JPanel panelSanHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panelSanHeader.setBackground(bgOscuro);
        panelSanHeader.add(createSectionLabel("Interfaces de Red Detectadas de forma dinámica (SAN):"));
        panelCentral.add(panelSanHeader);
        panelCentral.add(Box.createVerticalStrut(5));

        panelInterfaces = new JPanel();
        panelInterfaces.setLayout(new BoxLayout(panelInterfaces, BoxLayout.Y_AXIS));
        panelInterfaces.setBackground(bgConsola);
        panelInterfaces.setBorder(new LineBorder(new Color(60, 63, 65), 1));

        detectarInterfacesDeRed(fgTexto, bgConsola);

        JScrollPane scrollInterfaces = new JScrollPane(panelInterfaces);
        scrollInterfaces.setPreferredSize(new Dimension(500, 100));
        scrollInterfaces.setMaximumSize(new Dimension(Short.MAX_VALUE, 100));
        panelCentral.add(scrollInterfaces);

        root.add(panelCentral, BorderLayout.CENTER);

        JPanel panelInferior = new JPanel(new BorderLayout(10, 10));
        panelInferior.setBackground(bgOscuro);

        JPanel panelAccion = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        panelAccion.setBackground(bgOscuro);

        btnGenerar = new JButton("🚀 GENERAR ENTORNO SEGURO");
        btnGenerar.setBackground(btnColor);
        btnGenerar.setForeground(Color.WHITE);
        btnGenerar.setFont(new Font("Arial", Font.BOLD, 13));
        btnGenerar.setFocusPainted(false);
        btnGenerar.addActionListener(e -> despacharGeneracionAlControlador());

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(150, 22));

        panelAccion.add(btnGenerar);
        panelAccion.add(Box.createHorizontalStrut(20));
        panelAccion.add(progressBar);
        panelInferior.add(panelAccion, BorderLayout.NORTH);

        JPanel panelLogHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelLogHeader.setBackground(bgOscuro);
        panelLogHeader.add(createLabel("📄 Consola de Auditoría Log:", fgTexto));

        JPanel panelLogCompleto = new JPanel(new BorderLayout(5, 5));
        panelLogCompleto.setBackground(bgOscuro);
        panelLogCompleto.add(panelLogHeader, BorderLayout.NORTH);

        txtConsoleLog = new JTextArea();
        txtConsoleLog.setEditable(false);
        txtConsoleLog.setBackground(bgConsola);
        txtConsoleLog.setForeground(fgLogs);
        txtConsoleLog.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollLogs = new JScrollPane(txtConsoleLog);
        scrollLogs.setPreferredSize(new Dimension(500, 160));
        panelLogCompleto.add(scrollLogs, BorderLayout.CENTER);

        panelInferior.add(panelLogCompleto, BorderLayout.CENTER);
        root.add(panelInferior, BorderLayout.SOUTH);
    }

    private JLabel createLabel(String texto, Color color) {
        JLabel label = new JLabel(texto);
        label.setForeground(color);
        label.setFont(new Font("Arial", Font.BOLD, 12));
        return label;
    }

    private JLabel createSectionLabel(String texto) {
        JLabel label = new JLabel(texto);
        label.setForeground(new Color(78, 178, 255));
        label.setFont(new Font("Arial", Font.BOLD, 13));
        return label;
    }

    private void detectarInterfacesDeRed(Color fgColor, Color bgColor) {
        try {
            agregarCheckboxIP("localhost (127.0.0.1)", "127.0.0.1", fgColor, bgColor);
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> direcciones = iface.getInetAddresses();
                while (direcciones.hasMoreElements()) {
                    InetAddress addr = direcciones.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        agregarCheckboxIP(String.format("%s (%s)", iface.getName(), addr.getHostAddress()), addr.getHostAddress(), fgColor, bgColor);
                    }
                }
            }
        } catch (Exception e) {
            txtConsoleLog.append("⚠️ Error detectando interfaces: " + e.getMessage() + "\n");
        }
    }

    private void agregarCheckboxIP(String labelText, String ip, Color fg, Color bg) {
        JCheckBox chk = new JCheckBox(labelText, true);
        chk.setForeground(fg);
        chk.setBackground(bg);
        chk.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chk.setName(ip);
        panelInterfaces.add(chk);
        checkBoxesIPs.add(chk);
    }

    private void seleccionarCarpeta() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtRutaDestino.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Captura los campos de la GUI y delega la ejecución real al controlador
     * usando SwingWorker para mantener desacoplada la lógica criptográfica pesada.
     */
    private void despacharGeneracionAlControlador() {
        String carpetaBase = txtRutaDestino.getText().trim();
        String password = new String(txtPassword.getPassword());
        String commonName = txtCN.getText().trim();
        String organizacion = txtOrganizacion.getText().trim();
        int diasValidez = (Integer) spinnerValidez.getValue();

        List<String> ipsSeleccionadas = new ArrayList<>();
        for (JCheckBox chk : checkBoxesIPs) {
            if (chk.isSelected()) ipsSeleccionadas.add(chk.getName());
        }

        btnGenerar.setEnabled(false);
        progressBar.setVisible(true);

        // Orquestación del worker asíncrono que consume el controlador modular
        SwingWorker<Void, String> taskWorker = new SwingWorker<>() {
            private boolean success = false;

            @Override
            protected Void doInBackground() {
                controller.procesarGeneracion(carpetaBase, password, commonName, organizacion, diasValidez, ipsSeleccionadas,
                        new CryptoGeneratorController.CryptoExecutionListener() {
                            @Override
                            public void onProgress(String message) {
                                publish(message);
                            }

                            @Override
                            public void onSuccess(String keystorePath, String certPath) {
                                success = true;
                                publish("✅ ¡Entorno de llaves y certificados creado con éxito!");
                                publish("📦 Contenedor PKCS12 -> " + keystorePath);
                                publish("📜 Certificado Público -> " + certPath + "\n");
                            }

                            @Override
                            public void onError(String errorMessage, Throwable cause) {
                                success = false;
                                publish("❌ Fallo crítico al generar: " + errorMessage);
                            }
                        }
                );
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String log : chunks) {
                    txtConsoleLog.append(log + "\n");
                }
            }

            @Override
            protected void done() {
                btnGenerar.setEnabled(true);
                progressBar.setVisible(false);
            }
        };

        taskWorker.execute();
    }
}
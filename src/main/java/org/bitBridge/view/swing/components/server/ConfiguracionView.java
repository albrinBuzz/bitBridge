package org.bitBridge.view.swing.components.server;

import org.bitBridge.server.ConfiguracionServidor;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class ConfiguracionView extends JDialog {
    private final ConfiguracionServidor config = ConfiguracionServidor.getInstancia();

    private JTextField txtNombre, txtPuertoServidor, txtRutaDescargas;
    private final Color COLOR_PRIMARIO = new Color(0, 191, 255); // Deep Sky Blue
    private final Color COLOR_FONDO = new Color(30, 33, 37);

    public ConfiguracionView(Frame parent) {
        super(parent, "Ajustes de BitBridge", true);
        setSize(480, 550);
        setLocationRelativeTo(parent);
        setResizable(false);

        getContentPane().setBackground(COLOR_FONDO);
        setLayout(new BorderLayout());

        initUI();
    }

    private void initUI() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(25, 30, 25, 30));

        // --- 1. SECCIÓN SERVIDOR Y RED ---
        content.add(createSectionTitle("⚙️ CONFIGURACIÓN DEL SERVIDOR"));

        content.add(new JLabel("<html><font color='#adb5bd'>Nombre del Servidor (Nickname):</font></html>"));
        txtNombre = createStyledField(config.obtener("servidor.nombre", "Servidor_Principal"));
        content.add(txtNombre);

        content.add(Box.createRigidArea(new Dimension(0, 15)));

        // --- SUB-SECCIÓN PUERTO CON ADVERTENCIA ---
        content.add(new JLabel("<html><font color='#adb5bd'>Puerto de Escucha:</font></html>"));

        JPanel portPanel = new JPanel(new BorderLayout(10, 0));
        portPanel.setOpaque(false);

        txtPuertoServidor = createStyledField(config.obtener("servidor.puerto", "8080"));
        txtPuertoServidor.setEnabled(false); // Bloqueado por seguridad

        JButton btnUnlock = new JButton("🔓");
        btnUnlock.setToolTipText("Cambiar puerto (No recomendado)");
        btnUnlock.setBackground(new Color(60, 63, 65));
        btnUnlock.setForeground(Color.WHITE);
        btnUnlock.addActionListener(e -> advertirCambioPuerto());

        portPanel.add(txtPuertoServidor, BorderLayout.CENTER);
        portPanel.add(btnUnlock, BorderLayout.EAST);
        content.add(portPanel);

        JLabel lblPortInfo = new JLabel("<html><body style='width: 350px;'><font color='#e67e22' size='2'>" +
                "⚠️ El puerto 8080 está autorizado en el Firewall. Cambiarlo puede romper la conexión local." +
                "</font></body></html>");
        lblPortInfo.setBorder(new EmptyBorder(5, 0, 0, 0));
        content.add(lblPortInfo);

        content.add(Box.createRigidArea(new Dimension(0, 25)));

        // --- 2. SECCIÓN ALMACENAMIENTO ---
        content.add(createSectionTitle("📁 ALMACENAMIENTO"));

        content.add(new JLabel("<html><font color='#adb5bd'>Carpeta de Descargas:</font></html>"));
        JPanel pathPanel = new JPanel(new BorderLayout(10, 0));
        pathPanel.setOpaque(false);

        txtRutaDescargas = createStyledField(config.obtener("cliente.directorio_descargas"));
        txtRutaDescargas.setEditable(false);

        JButton btnBrowse = new JButton("Cambiar");
        btnBrowse.setBackground(new Color(60, 63, 65));
        btnBrowse.setForeground(Color.WHITE);
        btnBrowse.addActionListener(e -> selectFolder());

        pathPanel.add(txtRutaDescargas, BorderLayout.CENTER);
        pathPanel.add(btnBrowse, BorderLayout.EAST);
        content.add(pathPanel);

        // --- PANEL DE BOTONES (FOOTER) ---
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 10, 20, 20));

        JButton btnCancelar = new JButton("CANCELAR");
        btnCancelar.addActionListener(e -> dispose());

        JButton btnGuardar = new JButton("GUARDAR CAMBIOS");
        btnGuardar.setBackground(COLOR_PRIMARIO);
        btnGuardar.setForeground(Color.BLACK);
        btnGuardar.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnGuardar.addActionListener(e -> saveSettings());

        footer.add(btnCancelar);
        footer.add(btnGuardar);

        add(new JScrollPane(content), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private void advertirCambioPuerto() {
        String mssg = "<html><body style='width: 350px;'>" +
                "<h3 style='color:red;'>¡Atención! Cambio de Puerto Crítico</h3>" +
                "<p>El puerto <b>8080</b> fue configurado automáticamente durante la instalación.</p>" +
                "<p>Si lo cambia:</p>" +
                "<ul>" +
                "<li>Deberá configurar manualmente su <b>Firewall</b>.</li>" +
                "<li>La aplicación podría dejar de funcionar correctamente.</li>" +
                "</ul>" +
                "<p>¿Desea desbloquear el campo bajo su propia responsabilidad?</p>" +
                "</body></html>";

        int resp = JOptionPane.showConfirmDialog(this, mssg, "Advertencia Técnica",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (resp == JOptionPane.YES_OPTION) {
            txtPuertoServidor.setEnabled(true);
            txtPuertoServidor.requestFocus();
        }
    }

    private void saveSettings() {
        String nuevoNombre = txtNombre.getText().trim();
        String nuevoPuerto = txtPuertoServidor.getText().trim();
        String nuevaRuta = txtRutaDescargas.getText().trim();

        if (nuevoNombre.isEmpty() || nuevoPuerto.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El nombre y el puerto no pueden estar vacíos.");
            return;
        }

        // Actualizar el objeto de configuración (Requiere métodos setter en tu clase)
         config.setProperty("servidor.nombre", nuevoNombre);
         config.setProperty("servidor.puerto", nuevoPuerto);
         config.setProperty("cliente.directorio_descargas", nuevaRuta);

        config.guardarEnArchivo();
        JOptionPane.showMessageDialog(this, "Configuración guardada. Reinicie el servidor para aplicar.");
        dispose();
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtRutaDescargas.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private JLabel createSectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(COLOR_PRIMARIO);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        l.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(70, 70, 70)));
        l.setMaximumSize(new Dimension(500, 30));
        return l;
    }

    private JTextField createStyledField(String text) {
        JTextField f = new JTextField(text);
        f.setMaximumSize(new Dimension(500, 35));
        f.setBackground(new Color(45, 48, 54));
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(75, 75, 75)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        return f;
    }
}
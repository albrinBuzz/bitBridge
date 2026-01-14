package org.bitBridge.web.serverGui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.bitBridge.shared.Logger;
import org.bitBridge.web.MainWeb;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

public class ServidorLauncher extends JFrame {

    private ConfigurableApplicationContext springContext;
    private JButton btnAccion, btnAbrirWeb;
    private JLabel lblStatus, lblIp, lblQr;
    private JProgressBar progressBar;
    private int puertoActual = 8081;
    private String urlServidorActiva = "";

    // Paleta de Colores Mejorada (Más contraste)
    private final Color COLOR_FONDO = new Color(33, 37, 41);    // Gris carbón limpio
    private final Color COLOR_TARJETA = new Color(52, 58, 64);  // Gris acero claro
    private final Color ACCENT_COLOR = new Color(0, 191, 255);  // Deep Sky Blue
    private final Color COLOR_EXITO = new Color(40, 167, 69);   // Verde Bootstrap
    private final Color COLOR_TEXTO_PRIMARIO = Color.WHITE;
    private final Color COLOR_TEXTO_SECUNDARIO = new Color(173, 181, 189);

    public ServidorLauncher() {
        configureTheme();
        configurarVentana();
        inicializarComponentes();
        setupShutdownHook();
    }

    private void configureTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Button.arc", 20);
            UIManager.put("Component.arc", 20);
            UIManager.put("ProgressBar.arc", 20);
            // Aumentar un poco el brillo de los paneles de FlatLaf
            UIManager.put("Panel.background", COLOR_FONDO);
        } catch (Exception ignored) {}
    }

    private void configurarVentana() {
        setTitle("FileTalk | Web Bridge");
        setSize(480, 720);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cerrarPanelSeguro();
            }
        });
    }

    private void inicializarComponentes() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(COLOR_FONDO);

        // --- 1. CABECERA (Más brillante) ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(43, 48, 53)); // Un gris un poco más claro que el fondo
        headerPanel.setBorder(new EmptyBorder(30, 35, 25, 35));

        JLabel mainTitle = new JLabel("📱 ENLACE MÓVIL");
        mainTitle.setFont(new Font("SansSerif", Font.BOLD, 26));
        mainTitle.setForeground(ACCENT_COLOR);

        JLabel subTitle = new JLabel("<html>Conecta tu teléfono escaneando el código QR.<br>Asegúrate de estar en la misma red Wi-Fi.</html>");
        subTitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subTitle.setForeground(COLOR_TEXTO_SECUNDARIO);

        headerPanel.add(mainTitle, BorderLayout.NORTH);
        headerPanel.add(subTitle, BorderLayout.CENTER);

        // --- 2. CUERPO (Layout Espaciado) ---
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(10, 35, 10, 35));

        // Tarjetas informativas con bordes más definidos
        JPanel explainerCard = new JPanel(new GridLayout(1, 2, 20, 0));
        explainerCard.setOpaque(false);
        explainerCard.setMaximumSize(new Dimension(420, 100));
        explainerCard.add(createUserFriendlyCard("PC A PC", "Red de alta velocidad.", "💻"));
        explainerCard.add(createUserFriendlyCard("WEB / MÓVIL", "Sin instalar apps.", "✨"));

        lblStatus = new JLabel("● ESTADO: LISTO PARA CONECTAR");
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblStatus.setForeground(COLOR_TEXTO_SECUNDARIO);
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 13));

        progressBar = new JProgressBar();
        progressBar.setMaximumSize(new Dimension(380, 12));
        progressBar.setForeground(ACCENT_COLOR);
        progressBar.setBackground(new Color(60, 60, 60));
        progressBar.setStringPainted(false);

        // MARCO DEL QR (Brillo y Contraste)
        JPanel qrFrame = new JPanel(new GridBagLayout());
        qrFrame.setOpaque(true);
        qrFrame.setBackground(new Color(255, 255, 255, 10)); // Sutil transparencia blanca
        qrFrame.setBorder(new LineBorder(new Color(255, 255, 255, 30), 1, true));
        qrFrame.setMaximumSize(new Dimension(280, 280));

        lblQr = new JLabel("ESPERANDO...", SwingConstants.CENTER);
        lblQr.setPreferredSize(new Dimension(240, 240));
        lblQr.setOpaque(true);
        lblQr.setBackground(Color.WHITE);
        lblQr.setBorder(new LineBorder(ACCENT_COLOR, 3));
        qrFrame.add(lblQr);

        lblIp = new JLabel("Configura el acceso vía QR");
        lblIp.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblIp.setFont(new Font("Monospaced", Font.BOLD, 14));
        lblIp.setForeground(ACCENT_COLOR);
        lblIp.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Listener para Copiar IP
        lblIp.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if(!urlServidorActiva.isEmpty()) {
                    java.awt.datatransfer.StringSelection ss = new java.awt.datatransfer.StringSelection(urlServidorActiva);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
                    lblIp.setText("¡URL COPIADA AL PORTAPAPELES!");
                    new Timer(2000, e -> lblIp.setText(urlServidorActiva)).start();
                }
            }
        });

        // Construcción del cuerpo
        body.add(Box.createRigidArea(new Dimension(0, 20)));
        body.add(explainerCard);
        body.add(Box.createRigidArea(new Dimension(0, 30)));
        body.add(lblStatus);
        body.add(Box.createRigidArea(new Dimension(0, 10)));
        body.add(progressBar);
        body.add(Box.createRigidArea(new Dimension(0, 30)));
        body.add(qrFrame);
        body.add(Box.createRigidArea(new Dimension(0, 20)));
        body.add(lblIp);

        // --- 3. FOOTER (Botones Grandes) ---
        JPanel footer = new JPanel(new GridLayout(1, 2, 20, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(25, 35, 35, 35));

        btnAccion = new JButton("ACTIVAR ENLACE");
        btnAccion.setFocusPainted(false);
        btnAccion.setBackground(ACCENT_COLOR);
        btnAccion.setForeground(Color.BLACK);
        btnAccion.setFont(new Font("SansSerif", Font.BOLD, 14));

        btnAbrirWeb = new JButton("PROBAR EN PC");
        btnAbrirWeb.setFocusPainted(false);
        btnAbrirWeb.setEnabled(false);

        btnAccion.addActionListener(e -> toggleServidor());
        btnAbrirWeb.addActionListener(e -> abrirNavegador(urlServidorActiva));

        footer.add(btnAccion);
        footer.add(btnAbrirWeb);

        container.add(headerPanel, BorderLayout.NORTH);
        container.add(body, BorderLayout.CENTER);
        container.add(footer, BorderLayout.SOUTH);

        add(container);
    }

    private JPanel createUserFriendlyCard(String title, String desc, String icon) {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBackground(COLOR_TARJETA);
        p.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel t = new JLabel(icon + " " + title);
        t.setFont(new Font("SansSerif", Font.BOLD, 12));
        t.setForeground(ACCENT_COLOR);

        JTextArea d = new JTextArea(desc);
        d.setFont(new Font("SansSerif", Font.PLAIN, 11));
        d.setForeground(COLOR_TEXTO_SECUNDARIO);
        d.setEditable(false);
        d.setOpaque(false);
        d.setLineWrap(true);
        d.setWrapStyleWord(true);

        p.add(t, BorderLayout.NORTH);
        p.add(d, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1, true));
        return p;
    }

    // --- LÓGICA DE CONTROL ---
    private boolean esPuertoDisponible(int puerto) {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(puerto)) {
            ss.setReuseAddress(true);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private void iniciar(int puertoAIntentar) {
        if (!esPuertoDisponible(puertoAIntentar)) {
            SwingUtilities.invokeLater(() -> {
                mostrarPanelAyudaPuerto(puertoAIntentar);
                resetearUI();
            });
            return;
        }

        btnAccion.setEnabled(false);
        progressBar.setIndeterminate(true);
        lblStatus.setText("● Enciendo El Servidor Web...");
        lblStatus.setForeground(Color.YELLOW);

        new Thread(() -> {
            try {
                System.setProperty("server.port", String.valueOf(puertoAIntentar));
                springContext = new SpringApplicationBuilder(MainWeb.class)
                        .properties("server.port=" + puertoAIntentar)
                        .headless(false)
                        .run();

                String ip = InetAddress.getLocalHost().getHostAddress();
                String urlFinal = "http://" + ip + ":" + puertoAIntentar + "/home/index.xhtml";
                urlServidorActiva = urlFinal;

                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("● ENLACE ACTIVO Y SEGURO");
                    lblStatus.setForeground(COLOR_EXITO);
                    lblIp.setText(urlFinal);
                    generarQR(urlFinal);

                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);

                    btnAccion.setText("DESACTIVAR");
                    btnAccion.setBackground(new Color(220, 53, 69)); // Rojo Bootstrap
                    btnAccion.setForeground(Color.WHITE);
                    btnAccion.setEnabled(true);
                    btnAbrirWeb.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    detenerServidor(false);

                    Logger.logWarn(e.getMessage());
                    // --- Análisis de Error Mejorado ---
                    String mensajeError = "No se pudo iniciar el puente web.";
                    String sugerencia = "\n\nSugerencia: ";

                    if (e.getMessage().contains("Port already in use") || e.toString().contains("BindException")) {
                        mensajeError = "EL PUERTO " + puertoAIntentar + " ESTÁ OCUPADO";
                        sugerencia += "Cierra otras aplicaciones o intenta con el puerto 8082.";
                    } else if (e.toString().contains("Permission denied")) {
                        mensajeError = "PERMISO DE SISTEMA DENEGADO";
                        sugerencia += "Ejecuta los comandos de Firewall que configuramos o usa un puerto superior al 1024.";
                    } else {
                        sugerencia += "Asegúrate de tener conexión a la red local.";
                    }

                    lblStatus.setText("● FALLO AL INICIAR");
                    lblStatus.setForeground(new Color(220, 53, 69));

                    // Mostramos un diálogo más profesional y útil
                    JOptionPane.showMessageDialog(this,
                            "⚠️ " + mensajeError + sugerencia + "\nDetalles: " + e.getLocalizedMessage(),
                            "Error de Red - BitBridge",
                            JOptionPane.ERROR_MESSAGE);

                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    btnAccion.setEnabled(true);
                });
            }
        }).start();
    }

    private void detenerServidor(boolean cerrarVentanaAlFinal) {
        btnAccion.setEnabled(false);
        progressBar.setIndeterminate(true);
        new Thread(() -> {
            if (springContext != null) {
                springContext.close();
                springContext = null;
            }
            SwingUtilities.invokeLater(() -> {
                if (cerrarVentanaAlFinal) {
                    this.dispose();
                } else {
                    resetearUI();
                }
            });
        }).start();
    }

    private void resetearUI() {
        lblStatus.setText("● ESTADO: LISTO PARA CONECTAR");
        lblStatus.setForeground(COLOR_TEXTO_SECUNDARIO);
        lblIp.setText("Configura el acceso vía QR");
        urlServidorActiva = "";
        lblQr.setIcon(null);
        lblQr.setText("ESPERANDO...");
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        btnAccion.setText("ACTIVAR ENLACE");
        btnAccion.setBackground(ACCENT_COLOR);
        btnAccion.setForeground(Color.BLACK);
        btnAccion.setEnabled(true);
        btnAbrirWeb.setEnabled(false);
    }

    private void toggleServidor() {
        if (springContext == null) {
            iniciar(puertoActual);
        } else {
            int respuesta = JOptionPane.showConfirmDialog(this,
                    "¿Deseas cerrar el enlace con tu dispositivo móvil?",
                    "Confirmar desconexión", JOptionPane.YES_NO_OPTION);
            if (respuesta == JOptionPane.YES_OPTION) {
                detenerServidor(false);
            }
        }
    }

    private void generarQR(String data) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 240, 240);
            lblQr.setText("");
            lblQr.setIcon(new ImageIcon(MatrixToImageWriter.toBufferedImage(bitMatrix)));
        } catch (Exception e) {
            lblQr.setText("Error QR");
        }
    }

    private void abrirNavegador(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec("xdg-open " + url);
            }
        } catch (Exception ignored) {}
    }

    private void cerrarPanelSeguro() {
        if (springContext == null) {
            this.dispose();
            return;
        }
        int seleccion = JOptionPane.showConfirmDialog(this, "¿Detener el puente web y salir?", "Salir", JOptionPane.YES_NO_OPTION);
        if (seleccion == JOptionPane.YES_OPTION) {
            detenerServidor(true);
        }
    }

    private void mostrarPanelAyudaPuerto(int puerto) {
        String mensaje = "<html>" +
                "<body style='width: 300px; font-family: sans-serif;'>" +
                "<h2 style='color: #e74c3c;'>Puerto " + puerto + " bloqueado</h2>" +
                "<p>No se pudo iniciar el servidor web porque el puerto ya está en uso.</p>" +
                "<hr>" +
                "<b>Causas probables y soluciones:</b>" +
                "<ul>" +
                "<li><b>Otra instancia:</b> Verifica si ya tienes otra ventana de BitBridge abierta.</li>" +
                "<li><b>Servicio en conflicto:</b> Alguna aplicación (como McAfee, Docker o servidores dev) está usando este puerto.</li>" +
                "<li><b>Proceso huérfano:</b> A veces Java no se cierra bien. Intenta reiniciar la aplicación.</li>" +
                "</ul>" +
                "<p style='background: #34495e; padding: 5px; color: white;'>" +
                "<b>Tip para expertos:</b><br>" +
                "En consola usa: <br><code>netstat -ano | findstr :" + puerto + "</code> (Windows)<br>" +
                "<code>sudo lsof -i :" + puerto + "</code> (Linux)" +
                "</p>" +
                "</body></html>";

        JOptionPane.showMessageDialog(this, mensaje, "Conflicto de Red Detectado", JOptionPane.WARNING_MESSAGE);
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (springContext != null && springContext.isActive()) springContext.close();
        }));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServidorLauncher().setVisible(true));
    }
}
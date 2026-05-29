package org.bitBridge.Tests.Gui;

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class TrayExample {

    public static void main(String[] args) {
        FlatOneDarkIJTheme.setup();

        // 1. Crear el menú (Usamos JPopupMenu para que se vea moderno con FlatLaf)
        JPopupMenu swingMenu = createModernMenu();

        // 2. Intentar usar el SystemTray nativo
        if (SystemTray.isSupported()) {
            setupNativeTray(swingMenu);
        } else {
            // 3. FALLBACK: Si estamos en Wayland/Sway, creamos un mini-widget
            setupFloatingWidget(swingMenu);
        }
    }

    private static void setupNativeTray(JPopupMenu swingMenu) {
        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image iconImg = createStatusIcon(Color.RED);
            TrayIcon trayIcon = new TrayIcon(iconImg, "Soporte Activo");
            trayIcon.setImageAutoSize(true);

            // Listener para abrir el menú de Swing con click derecho
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                        swingMenu.setLocation(e.getX(), e.getY());
                        swingMenu.setInvoker(swingMenu);
                        swingMenu.setVisible(true);
                    }
                }
            });

            tray.add(trayIcon);
            trayIcon.displayMessage("Soporte Activo", "Ejecutándose en la bandeja.", TrayIcon.MessageType.INFO);
        } catch (Exception e) {
            setupFloatingWidget(swingMenu); // Si falla al agregar (común en Linux), fallback.
        }
    }

    private static void setupFloatingWidget(JPopupMenu swingMenu) {
        JFrame widget = new JFrame();
        widget.setUndecorated(true);
        widget.setAlwaysOnTop(true);
        widget.setSize(160, 40);
        widget.setLayout(new BorderLayout());

        // Panel con estilo One Dark
        JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        content.setBorder(BorderFactory.createLineBorder(new Color(82, 139, 255), 1));

        JLabel statusLabel = new JLabel("● Agente Activo");
        statusLabel.setForeground(new Color(152, 195, 121)); // Verde One Dark
        content.add(statusLabel);

        widget.add(content);

        // Click derecho para el menú
        content.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { check(e); }
            public void mouseReleased(MouseEvent e) { check(e); }
            private void check(MouseEvent e) {
                if (e.isPopupTrigger()) swingMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        // Posicionar en la esquina inferior derecha (encima de la barra de Sway)
        GraphicsConfiguration config = widget.getGraphicsConfiguration();
        Rectangle bounds = config.getBounds();
        widget.setLocation(bounds.width - 170, bounds.height - 80);

        widget.setVisible(true);
        System.out.println("⚠️ Modo Fallback: Widget flotante activado (Wayland/Sway)");
    }

    private static JPopupMenu createModernMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("Abrir Consola");
        JMenuItem aboutItem = new JMenuItem("Acerca de");
        JMenuItem exitItem = new JMenuItem("Salir del Agente");

        exitItem.setForeground(new Color(224, 108, 117)); // Color rojizo

        menu.add(openItem);
        menu.add(aboutItem);
        menu.addSeparator();
        menu.add(exitItem);

        exitItem.addActionListener(e -> System.exit(0));
        openItem.addActionListener(e -> JOptionPane.showMessageDialog(null, "Consola abierta."));

        return menu;
    }

    private static Image createStatusIcon(Color color) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillOval(2, 2, 12, 12);
        g2.dispose();
        return img;
    }
}
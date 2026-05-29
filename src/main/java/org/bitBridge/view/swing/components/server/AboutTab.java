package org.bitBridge.view.swing.components.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AboutTab extends JPanel {
    private final Color COLOR_ACCENTO = new Color(0, 191, 255);
    private final Color COLOR_FONDO = new Color(30, 33, 37);
    private final Color COLOR_BORDE = new Color(50, 55, 60);

    public AboutTab() {
        setBackground(COLOR_FONDO);
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(25, 25, 25, 25));

        initHeader();
        initContent();
        initFooter();
    }

    private void initHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("BITBRIDGE ENGINE");
        lblTitle.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 28));
        lblTitle.setForeground(COLOR_ACCENTO);

        JLabel lblSub = new JLabel("Infraestructura de Conectividad Inteligente | v2.6.0");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(Color.GRAY);

        headerPanel.add(lblTitle, BorderLayout.NORTH);
        headerPanel.add(lblSub, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);
    }

    private void initContent() {
        JEditorPane infoPane = new JEditorPane();
        infoPane.setContentType("text/html");
        infoPane.setEditable(false);
        infoPane.setOpaque(false);

        String html = "<html><body style='color: #CCCCCC; font-family: Segoe UI, sans-serif;'>" +

                // RESUMEN SUPERFICIAL
                "<b style='color: white;'>Versión:</b> 2.6.0 <span style='color: #00BFFF;'>[Stable Build]</span><br>" +
                "<b style='color: white;'>Build Date:</b> Febrero 2026<br>" +
                "<h4 style='color: white; margin-bottom: 2;'>¿QUÉ ES BITBRIDGE?</h4>" +
                "<p style='font-size: 12px; margin-top: 0;'>" +
                "BitBridge es una autopista digital de alta velocidad. Permite que múltiples dispositivos " +
                "se conecten y compartan información de forma simultánea, eficiente y robusta. " +
                "Está diseñado para soportar grandes cargas de trabajo sin ralentizar el sistema, " +
                "garantizando que tus datos lleguen siempre a su destino de la manera más rápida posible.</p>" +

                // SECCIÓN 2: BLINDAJE LEGAL (Basado en Apache 2.0 Cláusulas 7 y 8)
                "<h4 style='color: #e74c3c; margin-bottom: 2;'>AVISO LEGAL Y EXENCIÓN DE RESPONSABILIDAD</h4>" +
                "<p style='font-size: 10px; color: #ff7675; margin-top: 0; text-align: justify;'>" +
                "<b>LIMITACIÓN DE PERJUICIOS:</b> Este software se entrega 'TAL CUAL' (AS IS), sin garantías de ningún tipo. " +
                "En ningún caso el Autor será responsable por daños directos, indirectos, incidentales o especiales, incluyendo " +
                "pérdida de datos, lucro cesante o fallos en la infraestructura, derivados del uso o la incapacidad de uso de este motor, " +
                "incluso si se ha advertido de la posibilidad de tales daños.</p>" +

                "<p style='font-size: 10px; color: #888888;'>" +
                "Licensed under the Apache License, Version 2.0 (the 'License'). Usted no puede usar este archivo excepto en " +
                "cumplimiento con la Licencia. Copyright © 2026 [Tu Nombre Completo]. Todos los derechos reservados.</p>" +

                "<h4 style='color: #00BFFF; margin-bottom: 2;'>CARACTERÍSTICAS CLAVE</h4>" +
                "<ul style='font-size: 11px;'>" +
                "<li>Conexión masiva y fluida de usuarios.</li>" +
                "<li>Optimización extrema de memoria del PC.</li>" +
                "<li>Estabilidad garantizada bajo alta demanda.</li>" +
                "</ul>" +
                "</body></html>";

        infoPane.setText(html);

        JScrollPane scroll = new JScrollPane(infoPane);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, COLOR_BORDE));
        add(scroll, BorderLayout.CENTER);
    }

    private void initFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JLabel lblStatus = new JLabel("Estado del Motor: Sincronizado y Protegido");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblStatus.setForeground(new Color(85, 239, 196)); // Verde neón
        footer.add(lblStatus, BorderLayout.WEST);
        add(footer, BorderLayout.SOUTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, COLOR_FONDO, 0, getHeight(), new Color(20, 22, 25));
        g2d.setPaint(gp);
        g2d.fillRect(0, 0, getWidth(), getHeight());
    }
}
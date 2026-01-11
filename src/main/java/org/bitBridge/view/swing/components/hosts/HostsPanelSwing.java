package org.bitBridge.view.swing.components.hosts;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.HostsObserver;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class HostsPanelSwing extends JPanel implements HostsObserver {

    private final Client cliente;
    private final JPanel usuariosBox;
    private final JLabel usuariosTitle;
    private final JScrollPane usuariosScroll;

    // --- PALETA DE COLORES FIEL A TU APP ---
    private final Color COLOR_AZUL_OSCURO = new Color(52, 73, 94);   // El azul de tu connection-bar
    private final Color COLOR_FONDO_APP = new Color(30, 39, 46);     // El fondo oscuro del menú
    private final Color COLOR_ITEM_FONDO = new Color(45, 52, 54);    // Gris azulado para los items
    private final Color COLOR_BORDE = new Color(60, 63, 65);         // Borde sutil
    private final Color COLOR_TEXTO_TITULO = new Color(0, 191, 255); // DeepSkyBlue para acentos

    public HostsPanelSwing(Client cliente) {
        this.cliente = cliente;
        this.cliente.addHostOserver(this);

        // 1. Configuración del contenedor principal
        setLayout(new BorderLayout());
        setBackground(COLOR_FONDO_APP);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDE, 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // 2. Cabecera del Panel (Header con estilo azul oscuro)
        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        headerPanel.setOpaque(false);

        usuariosTitle = new JLabel("🧑‍💻 Usuarios Conectados (0)");
        usuariosTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        usuariosTitle.setForeground(Color.WHITE);

        JLabel titleLabel = new JLabel("Clientes Conectados");
        titleLabel.setForeground(COLOR_TEXTO_TITULO);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        headerPanel.add(usuariosTitle);
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        // 3. Contenedor de la lista (usuariosBox)
        usuariosBox = new JPanel();
        usuariosBox.setLayout(new BoxLayout(usuariosBox, BoxLayout.Y_AXIS));
        usuariosBox.setBackground(COLOR_FONDO_APP); // Fondo oscuro coherente

        usuariosScroll = new JScrollPane(usuariosBox);
        usuariosScroll.setBorder(null);
        usuariosScroll.setOpaque(false);
        usuariosScroll.getViewport().setOpaque(false);
        usuariosScroll.setPreferredSize(new Dimension(0, 200));

        // Mejorar velocidad de scroll
        usuariosScroll.getVerticalScrollBar().setUnitIncrement(12);

        add(usuariosScroll, BorderLayout.CENTER);
    }

    // ... dentro de HostsPanelSwing.java ...

    @Override
    public void updateAllHosts(List<ClientInfo> hostList) {
        if (hostList == null || hostList.isEmpty()) {
            mostrarEstadoVacio();
            return;
        }

        List<ClientInfo> hostsValidos = hostList.stream()
                .filter(this::esHostValido)
                .toList();

        SwingUtilities.invokeLater(() -> {
            // 1. Limpiar lista actual
            usuariosBox.removeAll();

            // 2. Título dinámico
            usuariosTitle.setText(String.format("🧑‍💻 Usuarios Conectados (%d)", hostsValidos.size()));

            // 3. Inyectar nuevos paneles especializados
            for (ClientInfo host : hostsValidos) {
                HostControlPanelSwing panel = new HostControlPanelSwing(host, this.cliente);
                usuariosBox.add(panel);
                usuariosBox.add(Box.createVerticalStrut(10)); // Margen entre tarjetas
            }

            // 4. Forzar refresco visual
            usuariosBox.revalidate();
            usuariosBox.repaint();

            // Reset scroll al inicio
            usuariosScroll.getVerticalScrollBar().setValue(0);
        });
    }



    private boolean esHostValido(ClientInfo host) {
        String nick = host.getNick();
        if (nick == null || nick.isEmpty()) return false;
        return !nick.equalsIgnoreCase("enviando") && !nick.chars().allMatch(Character::isDigit);
    }

    private void mostrarEstadoVacio() {
        SwingUtilities.invokeLater(() -> {
            usuariosBox.removeAll();
            JLabel placeholder = new JLabel("🔍 Buscando usuarios en la red...");
            placeholder.setForeground(Color.GRAY);
            placeholder.setBorder(new EmptyBorder(20, 20, 20, 20));
            placeholder.setAlignmentX(Component.CENTER_ALIGNMENT);
            usuariosBox.add(placeholder);
            usuariosBox.revalidate();
            usuariosBox.repaint();
        });
    }
}
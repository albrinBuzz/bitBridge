package org.bitBridge.view.fx.components.hosts;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.HostsObserver;
import org.bitBridge.shared.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.List;
public class HostsPanelSwing extends JPanel implements HostsObserver {

    private Client cliente;
    private JPanel usuariosBox;  // JPanel donde se añaden los paneles de hosts
    private JScrollPane usuariosScroll;  // El JScrollPane que contendrá el JPanel
    private JLabel usuariosTitle;

    public HostsPanelSwing(Client cliente) {
        this.cliente = cliente;
        //cliente.addObserver(this);
        cliente.addHostOserver(this);

        // Crear un JPanel para contener los paneles de los hosts
        usuariosBox = new JPanel();
        usuariosBox.setLayout(new BoxLayout(usuariosBox, BoxLayout.Y_AXIS));  // Utilizamos BoxLayout para organizar los elementos
        usuariosBox.setBackground(new Color(46, 46, 46));  // Fondo oscuro
        usuariosBox.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));  // Padding

        usuariosTitle = new JLabel("🧑‍💻 Usuarios Conectados (5)");
        usuariosTitle.setFont(new Font("Arial", Font.BOLD, 14));

        usuariosBox.add(usuariosTitle);

        // Crear un JScrollPane que contendrá el JPanel
        usuariosScroll = new JScrollPane(usuariosBox);
        usuariosScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);  // Desactivar el scroll horizontal
        usuariosScroll.setPreferredSize(new Dimension(400, 180));  // Definir el tamaño

        // Configuración de la sección de clientes conectados
        JLabel titleLabel = new JLabel("Clientes Conectados");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setForeground(new Color(0, 123, 255));  // Color azul
        titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Configurar el JPanel principal
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.setBackground(new Color(45, 45, 45));  // Fondo oscuro

        this.add(titleLabel);  // Añadir el título
        this.add(usuariosScroll);  // Añadir el ScrollPane

        this.setBorder(BorderFactory.createLineBorder(new Color(0, 191, 255), 2));  // Borde azul
    }

    public void updateAllHosts(List<ClientInfo> hostList) {
        Logger.logInfo("Actualizado la lista de hosts");
        if (hostList == null || hostList.isEmpty()) {
            Logger.logInfo("La lista de hosts está vacía.");

            SwingUtilities.invokeLater(() -> this.usuariosBox.removeAll());
            return;  // Si la lista está vacía o nula, no hacer nada
        }
        hostList.forEach(clientInfo -> Logger.logInfo(clientInfo.getNick()));

        // Usar SwingUtilities.invokeLater para asegurar que las actualizaciones se realicen en el hilo de la interfaz de usuario
        SwingUtilities.invokeLater(() -> {
            // Limpiar el JPanel antes de agregar los nuevos elementos
            usuariosBox.removeAll();

            JLabel usuariosTitle = new JLabel("🧑‍💻 Usuarios Conectados (" + hostList.size() + ")");
            usuariosTitle.setFont(new Font("Arial", Font.BOLD, 14));
            usuariosBox.add(usuariosTitle);

            // Agregar un panel por cada host en la lista
            for (ClientInfo host : hostList) {
                if (!host.getNick().equals("enviando") && !host.getNick().chars().allMatch(Character::isDigit)) {
                    //HostControlPanel hostControlPanel = new HostControlPanel(host, this.cliente);
                    //hostControlPanel.setBackground(new Color(46, 46, 46));  // Fondo oscuro para cada panel de host
                    //usuariosBox.add(hostControlPanel);  // Agregar el panel del host al JPanel
                }
            }

            // Forzar el JScrollPane a mostrar la última actualización
            usuariosScroll.getVerticalScrollBar().setValue(usuariosScroll.getVerticalScrollBar().getMaximum());
            usuariosBox.revalidate();
            usuariosBox.repaint();
        });
    }

    // Panel que representa a un usuario conectado
    private JPanel createUserItem(String nombre, String estado, String ultimaConexion, boolean mostrarArchivos) {
        JPanel userItem = new JPanel();
        userItem.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        userItem.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(189, 195, 199)));
        userItem.setBackground(new Color(46, 46, 46));

        JLabel icon = new JLabel(estado);
        JLabel nameLabel = new JLabel("👤 " + nombre);
        JLabel lastConn = new JLabel("Última conexión: " + ultimaConexion);
        lastConn.setFont(new Font("Arial", Font.ITALIC, 12));
        lastConn.setForeground(new Color(136, 136, 136));

        JButton enviarArchivoBtn = new JButton("📤 Enviar archivo");

        // Agregar los componentes al panel
        userItem.add(nameLabel);
        userItem.add(icon);
        userItem.add(lastConn);
        userItem.add(Box.createHorizontalGlue());
        userItem.add(enviarArchivoBtn);

        if (mostrarArchivos) {
            JButton archivosBtn = new JButton("📁 Archivos");
            userItem.add(archivosBtn);
        }

        return userItem;
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            // Crear la ventana principal
            JFrame frame = new JFrame("Panel de Hosts");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 400);

            // Crear el panel de hosts
            HostsPanelSwing hostsPanel = new HostsPanelSwing(new Client());
            hostsPanel.setBackground(new Color(34, 49, 63));  // Fondo oscuro

            // Añadir el panel de hosts a la ventana
            frame.add(hostsPanel);

            // Hacer visible la ventana
            frame.setVisible(true);
        });
    }
}
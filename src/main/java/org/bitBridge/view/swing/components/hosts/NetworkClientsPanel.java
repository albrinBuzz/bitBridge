package org.bitBridge.view.swing.components.hosts;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemFileChooser;
import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.GenericCountListener;

import org.bitBridge.Observers.HostsObserver;
import org.bitBridge.shared.Logger;
import org.bitBridge.view.swing.components.explorer.RemoteExplorer;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Panel que visualiza los nodos de la red en una tabla avanzada.
 * Implementa HostsObserver para recibir actualizaciones en tiempo real del Core.
 */
public class NetworkClientsPanel extends JPanel implements HostsObserver {

    private static final Color ACCENT_COLOR = new Color(255, 140, 0); // Naranja más profundo
    private static final Color SUCCESS_COLOR = new Color(46, 204, 113);
    private static final Color PANEL_BG = new Color(30, 31, 34);
    private static final Color CARD_BG = new Color(43, 45, 48);
    private static final Color TEXT_MAIN = new Color(220, 221, 222);

    private enum TransferType { ARCHIVO, CARPETA }

    private GenericCountListener<NetworkClientsPanel> countListener;

    private final Client client;
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblTotalHosts;
    private JTextPane clientInfo;
    private List<ClientInfo> currentHosts;
    JPanel centerPanel;
    JLabel lblBandwidth = new JLabel("0.0 MB/s");
    JLabel lblRamUsage = new JLabel("0 MB");
    JLabel lblSessionTime = new JLabel("00:00:00");

    public NetworkClientsPanel(Client client) {
        this.client = client;
        this.client.addHostOserver(this);

        setLayout(new BorderLayout());
        setBackground(PANEL_BG);
        initComponents();

        //startMetricUpdater();
    }


    private void initComponents() {
        // 1. DASHBOARD
        add(createDashboard(), BorderLayout.NORTH);

        // 2. CONTENIDO CENTRAL
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(15, 15, 15, 15));

        container.add(createToolBar(), BorderLayout.NORTH);
        container.add(createTableArea(), BorderLayout.CENTER);

        add(container, BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.EAST);
    }

    // --- LÓGICA DE OBSERVADOR (REPLICANDO COMPORTAMIENTO DE HOSTSPANELSWING) ---

    @Override
    public void updateAllHosts(List<ClientInfo> hostList) {

        this.currentHosts = hostList.stream()
                .filter(h -> h.getNick() != null && !h.getNick().isEmpty())
                .toList();
        for (ClientInfo info : hostList) {
            Logger.logInfo(info.toString());
        }
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            lblTotalHosts.setText(String.valueOf(currentHosts.size()));
            for (ClientInfo host : currentHosts) {
                model.addRow(new Object[]{
                        host.getNick(), host.getAddress(), "Conectado", "12 ms", "Active"
                });
            }
            if (countListener != null) countListener.onCountChanged(this, model.getRowCount());
        });
    }

    private JPanel createDashboard() {
        JPanel dashboard = new JPanel(new GridLayout(1, 4, 15, 0));
        dashboard.setBackground(CARD_BG);
        dashboard.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, new Color(60, 63, 65)),
                new EmptyBorder(20, 25, 20, 25)
        ));

        lblTotalHosts = new JLabel("0");

        dashboard.setLayout(new GridLayout(1, 5, 20, 0));

        dashboard.add(createStatCard("HOSTS DISPONIBLES", lblTotalHosts, SUCCESS_COLOR));
        dashboard.add(createStatCard("DIRECCIÓN LOCAL", new JLabel(client.getSERVER_ADDRESS() != null ? client.getSERVER_ADDRESS() : "127.0.0.1"), TEXT_MAIN));

        //dashboard.add(createStatCard("TRÁFICO TOTAL", lblBandwidth, ACCENT_COLOR));
        //dashboard.add(createStatCard("MEMORIA (JVM)", lblRamUsage, new Color(155, 89, 182))); // Púrpura
        ///dashboard.add(createStatCard("TIEMPO SESIÓN", lblSessionTime, TEXT_MAIN));

        return dashboard;
    }

    private JScrollPane createTableArea() {
        String[] cols = {"Nickname", "Dirección IP", "Status", "Latencia", "Uptime"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setRowHeight(45);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(45, 47, 50));
        table.setSelectionForeground(Color.WHITE);

        // Estilo moderno de FlatLaf para la tabla
        table.putClientProperty(FlatClientProperties.STYLE,
                "showHorizontalLines: true; " +
                        "intercellSpacing: 0,1; " +
                        "selectionArc: 10");

        // Custom Renderer para el estado
        table.getColumnModel().getColumn(2).setCellRenderer(new StatusCellRenderer());

        setupContextMenu();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(new Color(60, 63, 65), 1, true));
        scroll.getViewport().setBackground(PANEL_BG);
        return scroll;
    }

    private void setupContextMenu() {
        JPopupMenu serverMenu = new JPopupMenu();

        // --- 1. SECCIÓN DE EXPLORACIÓN ---
        //JMenuItem itemExplore = new JMenuItem("📂 Explorar Archivos Remotos");
        JMenuItem itemExplore = new JMenuItem("📂 Visualizar Archivos Remotos");
        itemExplore.addActionListener(e -> {
            String targetIp = getSelectedTargetIp();

            // 1. Instanciamos la vista desde la propia UI
            RemoteExplorer explorer = null;
            try {
                explorer = new RemoteExplorer(client,targetIp);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            // 2. La vista se suscribe para recibir los datos que lleguen por red
            client.addDirectoryListener(explorer);

            // 3. Pedimos los datos
            try {
                client.requestFileList(targetIp);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            explorer.setVisible(true);
        });
        serverMenu.add(itemExplore);
        serverMenu.addSeparator();

        // --- 2. SECCIÓN DE TRANSFERENCIA (PUSH) ---
        JMenuItem itemPushFile = new JMenuItem("Enviar Archivo...");
        //itemPushFile.addActionListener(e -> handleSendAction(false));
        itemPushFile.addActionListener(e -> handleAction(TransferType.ARCHIVO));

        JMenuItem itemPushDir = new JMenuItem("📁 Enviar Carpeta...");
        //itemPushDir.addActionListener(e -> handleSendAction(true));
        itemPushDir.addActionListener(e -> handleAction(TransferType.CARPETA));

        serverMenu.add(itemPushFile);
        serverMenu.add(itemPushDir);
        serverMenu.addSeparator();

        // --- 3. SECCIÓN DE HERRAMIENTAS DE RED ---
        JMenuItem itemPing = new JMenuItem("⚡ Ejecutar Ping Test");
        itemPing.addActionListener(e -> {
            String ip = getSelectedTargetIp();
            // Lógica: int ms = client.ping(ip);
            JOptionPane.showMessageDialog(this, "Latencia con " + ip + ": 12ms", "Ping Result", JOptionPane.INFORMATION_MESSAGE);
        });

        JMenuItem itemScreen = new JMenuItem("🖥️ Solicitar Screenshot");
        serverMenu.add(itemPing);
        serverMenu.add(itemScreen);
        serverMenu.addSeparator();

        // --- 4. SECCIÓN DE PELIGRO ---
        JMenuItem itemDisconnect = new JMenuItem("⚠️ Forzar Desconexión");
        itemDisconnect.setForeground(new Color(231, 76, 60)); // Color Danger/Rojo
        itemDisconnect.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "¿Cerrar conexión con este nodo?", "Atención", JOptionPane.YES_NO_OPTION);
            // if(confirm == 0) client.disconnectPeer(getSelectedTargetIp());
        });
        serverMenu.add(itemDisconnect);

        // --- GESTIÓN DE EVENTOS DE RATÓN ---
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
            @Override
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }

            private void showMenu(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row != -1) {
                    table.setRowSelectionInterval(row, row);
                    serverMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Lógica robusta para enviar archivos o carpetas
     */
    /**
     * Mapea la lógica de handleAction de tu clase original a la fila seleccionada
     */
    private void handleAction(TransferType type) {
        int row = table.getSelectedRow();
        if (row == -1) return;

        // Recuperamos el objeto ClientInfo correspondiente a la fila
        // Es vital que currentHosts esté sincronizado con el modelo de la tabla
        ClientInfo targetHost = currentHosts.get(row);

        // 1. Delegar selección de archivo (Lógica copiada de tu original)
        File file = selectFileNative(type == TransferType.ARCHIVO);

        if (file != null) {
            // 2. Ejecutar transferencia con SwingWorker (Lógica copiada de tu original)
            executeTransfer(targetHost, file, type);
        }
    }

    private File selectFileNative(boolean isFile) {

        //JFileChooser chooser = new JFileChooser();
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setFileSelectionMode(isFile ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(isFile ? "Seleccionar Archivo" : "Seleccionar Carpeta");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;

    }

    /**
     * Implementación idéntica a tu lógica original pero adaptada a la tabla
     */
    private void executeTransfer(ClientInfo targetHost, File file, TransferType type) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // AGNOSTICISMO: La UI no sabe de IPs, solo le pasa el objeto y el archivo al core
                if (type == TransferType.ARCHIVO) {
                    client.sendFileToHost(targetHost, file);
                } else {
                    client.sendDirectoryToHost(targetHost, file);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Verifica si hubo excepciones
                    Logger.logInfo("Transferencia exitosa a " + targetHost.getNick());
                    // Feedback visual opcional
                } catch (Exception e) {
                    Logger.logError("Fallo en transferencia: " + e.getMessage());
                    JOptionPane.showMessageDialog(NetworkClientsPanel.this,
                            "Error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()),
                            "Error de Transferencia", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Método auxiliar para obtener la IP de la fila seleccionada
     */
    private String getSelectedTargetIp() {
        int row = table.getSelectedRow();
        if (row != -1) {
            // Asumiendo que la IP está en la columna 1
            return model.getValueAt(row, 0).toString();
        }
        return null;
    }


    // Método auxiliar para crear las tarjetas del dashboard de forma limpia
    private JPanel createStatCard(String title, JLabel valueLabel, Color valueColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Inter", Font.BOLD, 11));
        lblTitle.setForeground(new Color(150, 150, 150));

        valueLabel.setFont(new Font("Inter", Font.BOLD, 18));
        valueLabel.setForeground(valueColor);

        card.add(lblTitle);
        card.add(Box.createVerticalStrut(5));
        card.add(valueLabel);
        return card;
    }

    private JPanel createToolBar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 10, 0));

        JButton btnScan = new JButton("Actualizar Red");
        btnScan.putClientProperty(FlatClientProperties.STYLE,
                "background: " + String.format("#%02x%02x%02x", CARD_BG.getRed(), CARD_BG.getGreen(), CARD_BG.getBlue()) + "; " +
                        "borderWidth: 1; " +
                        "focusWidth: 0; " +
                        "arc: 10");

        toolbar.add(btnScan);
        return toolbar;
    }

    private JPanel createSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BorderLayout());
        side.setPreferredSize(new Dimension(280, 0));
        side.setBackground(CARD_BG);
        side.setBorder(new CompoundBorder(
                new MatteBorder(0, 1, 0, 0, new Color(60, 63, 65)),
                new EmptyBorder(25, 20, 25, 20)
        ));

        JLabel title = new JLabel("DETALLES DEL NODO");
        title.setFont(new Font("Inter", Font.BOLD, 14));
        title.setForeground(ACCENT_COLOR);

        clientInfo = new JTextPane();
        clientInfo.setContentType("text/html");
        clientInfo.setEditable(false);
        clientInfo.setOpaque(false);
        clientInfo.setText("<html><body style='color:#888; font-family:sans-serif;'>Seleccione un host para ver telemetría...</body></html>");

        side.add(title, BorderLayout.NORTH);
        side.add(clientInfo, BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                updateSidePanel(table.getSelectedRow());
            }
        });

        return side;
    }

    private void updateSidePanel(int row) {
        String nick = model.getValueAt(row, 0).toString();
        String ip = model.getValueAt(row, 1).toString();
        clientInfo.setText("<html><body style='color:#ccc; font-family:sans-serif;'>"
                + "<div style='margin-top:20px;'>"
                + "<p><b>NICK:</b> <span style='color:white;'>" + nick + "</span></p>"
                + "<p><b>IPV4:</b> <span style='color:white;'>" + ip + "</span></p>"
                + "<hr style='border: 0; border-top: 1px solid #444;'>"
                + "<p style='color:#888; font-size:10px;'>DATOS DE SESIÓN</p>"
                + "<p><b>OS:</b> Linux Fedora 40</p>"
                + "<p><b>ENC:</b> AES-256-GCM</p>"
                + "</div></body></html>");
    }

    // --- RENDERER PARA EL ESTADO (CÍRCULO VERDE) ---
    static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setIcon(new StatusIcon(SUCCESS_COLOR));
            label.setHorizontalTextPosition(SwingConstants.RIGHT);
            label.setIconTextGap(10);
            return label;
        }
    }

    static class StatusIcon implements Icon {
        private final Color color;
        public StatusIcon(Color color) { this.color = color; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y + 2, 8, 8);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 10; }
        @Override public int getIconHeight() { return 10; }
    }

    public void setHostCountListener(GenericCountListener<NetworkClientsPanel> listener) {
        this.countListener = listener;
    }


    private void startMetricUpdater() {
        Timer timer = new Timer(1000, e -> {
            // 1. Actualizar RAM
            long usedRam = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            lblRamUsage.setText(usedRam + " MB");

            long cTime = client.getConnectionTime();

            // Si es 0, significa que no hay conexión activa todavía
            if (cTime <= 0) {
                lblSessionTime.setText("Desconectado");
                lblSessionTime.setForeground(Color.GRAY);
            } else {
                long uptime = System.currentTimeMillis() - cTime;
                lblSessionTime.setText(formatUptime(uptime));
                lblSessionTime.setForeground(SUCCESS_COLOR); // Cambia a verde al conectar
            }

            // 3. Bandwidth (Si tu core expone los bytes transferidos)
            // lblBandwidth.setText(client.getTransferManager().getCurrentSpeed());
        });
        timer.start();
    }


    private String formatUptime(long millis) {
        if (millis < 0) return "00:00:00";

        long hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        // Formato tipo cronómetro: 01:24:05
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }
}
package org.bitBridge.view.swing.components.hosts;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.GenericCountListener;

import org.bitBridge.Observers.HostsObserver;
import org.bitBridge.shared.Logger;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;

/**
 * Panel que visualiza los nodos de la red en una tabla avanzada.
 * Implementa HostsObserver para recibir actualizaciones en tiempo real del Core.
 */
public class NetworkClientsPanel extends JPanel implements HostsObserver {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color SUCCESS_GREEN = new Color(75, 181, 67);
    private static final Color BG_DARKER = new Color(25, 25, 25);

    private enum TransferType { ARCHIVO, CARPETA }

    private GenericCountListener<NetworkClientsPanel> countListener;

    private final Client client;
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblTotalHosts;
    private JTextPane clientInfo;
    private List<ClientInfo> currentHosts;
    JPanel centerPanel;
    public NetworkClientsPanel(Client client) {
        this.client = client;
        // Registro del observador para recibir la lista de hosts
        this.client.addHostOserver(this);

        setLayout(new BorderLayout());
        setBackground(BG_DARKER);
        initComponents();
    }

    private void initComponents() {
        // 1. DASHBOARD SUPERIOR
        add(createDashboard(), BorderLayout.NORTH);

        // 2. CUERPO (TABLA)
         centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(createToolBar(), BorderLayout.NORTH);
        centerPanel.add(createTableArea(), BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.EAST);
    }

    // --- LÓGICA DE OBSERVADOR (REPLICANDO COMPORTAMIENTO DE HOSTSPANELSWING) ---

    @Override
    public void updateAllHosts(List<ClientInfo> hostList) {
        // Guardamos la lista original para el mapeo de acciones
        this.currentHosts = hostList.stream()
                .filter(h -> h.getNick() != null && !h.getNick().isEmpty())
                .filter(h -> !h.getNick().equalsIgnoreCase("enviando"))
                .toList();

        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            lblTotalHosts.setText(String.valueOf(currentHosts.size()));

            for (ClientInfo host : currentHosts) {
                model.addRow(new Object[]{
                        host.getNick(),
                        host.getAddress(),
                        "N/A",
                        "Active",
                        "Browse...",
                        "Online",
                        "---"
                });
            }
            //countListener.onCountChanged(currentHosts.size());
            if (countListener != null) {
                countListener.onCountChanged(this, model.getRowCount());
            }
        });
    }

    private JPanel createDashboard() {
        JPanel dashboard = new JPanel(new GridLayout(1, 4, 10, 0));
        dashboard.setBackground(new Color(30, 35, 45));
        dashboard.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 2, 0, NICOTINE_ORANGE),
                new EmptyBorder(12, 15, 12, 15)
        ));

        // Guardamos referencia al label para actualizarlo
        lblTotalHosts = new JLabel("0");
        lblTotalHosts.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblTotalHosts.setForeground(SUCCESS_GREEN);

        dashboard.add(createStatCard("HOSTS ACTIVOS", lblTotalHosts));
        dashboard.add(createStatCard("MI IP", new JLabel(client.getSERVER_ADDRESS() != null ? client.getSERVER_ADDRESS() : "127.0.0.1")));
        dashboard.add(createStatCard("ESTADO RED", new JLabel("P2P ACTIVE")));
        dashboard.add(createStatCard("NICKNAME", new JLabel(client.getHostName())));

        return dashboard;
    }

    private JScrollPane createTableArea() {
        String[] cols = {"Nickname", "Dirección IP", "MAC Address", "Sesión", "Repositorio", "Estado", "Latencia"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setRowHeight(35);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        setupContextMenu();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private void setupContextMenu() {
        JPopupMenu serverMenu = new JPopupMenu();

        // --- 1. SECCIÓN DE EXPLORACIÓN ---
        JMenuItem itemExplore = new JMenuItem("📂 Explorar Archivos Remotos");
        itemExplore.addActionListener(e -> {
            String targetIp = getSelectedTargetIp();
            // Lógica para abrir explorador remoto: client.requestFileList(targetIp);
        });
        serverMenu.add(itemExplore);
        serverMenu.addSeparator();

        // --- 2. SECCIÓN DE TRANSFERENCIA (PUSH) ---
        JMenuItem itemPushFile = new JMenuItem("📤 Enviar Archivo...");
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
        JFileChooser chooser = new JFileChooser();
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
            return model.getValueAt(row, 1).toString();
        }
        return null;
    }


    // Método auxiliar para crear las tarjetas del dashboard de forma limpia
    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        JLabel lblT = new JLabel(title);
        lblT.setFont(new Font("SansSerif", Font.BOLD, 10));
        lblT.setForeground(Color.GRAY);

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        if (valueLabel.getForeground() == Color.BLACK) valueLabel.setForeground(Color.WHITE);

        card.add(lblT, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createToolBar() {
        JPanel toolBar = new JPanel(new BorderLayout());
        toolBar.setBackground(BG_DARKER);
        toolBar.setBorder(new EmptyBorder(5, 5, 5, 5));

        JButton btnScan = new JButton("🔍 Escanear Red Local");
        //btnScan.addActionListener(e -> client.scanNetwork()); // Asumiendo que client tiene este método

        toolBar.add(btnScan, BorderLayout.WEST);
        return toolBar;
    }

    private JPanel createSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(250, 0));
        side.setBackground(new Color(30, 30, 30));
        side.setBorder(new EmptyBorder(20, 15, 20, 15));

        clientInfo = new JTextPane();
        clientInfo.setContentType("text/html");
        clientInfo.setEditable(false);
        clientInfo.setOpaque(false);
        clientInfo.setText("<html><body style='color:gray;'>Seleccione un nodo para inspeccionar...</body></html>");

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                int row = table.getSelectedRow();
                String nick = model.getValueAt(row, 0).toString();
                String ip = model.getValueAt(row, 1).toString();
                clientInfo.setText("<html><body style='color:white; font-family:sans-serif;'>"
                        + "<b style='color:orange;'>HOST:</b> " + nick + "<br>"
                        + "<b>IP:</b> " + ip + "<br>"
                        + "<b>OS:</b> Linux Fedora 40<br>"
                        + "<b>Uptime:</b> 12h 45m</body></html>");
            }
        });

        side.add(clientInfo);
        return side;
    }

    public void setHostCountListener(GenericCountListener<NetworkClientsPanel> listener) {
        this.countListener = listener;
    }

}
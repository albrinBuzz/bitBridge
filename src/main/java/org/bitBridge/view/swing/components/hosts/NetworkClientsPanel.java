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
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Panel visual simplificado y amigable para el usuario final.
 * Muestra información operativa útil ocultando los tecnicismos de red complejos.
 * * Copyright 2026 Cristobal Roman Zamora
 */
public class NetworkClientsPanel extends JPanel implements HostsObserver {

    private static final Color ACCENT_COLOR = new Color(255, 140, 0);
    private static final Color SUCCESS_COLOR = new Color(46, 204, 113);
    private static final Color WARN_COLOR = new Color(241, 196, 15);
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

    public NetworkClientsPanel(Client client) {
        this.client = client;
        this.client.addHostOserver(this);

        setLayout(new BorderLayout());
        setBackground(PANEL_BG);
        initComponents();
    }

    private void initComponents() {
        // 1. Panel de estadísticas superior
        add(createDashboard(), BorderLayout.NORTH);

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(15, 15, 15, 15));

        container.add(createToolBar(), BorderLayout.NORTH);
        container.add(createTableArea(), BorderLayout.CENTER);

        add(container, BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.EAST);
    }

    @Override
    public void updateAllHosts(List<ClientInfo> hostList) {
        this.currentHosts = hostList.stream()
                .filter(h -> h.getNick() != null && !h.getNick().isEmpty())
                .toList();

        for (ClientInfo info : hostList) {
            Logger.logInfo(info.toString());
        }

        SwingUtilities.invokeLater(() -> {
            int selectedRow = table.getSelectedRow();

            model.setRowCount(0);
            lblTotalHosts.setText(String.valueOf(currentHosts.size()));

            for (ClientInfo host : currentHosts) {
                // Traducimos el estado técnico a algo familiar
                String estadoAmigable = mapearEstadoAmigable(host.getStatus());

                // Columnas de Datos Amigables: Nombre, Actividad Actual, Sistema, Tiempo Conectado
                model.addRow(new Object[]{
                        host.getNick(),
                        estadoAmigable,
                        host.getOsName(),
                        host.formatUptime(),
                        "", "", ""
                });
            }

            // Si había un elemento seleccionado, mantenemos la selección viva y refrescamos su panel lateral
            if (selectedRow != -1 && selectedRow < table.getRowCount()) {
                table.setRowSelectionInterval(selectedRow, selectedRow);
                updateSidePanel(selectedRow);
            }

            if (countListener != null) countListener.onCountChanged(this, model.getRowCount());
        });
    }

    private JPanel createDashboard() {
        JPanel dashboard = new JPanel(new GridLayout(1, 2, 20, 0));
        dashboard.setBackground(CARD_BG);
        dashboard.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, new Color(60, 63, 65)),
                new EmptyBorder(20, 25, 20, 25)
        ));

        lblTotalHosts = new JLabel("0");

        dashboard.add(createStatCard("EQUIPOS CONECTADOS A TI", lblTotalHosts, SUCCESS_COLOR));
        dashboard.add(createStatCard("TU NOMBRE EN LA RED", new JLabel(client.getHostName() != null ? client.getHostName() : "Iniciando..."), TEXT_MAIN));

        return dashboard;
    }

    private JScrollPane createTableArea() {
        // Estructura limpia y descriptiva sin IPs ni Puertos
        String[] cols = {"Nombre del Equipo", "Actividad Actual", "Sistema", "Tiempo Conectado", "Centro de Sincronización", "Enviar Archivo", "Enviar Carpeta"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c >= 4; } // Columnas de botones activas
        };

        table = new JTable(model);
        table.setRowHeight(45);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(45, 47, 50));
        table.setSelectionForeground(Color.WHITE);

        table.putClientProperty(FlatClientProperties.STYLE,
                "showHorizontalLines: true; " +
                        "intercellSpacing: 0,1; " +
                        "selectionArc: 10");

        // Renderer para cambiar los colores del texto del estado
        table.getColumnModel().getColumn(1).setCellRenderer(new FriendlyStatusCellRenderer());

        // Inyectar los botones de interacción directa en las columnas correspondientes
        setupActionButtons();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(new Color(60, 63, 65), 1, true));
        scroll.getViewport().setBackground(PANEL_BG);
        return scroll;
    }

    private void setupActionButtons() {
        // Columna 4: Botón principal de sincronización y gestión bidireccional
        table.getColumnModel().getColumn(4).setCellRenderer(new TableButtonRenderer("🔄 Sincronizar y Compartir", ACCENT_COLOR));
        table.getColumnModel().getColumn(4).setCellEditor(new TableButtonEditor(e -> ejecutarAccionExplorar()));

        // Columna 5: Enviar Archivo Directo
        table.getColumnModel().getColumn(5).setCellRenderer(new TableButtonRenderer("📄 Enviar Archivo", TEXT_MAIN));
        table.getColumnModel().getColumn(5).setCellEditor(new TableButtonEditor(e -> handleAction(TransferType.ARCHIVO)));

        // Columna 6: Enviar Carpeta Directa
        table.getColumnModel().getColumn(6).setCellRenderer(new TableButtonRenderer("📁 Enviar Carpeta", TEXT_MAIN));
        table.getColumnModel().getColumn(6).setCellEditor(new TableButtonEditor(e -> handleAction(TransferType.CARPETA)));
    }

    private void ejecutarAccionExplorar() {
        String targetIp = getSelectedTargetIp();
        if (targetIp == null) return;

        RemoteExplorer explorer = null;
        try {
            explorer = new RemoteExplorer(client, targetIp);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        client.addDirectoryListener(explorer);

        try {
            client.requestFileList(targetIp);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        explorer.setVisible(true);
    }

    private void handleAction(TransferType type) {
        int row = table.getSelectedRow();
        if (row == -1) return;

        ClientInfo targetHost = currentHosts.get(row);
        File file = selectFileNative(type == TransferType.ARCHIVO);

        if (file != null) {
            executeTransfer(targetHost, file, type);
        }
    }

    private File selectFileNative(boolean isFile) {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setFileSelectionMode(isFile ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(isFile ? "Elige el archivo a mandar" : "Elige la carpeta a mandar");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private void executeTransfer(ClientInfo targetHost, File file, TransferType type) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
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
                    get();
                    Logger.logInfo("Envío completado hacia " + targetHost.getNick());
                } catch (Exception e) {
                    Logger.logError("Fallo en transferencia: " + e.getMessage());
                    JOptionPane.showMessageDialog(NetworkClientsPanel.this,
                            "No se pudo realizar el envío en este momento.",
                            "Aviso", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String getSelectedTargetIp() {
        int row = table.getSelectedRow();
        if (row != -1) {
            // Sigue obteniendo el Nick (Columna 0) para no romper tus consultas originales del core
            return model.getValueAt(row, 0).toString();
        }
        return null;
    }

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

        JButton btnScan = new JButton("Buscar Equipos Nuevos");
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

        JLabel title = new JLabel("RESUMEN DE USO");
        title.setFont(new Font("Inter", Font.BOLD, 13));
        title.setForeground(ACCENT_COLOR);

        clientInfo = new JTextPane();
        clientInfo.setContentType("text/html");
        clientInfo.setEditable(false);
        clientInfo.setOpaque(false);
        clientInfo.setText("<html><body style='color:#888; font-family:sans-serif;'>Selecciona un equipo de la lista para ver cuánto uso lleva en la aplicación...</body></html>");

        side.add(title, BorderLayout.NORTH);
        side.add(clientInfo, BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                updateSidePanel(table.getSelectedRow());
            }
        });

        return side;
    }

    /**
     * Muestra datos de uso de recursos convertidos a métricas humanas
     */
    private void updateSidePanel(int row) {
        if (currentHosts == null || row >= currentHosts.size()) return;

        ClientInfo host = currentHosts.get(row);

        // Convertimos los bytes crudos a formatos legibles como MB o KB automáticamente
        String enviados = transformarBytesALegible(host.getTotalBytesSent());
        String recibidos = transformarBytesALegible(host.getTotalBytesReceived());

        clientInfo.setText("<html><body style='color:#ccc; font-family:sans-serif; font-size:11px;'>"
                + "<div style='margin-top:15px;'>"
                + "<p style='margin: 5px 0;'><b>Nombre:</b> <span style='color:white;'>" + host.getNick() + "</span></p>"
                + "<p style='margin: 5px 0;'><b>Sistema Operativo:</b> <span style='color:white;'>" + host.getOsName() + "</span></p>"
                + "<p style='margin: 5px 0;'><b>Versión App:</b> <span style='color:#2ecc71;'>" + host.getClientVersion() + "</span></p>"
                + "<hr style='border: 0; border-top: 1px solid #444; margin: 12px 0;'>"
                + "<p style='color:#888; font-size:10px; font-weight:bold; margin-bottom: 6px;'>ARCHIVOS COMPARTIDOS EN ESTA SESIÓN</p>"
                + "<p style='margin: 4px 0;'><b>Datos Enviados:</b> <span style='color:#3498db;'>" + enviados + "</span></p>"
                + "<p style='margin: 4px 0;'><b>Datos Recibidos:</b> <span style='color:#9b59b6;'>" + recibidos + "</span></p>"
                + "</div></body></html>");
    }

    /**
     * Convierte valores numéricos de red gigantescos a texto intuitivo (Bytes, KB, MB, GB)
     */
    private String transformarBytesALegible(long bytes) {
        if (bytes <= 0) return "Ninguno todavía";
        if (bytes < 1024) return bytes + " Bytes";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Traduce los flags de texto del core en estados lógicos que un usuario entienda
     */
    private String mapearEstadoAmigable(String status) {
        if (status == null) return "Conectado";
        return switch (status.toUpperCase()) {
            case "IDLE" -> "En Espera";
            case "TRANSFRIENDO", "TRANSFER" -> "Compartiendo Archivos...";
            default -> "Listo";
        };
    }

    // --- RENDERIZADORES DE DISEÑO ---

    /**
     * Renderer personalizado para los textos de estado
     */
    static class FriendlyStatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String val = value != null ? value.toString() : "Listo";

            Color statusColor = SUCCESS_COLOR;
            if (val.contains("Compartiendo")) {
                statusColor = ACCENT_COLOR;
            } else if (val.contains("Espera")) {
                statusColor = TEXT_MAIN;
            }

            if (!isSelected) {
                label.setForeground(statusColor);
            }
            return label;
        }
    }

    static class TableButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public TableButtonRenderer(String text, Color textColor) {
            setText(text);
            setOpaque(false);
            setForeground(textColor);
            setFont(new Font("SansSerif", Font.BOLD, 11));
            putClientProperty(FlatClientProperties.STYLE, "arc: 6; borderWidth: 1; focusWidth: 0;");
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFoc, int r, int c) {
            return this;
        }
    }

    static class TableButtonEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
        private final JButton button;
        private final java.awt.event.ActionListener actionListener;

        public TableButtonEditor(java.awt.event.ActionListener actionListener) {
            this.actionListener = actionListener;
            this.button = new JButton();
            this.button.addActionListener(this);
            this.button.putClientProperty(FlatClientProperties.STYLE, "arc: 6; focusWidth: 0;");
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            table.setRowSelectionInterval(row, row);
            return button;
        }

        @Override
        public Object getCellEditorValue() { return ""; }

        @Override
        public void actionPerformed(ActionEvent e) {
            fireEditingStopped();
            actionListener.actionPerformed(e);
        }
    }

    public void setHostCountListener(GenericCountListener<NetworkClientsPanel> listener) {
        this.countListener = listener;
    }
}
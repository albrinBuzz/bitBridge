package org.bitBridge.view.swing.components.transfers;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;
import org.bitBridge.Client.managers.TransferManager;
import org.bitBridge.Observers.GenericCountListener;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.models.TransferProgress;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.*;
import org.bitBridge.shared.core.comunication.FileHandshakeAction;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centro de Control de Transferencias Asíncronas bitBridge Pro.
 * Incorpora Throttling optimizado para sockets de alta velocidad, filtrado por RowFilter,
 * barras de progreso adaptativas y gestión atómica de hilos de control (Pausar/Cancelar).
 * * Copyright 2026 Cristobal Roman Zamora
 */
public class TransferPanel extends JPanel implements TransferencesObserver {

    private GenericCountListener<TransferPanel> countListener;

    private JTable table;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;

    // ConcurrentHashMap previene la colisión de hilos si Netty actualiza el mapa mientras Swing itera
    private final Map<String, Integer> rowMap = new ConcurrentHashMap<>();
    private final Map<String, TransferManager> managerMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateMap = new ConcurrentHashMap<>();

    private static final int REFRESH_RATE_MS = 150; // Umbral crítico para protección del Event Dispatch Thread (EDT)

    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);
    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color PANEL_BG = new Color(30, 31, 34);
    private static final Color CARD_BG = new Color(43, 45, 48);

    public TransferPanel() {
        setLayout(new BorderLayout());
        setBackground(PANEL_BG);
        setBorder(new EmptyBorder(20, 25, 20, 25));
        initComponents();
    }

    private void initComponents() {
        // Estructura del modelo con inyección de acciones
        String[] cols = {"ID", "ARCHIVO", "TIPO", "ESTADO", "VELOCIDAD", "PROGRESO", "TAMAÑO", "ACCIONES"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 7; }
            @Override public Class<?> getColumnClass(int c) {
                if (c == 5) return Integer.class;
                return Object.class;
            }
        };

        // Activamos el Sorter para el motor de filtrado instantáneo
        sorter = new TableRowSorter<>(model);
        sorter.setSortsOnUpdates(false); // CRÍTICO: Evita saltos bruscos y reordenamiento masivo en descargas masivas

        table = new JTable(model);
        table.setRowSorter(sorter);
        table.setRowHeight(55);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(PANEL_BG);

        table.putClientProperty(FlatClientProperties.STYLE,
                "selectionBackground: #2d3a4d; selectionForeground: #ffffff; showHorizontalLines: true;");

        setupRenderersAndEditors();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new com.formdev.flatlaf.ui.FlatLineBorder(
                new Insets(0, 0, 0, 0),
                new Color(60, 63, 65),
                1,
                16
        ));
        scroll.getViewport().setBackground(PANEL_BG);

        // El Header se crea al final para poder pasarle el Sorter al buscador nativo
        add(createEnhancedHeader(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void setTransferCountListener(GenericCountListener<TransferPanel> listener) {
        this.countListener = listener;
    }

    private void updateCount() {
        if (countListener != null) {
            int activeCount = 0;
            for (int i = 0; i < model.getRowCount(); i++) {
                String estado = (String) model.getValueAt(i, 3);
                if (!"FINALIZADO".equals(estado) && !"CANCELADO".equals(estado)) {
                    activeCount++;
                }
            }
            countListener.onCountChanged(this, activeCount);
        }
    }

    private void setupRenderersAndEditors() {
        // Ocultar ID criptográfico por estética visual
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);

        // Renderer de estados con tipografía Bold y sincronización de hilos
        DefaultTableCellRenderer textRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, isS, hasF, r, c);
                l.setBorder(new EmptyBorder(0, 20, 0, 20));

                if (c == 3) { // Estado Lógico
                    String status = String.valueOf(v);
                    l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
                    switch (status) {
                        case "FINALIZADO" -> l.setForeground(SUCCESS_GREEN);
                        case "PAUSADO" -> l.setForeground(new Color(241, 196, 15));
                        case "CANCELADO" -> l.setForeground(new Color(231, 76, 60));
                        case "EN CURSO" -> l.setForeground(new Color(52, 152, 219));
                        default -> l.setForeground(new Color(149, 165, 166));
                    }
                }
                return l;
            }
        };

        // Renderizador premium para el Badge de Dirección de Tráfico
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            private final Color COLOR_SEND = new Color(0, 191, 255);
            private final Color COLOR_RECEIVE = new Color(187, 134, 252);

            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, isS, hasF, r, c);
                l.setBorder(new EmptyBorder(0, 15, 0, 15));
                l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));

                String val = String.valueOf(v).toUpperCase();
                l.setForeground(val.contains("ENVIANDO") ? COLOR_SEND : COLOR_RECEIVE);
                if (isS) l.setForeground(Color.WHITE);
                return l;
            }
        });

        // Inyección masiva de paddings y fuentes legibles
        for (int i = 1; i < 5; i++) {
            if (i == 2) continue;
            table.getColumnModel().getColumn(i).setCellRenderer(textRenderer);
        }
        table.getColumnModel().getColumn(6).setCellRenderer(textRenderer);

        // Barra de progreso y cálculo dinámico de velocidad + ETA
        table.getColumnModel().getColumn(5).setCellRenderer(new ProgressRenderer());

        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                super.getTableCellRendererComponent(t, v, isS, hasF, r, c);
                textRenderer.setBorder(new EmptyBorder(0, 15, 0, 15));

                if (v instanceof Double speed) {
                    int modelRow = t.convertRowIndexToModel(r);
                    String id = (String) t.getModel().getValueAt(modelRow, 0);
                    String eta = (String) t.getClientProperty("eta." + id);

                    if (speed <= 0) {
                        setText("---");
                    } else {
                        setText(String.format("%.2f MB/s (%s)", speed, (eta != null ? eta : "--:--")));
                    }
                }
                return this;
            }
        });

        // Inyección de Celda Controladora de Acciones Concurrente
        ActionCellHandler actionHandler = new ActionCellHandler();
        table.getColumnModel().getColumn(7).setCellRenderer(actionHandler);
        table.getColumnModel().getColumn(7).setCellEditor(actionHandler);
        table.getColumnModel().getColumn(7).setPreferredWidth(140);
    }

    private int getRowById(String id) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getValueAt(i, 0).equals(id)) return i;
        }
        return -1;
    }

    @Override
    public void addTransference(String mode, Transferencia t, TransferManager manager) {
        SwingUtilities.invokeLater(() -> {
            boolean isSending = mode.toUpperCase().contains("SEND");
            String tipoTexto = isSending ? "📤 ENVIANDO" : "📥 RECIBIENDO";

            rowMap.put(t.getId(), model.getRowCount());
            if (manager != null) managerMap.put(t.getId(), manager);

            model.addRow(new Object[]{
                    t.getId(),
                    t.getFileName().toUpperCase(),
                    tipoTexto,
                    "CONECTANDO...",
                    0.0,
                    0,
                    formatSize(t.getTamano()),
                    t.getId()
            });

            updateCount();
        });
    }

    @Override
    public void updateTransferenceFull(TransferProgress p) {
        Integer modelRowIndex = rowMap.get(p.id());
        if (modelRowIndex == null) return;

        long now = System.currentTimeMillis();
        long lastUpdate = lastUpdateMap.getOrDefault(p.id(), 0L);

        // Throttling estricto para mitigar lag y flickering en saturación de buffers de red
        if (p.percentage() < 100 && (now - lastUpdate < REFRESH_RATE_MS)) {
            return;
        }
        lastUpdateMap.put(p.id(), now);

        SwingUtilities.invokeLater(() -> {
            if (modelRowIndex >= model.getRowCount()) return; // Protección contra limpiezas en caliente

            model.setValueAt(p.speedMBs(), modelRowIndex, 4);
            table.putClientProperty("eta." + p.id(), p.eta());
            model.setValueAt(p.percentage(), modelRowIndex, 5);

            if (p.percentage() >= 100) {
                model.setValueAt("FINALIZADO", modelRowIndex, 3);
                model.setValueAt(0.0, modelRowIndex, 4);
                lastUpdateMap.remove(p.id());
                updateCount();
            } else if (!"EN CURSO".equals(model.getValueAt(modelRowIndex, 3)) && !"PAUSADO".equals(model.getValueAt(modelRowIndex, 3))) {
                model.setValueAt("EN CURSO", modelRowIndex, 3);
            }
        });
    }

    @Override
    public void endTransference(String m, String id) {
        SwingUtilities.invokeLater(() -> {
            int modelRow = getRowById(id);
            if (modelRow != -1) {
                model.setValueAt(100, modelRow, 5);
                model.setValueAt("FINALIZADO", modelRow, 3);
                model.setValueAt(0.0, modelRow, 4);
                lastUpdateMap.remove(id);
                updateCount();
            }
        });
    }

    @Override
    public boolean notifyTranference(FileHandshakeCommunication h) {
        final boolean[] accepted = {false};
        final int timeoutSeconds = 30;
        var info = h.getFileInfo();
        String sender = info.getSenderNick() != null ? info.getSenderNick() : "Nodo Remoto";

        try {
            SwingUtilities.invokeAndWait(() -> {
                JPanel panel = new JPanel(new BorderLayout(15, 15));
                panel.setOpaque(false);

                JLabel iconLabel = new JLabel("📥");
                iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
                panel.add(iconLabel, BorderLayout.WEST);

                JLabel lblTimer = new JLabel("Expiración en: " + timeoutSeconds + "s", SwingConstants.RIGHT);
                lblTimer.setFont(new Font("SansSerif", Font.BOLD, 11));
                lblTimer.setForeground(new Color(231, 76, 60));

                String htmlContent = String.format(
                        "<html><body style='font-family:sans-serif; color:#ecf0f1;'>" +
                                "<b style='font-size:13px; color:#ff8c00;'>SOLICITUD DE TRANSFERENCIA</b><br>" +
                                "<p style='margin-top:8px;'><b>Procedencia:</b> %s</p>" +
                                "<p><b>Descriptor:</b> <span style='color:#f1c40f;'>%s</span></p>" +
                                "<p><b>Carga Útil:</b> %s</p></body></html>",
                        sender, info.getName(), formatSize(info.getSize())
                );

                JLabel textLabel = new JLabel(htmlContent);
                JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 5));
                centerPanel.setOpaque(false);
                centerPanel.add(textLabel);
                centerPanel.add(lblTimer);
                panel.add(centerPanel, BorderLayout.CENTER);

                Object[] options = {"Aceptar Descarga", "Rechazar"};
                final JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.YES_NO_OPTION, null, options, options[0]);
                final JDialog dialog = optionPane.createDialog(this, "bitBridge Engine - Autenticación de Canal");

                final int[] timeLeft = {timeoutSeconds};
                Timer timer = new Timer(1000, e -> {
                    timeLeft[0]--;
                    lblTimer.setText("Expiración en: " + timeLeft[0] + "s");
                    if (timeLeft[0] <= 0) {
                        ((Timer)e.getSource()).stop();
                        dialog.dispose();
                    }
                });

                timer.start();
                dialog.setVisible(true);
                timer.stop();

                Object selectedValue = optionPane.getValue();
                accepted[0] = (selectedValue != null && selectedValue.equals(options[0]));
            });
        } catch (Exception e) {
            return false;
        }
        return accepted[0];
    }

    @Override
    public void notifyTranference(FileHandshakeAction a) {
        SwingUtilities.invokeLater(() -> {
            switch (a) {
                case DECLINE_REQUEST -> showToast("Transferencia rechazada por el receptor", new Color(231, 76, 60));
                case ERROR_TIMEOUT -> showToast("La solicitud ha expirado por inactividad", new Color(241, 196, 15));
                case SERVER_BUSY -> showToast("Conexión interrumpida con el nodo remoto", new Color(149, 165, 166));
                case ACCEPT_REQUEST -> showToast("¡Solicitud aceptada! Sincronizando sockets...", SUCCESS_GREEN);
            }
        });
    }

    private void showToast(String message, Color accentColor) {
        JPanel toast = new JPanel(new BorderLayout(10, 0));
        toast.setBackground(new Color(40, 42, 44));
        toast.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentColor, 1),
                new EmptyBorder(8, 15, 8, 15)
        ));

        JLabel lblMsg = new JLabel(message);
        lblMsg.setForeground(Color.WHITE);
        lblMsg.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JLabel lblIcon = new JLabel("ℹ");
        lblIcon.setForeground(accentColor);
        lblIcon.setFont(new Font("SansSerif", Font.BOLD, 14));

        toast.add(lblIcon, BorderLayout.WEST);
        toast.add(lblMsg, BorderLayout.CENTER);

        JLayeredPane layeredPane = getRootPane().getLayeredPane();
        toast.setSize(toast.getPreferredSize());

        int x = (getWidth() - toast.getWidth()) / 2;
        int y = getHeight() - toast.getHeight() - 50;
        toast.setLocation(x, y);

        layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
        layeredPane.repaint();

        Timer timer = new Timer(3000, e -> {
            layeredPane.remove(toast);
            layeredPane.repaint();
        });
        timer.setRepeats(false);
        timer.start();
    }

    @Override
    public void notifyException(String m) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, m, "Excepción crítica en Canal", JOptionPane.ERROR_MESSAGE));
    }

    @Override public void updateTransference(FileTransferState s, String id, int p) {}

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private JPanel createEnhancedHeader() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 15, 0));

        JLabel lblMain = new JLabel("Canales de Transferencia");
        lblMain.setFont(new Font("Inter", Font.BOLD, 22));
        lblMain.setForeground(Color.WHITE);
        header.add(lblMain, BorderLayout.WEST);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        toolbar.setOpaque(false);

        // 1. Buscador nativo acoplado al RowSorter mediante Regex
        JTextField searchField = new JTextField(15);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Filtrar por archivo...");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String text = searchField.getText();
                if (text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 1)); // Filtra sobre columna ARCHIVO (1)
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });

        // 2. Control de Limite de Tráfico Global
        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        limitPanel.setOpaque(false);
        JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(100, 0, 10000, 10));
        limitSpinner.setPreferredSize(new Dimension(80, 30));
        limitPanel.add(new JLabel("Límite:"));
        limitPanel.add(limitSpinner);
        limitPanel.add(new JLabel("MB/s"));

        // Accionador de limitación reactiva sobre los Managers de bitBridge
        limitSpinner.addChangeListener(e -> {
            int limitMBs = (int) limitSpinner.getValue();
            for (TransferManager m : managerMap.values()) {
                // Si tu TransferManager soporta limitación por Throttling:
                // m.setSpeedLimit(limitMBs * 1024 * 1024);
            }
        });

        // 3. Botones de Control de Lotes Atómicos
        JButton btnPauseAll = new JButton("Pausar Todo");
        styleSecondaryBtn(btnPauseAll);
        btnPauseAll.addActionListener(e -> {
            for (TransferManager m : managerMap.values()) {
                m.pause();
            }
            for (int i = 0; i < model.getRowCount(); i++) {
                if ("EN CURSO".equals(model.getValueAt(i, 3))) model.setValueAt("PAUSADO", i, 3);
            }
        });

        // LÓGICA COMPLETA DE LIMPIEZA MUTABLE DE FILAS TERMINADAS
        JButton btnClear = new JButton("Limpiar Terminados");
        styleSecondaryBtn(btnClear);
        btnClear.addActionListener(e -> {
            // Iteramos de abajo hacia arriba para evitar colapsos de índices lógicos
            for (int i = model.getRowCount() - 1; i >= 0; i--) {
                String estado = (String) model.getValueAt(i, 3);
                if ("FINALIZADO".equals(estado) || "CANCELADO".equals(estado)) {
                    String id = (String) model.getValueAt(i, 0);
                    rowMap.remove(id);
                    managerMap.remove(id);
                    lastUpdateMap.remove(id);
                    model.removeRow(i);
                }
            }
            // Re-mapear el rowMap para reflejar los nuevos índices reales de la tabla limpia
            rowMap.clear();
            for (int i = 0; i < model.getRowCount(); i++) {
                rowMap.put((String) model.getValueAt(i, 0), i);
            }
            updateCount();
        });

        toolbar.add(searchField);
        toolbar.add(new JSeparator(JSeparator.VERTICAL));
        toolbar.add(limitPanel);
        toolbar.add(btnPauseAll);
        toolbar.add(btnClear);

        header.add(toolbar, BorderLayout.CENTER);
        return header;
    }

    private void styleSecondaryBtn(JButton btn) {
        btn.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; background: #3d4446; foreground: #ffffff; focusWidth: 0; margin: 2,10,2,10");
    }

    // --- MANEJO DE CELDAS DINÁMICAS (RENDERERS Y EDITORES DE BOTONES) ---
    private class ActionCellHandler extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
        private String editingId;

        private void handleAction(String action, String id) {
            TransferManager m = managerMap.get(id);
            int modelRow = getRowById(id);

            if (action.equals("CANCEL")) {
                if (m != null) m.cancel();
                if (modelRow != -1) {
                    model.setValueAt("CANCELADO", modelRow, 3);
                    model.setValueAt(0.0, modelRow, 4);
                }
                managerMap.remove(id);
                lastUpdateMap.remove(id);
                fireEditingStopped();
                updateCount();
                return;
            }

            if (m == null) {
                fireEditingStopped();
                return;
            }

            if (action.equals("PAUSE")) {
                m.pause();
                if (modelRow != -1) model.setValueAt("PAUSADO", modelRow, 3);
            } else if (action.equals("RESUME")) {
                m.resume();
                if (modelRow != -1) model.setValueAt("EN CURSO", modelRow, 3);
            }

            SwingUtilities.invokeLater(() -> {
                if (modelRow != -1) {
                    fireEditingStopped();
                    model.fireTableCellUpdated(modelRow, 3);
                    model.fireTableCellUpdated(modelRow, 7);
                }
            });
        }

        private Component updatePanel(JTable table, int row, boolean isSelected, String id) {
            JPanel renderPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
            renderPanel.setOpaque(isSelected);
            if (isSelected) renderPanel.setBackground(table.getSelectionBackground());

            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow >= model.getRowCount()) return renderPanel;

            String estado = model.getValueAt(modelRow, 3).toString();
            int progreso = (int) model.getValueAt(modelRow, 5);

            if (progreso >= 100 || "FINALIZADO".equals(estado)) {
                JLabel lbl = new JLabel("✔ COMPLETO");
                lbl.setForeground(SUCCESS_GREEN);
                lbl.setFont(new Font("Inter", Font.BOLD, 11));
                renderPanel.add(lbl);
            } else if ("CANCELADO".equals(estado)) {
                JLabel lbl = new JLabel("✕ CANCELADO");
                lbl.setForeground(new Color(231, 76, 60));
                lbl.setFont(new Font("Inter", Font.BOLD, 11));
                renderPanel.add(lbl);
            } else {
                if ("PAUSADO".equals(estado)) {
                    JButton resume = createBtn("▶", "#16a34a", "Reanudar");
                    resume.addActionListener(e -> handleAction("RESUME", id));
                    renderPanel.add(resume);
                } else {
                    JButton pause = createBtn("⏸", "#d97706", "Pausar");
                    pause.addActionListener(e -> handleAction("PAUSE", id));
                    renderPanel.add(pause);
                }

                JButton cancel = createBtn("✕", "#ef4444", "Cancelar");
                cancel.addActionListener(e -> handleAction("CANCEL", id));
                renderPanel.add(cancel);
            }

            return renderPanel;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
            return updatePanel(t, r, isS, (String) v);
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object v, boolean isS, int r, int c) {
            this.editingId = (String) v;
            return updatePanel(t, r, true, this.editingId);
        }

        @Override public Object getCellEditorValue() { return editingId; }

        private JButton createBtn(String icon, String color, String tip) {
            JButton b = new JButton(icon);
            b.setToolTipText(tip);
            b.putClientProperty(FlatClientProperties.STYLE,
                    "arc: 10; background: " + color + "; foreground: #ffffff; borderPainted: false; focusWidth: 0");
            b.setPreferredSize(new Dimension(32, 26));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return b;
        }
    }

    // --- BARRAS DE PROGRESO DE ALTO CONTRASTE (FLATLAF ARCS) ---
    private static class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressRenderer() {
            super(0, 100);
            this.setStringPainted(true);
            this.putClientProperty(FlatClientProperties.PROGRESS_BAR_SQUARE, false);
            this.putClientProperty("JProgressBar.largeHeight", true);
            this.setBorder(new EmptyBorder(8, 10, 8, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
            int val = (v instanceof Integer) ? (Integer) v : 0;
            setValue(val);

            if (val == 100) {
                setForeground(SUCCESS_GREEN.darker());
                setString("100 %");
            } else {
                setForeground(NICOTINE_ORANGE);
                setString(val + " %");
            }
            setBackground(CARD_BG);
            return this;
        }
    }
}
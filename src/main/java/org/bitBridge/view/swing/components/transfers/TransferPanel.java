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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransferPanel extends JPanel implements TransferencesObserver {

    private GenericCountListener<TransferPanel> countListener;

    private JTable table;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;

    // CORRECCIÓN CONCURRENTE: Evitamos excepciones ConcurrentModificationException con los hilos de red
    private final Map<String, Integer> rowMap = new ConcurrentHashMap<>();
    private final Map<String, TransferManager> managerMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateMap = new ConcurrentHashMap<>();
    private static final int REFRESH_RATE_MS = 150;

    private final Color COLOR_TARJETA = new Color(45, 52, 54);
    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);
    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private final Color COLOR_SUCCESS = new Color(46, 204, 113);
    private static final Color BG_DARKER = new Color(25, 25, 25);

    public TransferPanel() {
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(new EmptyBorder(20, 25, 20, 25));
        initComponents();
    }

    private void initComponents() {
        add(createEnhancedHeader(), BorderLayout.NORTH);

        String[] cols = {"ID", "ARCHIVO", "TIPO", "ESTADO", "VELOCIDAD", "PROGRESO", "TAMAÑO", "ACCIONES"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) {
                return c == 7;
            }
            @Override public Class<?> getColumnClass(int c) {
                if (c == 5) return Integer.class;
                return Object.class;
            }
        };

        table = new JTable(model);
        table.setRowHeight(55);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        table.putClientProperty(FlatClientProperties.STYLE,
                "selectionBackground: #2d3a4d; selectionForeground: #ffffff");
        setupRenderersAndEditors();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new com.formdev.flatlaf.ui.FlatLineBorder(
                new Insets(0, 0, 0, 0),
                UIManager.getColor("Component.borderColor"),
                1,
                20
        ));

        add(scroll, BorderLayout.CENTER);
    }

    public void setTransferCountListener(GenericCountListener<TransferPanel> listener) {
        this.countListener = listener;
    }

    private void updateCount() {
        if (countListener != null) {
            int activeCount = 0;
            for (int i = 0; i < model.getRowCount(); i++) {
                if (!"FINALIZADO".equals(model.getValueAt(i, 3))) {
                    activeCount++;
                }
            }
            countListener.onCountChanged(this, activeCount);
        }
    }

    private void setupRenderersAndEditors() {
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);

        DefaultTableCellRenderer textRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, isS, hasF, r, c);
                l.setBorder(new EmptyBorder(0, 20, 0, 20));

                if (c == 3) {
                    String status = String.valueOf(v);
                    l.setFont(l.getFont().deriveFont(Font.BOLD));
                    if (status.equals("FINALIZADO")) l.setForeground(new Color(46, 204, 113));
                    else if (status.contains("PAUSADO")) l.setForeground(new Color(241, 196, 15));
                    else l.setForeground(new Color(52, 152, 219));
                }
                return l;
            }
        };

        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            private final Color COLOR_SEND = new Color(0, 191, 255);
            private final Color COLOR_RECEIVE = new Color(187, 134, 252);
            private final Color COLOR_TEXT_BASE = new Color(220, 220, 220);

            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, isS, hasF, r, c);
                l.setBorder(new EmptyBorder(0, 15, 0, 15));
                l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));

                String val = String.valueOf(v).toUpperCase();
                if (val.contains("ENVIANDO")) {
                    l.setForeground(COLOR_SEND);
                } else if (val.contains("RECIBIENDO")) {
                    l.setForeground(COLOR_RECEIVE);
                } else {
                    l.setForeground(COLOR_TEXT_BASE);
                }

                if (isS) l.setForeground(Color.WHITE);
                return l;
            }
        });

        for (int i = 1; i < 5; i++) {
            if (i == 2) continue;
            table.getColumnModel().getColumn(i).setCellRenderer(textRenderer);
        }
        table.getColumnModel().getColumn(6).setCellRenderer(textRenderer);
        table.getColumnModel().getColumn(5).setCellRenderer(new ProgressRenderer());

        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                super.getTableCellRendererComponent(t, v, isS, hasF, r, c);
                if (v instanceof Double speed) {
                    int modelRow = t.convertRowIndexToModel(r);
                    String id = (String) t.getModel().getValueAt(modelRow, 0);
                    String eta = (String) t.getClientProperty("eta." + id);

                    if (speed <= 0) {
                        setText("---");
                    } else {
                        setText(String.format("%.2f MB/s (%s)", speed, eta));
                    }
                }
                return this;
            }
        });

        // --- ENLAZAR HANDLER CORREGIDO ---
        ActionCellHandler actionHandler = new ActionCellHandler();
        table.getColumnModel().getColumn(7).setCellRenderer(actionHandler);
        table.getColumnModel().getColumn(7).setCellEditor(actionHandler);
        table.getColumnModel().getColumn(7).setPreferredWidth(120);
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
            String tipoTexto = isSending ? "ENVIANDO" : "RECIBIENDO";

            rowMap.put(t.getId(), model.getRowCount());
            managerMap.put(t.getId(), manager);

            model.addRow(new Object[]{
                    t.getId(),
                    t.getFileName().toUpperCase(),
                    tipoTexto,
                    "CONECTANDO...",
                    "---",
                    0,
                    formatSize(t.getTamano()),
                    t.getId()
            });

            updateCount();
        });
    }

    @Override
    public void updateTransferenceFull(TransferProgress p) {
        if (p == null || p.id() == null) return;

        long now = System.currentTimeMillis();
        long lastUpdate = lastUpdateMap.getOrDefault(p.id(), 0L);

        if (p.percentage() < 100 && (now - lastUpdate < REFRESH_RATE_MS)) {
            return;
        }
        lastUpdateMap.put(p.id(), now);

        SwingUtilities.invokeLater(() -> {
            Integer currentModelRowIndex = rowMap.get(p.id());
            if (currentModelRowIndex == null) return;

            model.setValueAt(p.speedMBs(), currentModelRowIndex, 4);
            table.putClientProperty("eta." + p.id(), p.eta());
            model.setValueAt(p.percentage(), currentModelRowIndex, 5);

            if (p.percentage() >= 100) {
                model.setValueAt("FINALIZADO", currentModelRowIndex, 3);
                lastUpdateMap.remove(p.id());
            } else {
                Object estadoActual = model.getValueAt(currentModelRowIndex, 3);
                // Si la red está activa y la UI dice PAUSADO, no sobreescribir hasta que reanude en el backend
                if (!"EN CURSO".equals(estadoActual) && !"PAUSADO".equals(estadoActual)) {
                    model.setValueAt("EN CURSO", currentModelRowIndex, 3);
                }
            }
        });
    }

    @Override
    public void endTransference(String m, String id) {
        SwingUtilities.invokeLater(() -> {
            Integer row = rowMap.get(id);
            if (row != null) {
                int mRow = table.convertRowIndexToModel(row);
                model.setValueAt(100, mRow, 5);
                model.setValueAt("FINALIZADO", mRow, 3);
                model.setValueAt("---", mRow, 4);
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
                lblTimer.setFont(new Font("SansSerif", Font.BOLD, 10));
                lblTimer.setForeground(new Color(231, 76, 60));

                String htmlContent = String.format(
                        "<html><div style='font-family:SansSerif; color:#ecf0f1;'>" +
                                "<b style='font-size:13px; color:#3498db;'>SOLICITUD DE ENTRADA</b><br>" +
                                "<p style='margin-top:5px;'><b>Origen:</b> %s</p>" +
                                "<p><b>Archivo:</b> <span style='color:#f1c40f;'>%s</span></p>" +
                                "<p><b>Tamaño:</b> %s</p></div></html>",
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
                final JDialog dialog = optionPane.createDialog(this, "BitBridge - Alerta de Transferencia");

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
                case SERVER_BUSY -> showToast("Conexión perdida con el nodo remoto", new Color(149, 165, 166));
                case ACCEPT_REQUEST -> showToast("¡Solicitud aceptada! Iniciando descarga...", new Color(46, 204, 113));
            }
        });
    }

    private void showToast(String message, Color accentColor) {
        JPanel toast = new JPanel(new BorderLayout(10, 0));
        toast.setBackground(new Color(40, 42, 44));
        toast.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentColor, 1),
                new javax.swing.border.EmptyBorder(8, 15, 8, 15)
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
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, m, "Error", JOptionPane.ERROR_MESSAGE));
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

        JLabel lblMain = new JLabel("Transferencias");
        lblMain.setFont(new Font("SansSerif", Font.BOLD, 20));
        header.add(lblMain, BorderLayout.WEST);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        toolbar.setOpaque(false);

        JTextField searchField = new JTextField(15);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Filtrar archivos...");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon());

        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        limitPanel.setOpaque(false);
        JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(100, 0, 10000, 10));
        limitSpinner.setPreferredSize(new Dimension(80, 30));
        limitPanel.add(new JLabel("Límite:"));
        limitPanel.add(limitSpinner);
        limitPanel.add(new JLabel("MB/s"));

        JButton btnClear = new JButton("Limpiar Terminados");
        styleSecondaryBtn(btnClear);

        JButton btnPauseAll = new JButton("Pausar Todo");
        styleSecondaryBtn(btnPauseAll);

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
                "arc: 8; background: #3d4446; focusWidth: 0; margin: 2,10,2,10");
    }

    // =========================================================================
    // --- CLASE INTERNA DE CONTROLADORES DE ACCIÓN (RENDERING/EDITING FIX) ---
    // =========================================================================
    private class ActionCellHandler extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {

        private String editingId;

        public ActionCellHandler() {}

        private void handleAction(String action, String id) {
            TransferManager m = managerMap.get(id);
            if (m == null) {
                cancelCellEditing();
                return;
            }

            // 1. Ejecutar inmediatamente la acción de control en el Backend
            executeTransferAction(m, action);

            // CORRECCIÓN ATÓMICA: Cerramos la edición ANTES de actualizar la UI
            // Esto destruye el editor interactivo viejo y evita el bug del doble clic
            SwingUtilities.invokeLater(() -> {
                cancelCellEditing();

                int modelRow = getRowById(id);
                if (modelRow != -1) {
                    updateModelStatus(modelRow, action);
                    // Forzamos la notificación de mutación en la celda del estado (3) y de los botones (7)
                    model.fireTableCellUpdated(modelRow, 3);
                    model.fireTableCellUpdated(modelRow, 7);
                }
            });
        }

        private void executeTransferAction(TransferManager m, String action) {
            // Sincronizado con los nombres de métodos reales de tu backend de red
            switch (action) {
                case "PAUSE" -> m.pause();
                case "RESUME" -> m.resume();
                case "CANCEL" -> m.cancel();
            }
        }

        private void updateModelStatus(int modelRow, String action) {
            String nuevoEstado = switch (action) {
                case "PAUSE" -> "PAUSADO";
                case "RESUME" -> "EN CURSO";
                case "CANCEL" -> "CANCELADO";
                default -> (String) model.getValueAt(modelRow, 3);
            };
            model.setValueAt(nuevoEstado, modelRow, 3);
        }

        private Component updatePanel(JTable table, int row, boolean isSelected, String id) {
            JPanel renderPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
            renderPanel.setOpaque(isSelected);
            if (isSelected) renderPanel.setBackground(table.getSelectionBackground());

            int modelRow = table.convertRowIndexToModel(row);
            String estado = model.getValueAt(modelRow, 3).toString();
            int progreso = (int) model.getValueAt(modelRow, 5);

            if (progreso >= 100 || "FINALIZADO".equals(estado) || "CANCELADO".equals(estado)) {
                JLabel lbl = new JLabel("CANCELADO".equals(estado) ? "✕" : "✔");
                lbl.setForeground("CANCELADO".equals(estado) ? new Color(231, 76, 60) : new Color(46, 204, 113));
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
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
            if (v == null) return new JLabel();
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
            setupBtn(b, color);
            return b;
        }

        private void setupBtn(JButton b, String colorHex) {
            b.putClientProperty(FlatClientProperties.STYLE,
                    "arc: 10; background: " + colorHex + "; foreground: #ffffff; borderPainted: false; focusWidth: 0");
            b.setPreferredSize(new Dimension(30, 26));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
    }

    private static class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressRenderer() {
            super(0, 100);
            this.setStringPainted(true);
            this.putClientProperty(FlatClientProperties.PROGRESS_BAR_SQUARE, false);
            this.putClientProperty("JComponent.outline", null);
            this.putClientProperty("JProgressBar.largeHeight", true);
            this.setBorder(new EmptyBorder(8, 10, 8, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
            int val = (v instanceof Integer) ? (Integer) v : 0;
            setValue(val);

            if (val == 100) {
                setForeground(SUCCESS_GREEN.darker());
                setString("Completado");
            } else {
                setForeground(NICOTINE_ORANGE.darker());
                setString(val + " %");
            }
            setBackground(new Color(45, 52, 54));
            return this;
        }
    }
}
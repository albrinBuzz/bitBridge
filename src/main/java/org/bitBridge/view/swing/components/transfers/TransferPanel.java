package org.bitBridge.view.swing.components.transfers;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;
import org.bitBridge.Client.TransferManager;
import org.bitBridge.Observers.GenericCountListener;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.models.TransferProgress;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.*;

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

public class TransferPanel extends JPanel implements TransferencesObserver {

    private GenericCountListener<TransferPanel> countListener;

    private JTable table;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;
    private final Map<String, Integer> rowMap = new HashMap<>();
    private final Map<String, TransferManager> managerMap = new HashMap<>();
    private final Map<String, Long> lastUpdateMap = new HashMap<>();
    private static final int REFRESH_RATE_MS = 150; // El ojo humano no percibe cambios más rápidos

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

        // Añadimos la columna "ACCIONES" (Índice 7)
        String[] cols = {"ID", "ARCHIVO", "TIPO", "ESTADO", "VELOCIDAD", "PROGRESO", "TAMAÑO", "ACCIONES"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) {
                return c == 7; // Solo la columna de botones es editable
            }
            @Override public Class<?> getColumnClass(int c) {
                if (c == 5) return Integer.class;
                return Object.class;
            }
        };
        //sorter = new TableRowSorter<>(model);

        //sorter.setSortsOnUpdates(false); // <--- ESTO evita que la tabla salte y consuma CPU al actualizar
        table = new JTable(model);
        //table.setRowSorter(sorter);
        table.setRowHeight(55); // Aumentado para los botones
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));


        table.putClientProperty(FlatClientProperties.STYLE,
                "selectionBackground: #2d3a4d; selectionForeground: #ffffff");
        setupRenderersAndEditors();

        JScrollPane scroll = new JScrollPane(table);
        //scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBorder(new com.formdev.flatlaf.ui.FlatLineBorder(
                new Insets(0, 0, 0, 0),
                UIManager.getColor("Component.borderColor"), // Color del borde del tema
                1, // Grosor
                20 // ESTE ES EL ARC (Redondeo)
        ));
        //scroll.putClientProperty(FlatClientProperties.STYLE, "arc: 20");
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel createTransferToolbar() {

        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tb.add(new JButton("▶ Iniciar"));
        tb.add(new JButton("⏸ Pausar"));
        tb.add(new JButton("⏹ Detener"));
        tb.add(new JSeparator(SwingConstants.VERTICAL));
        tb.add(new JLabel("Límite Global:"));
        tb.add(new JSpinner(new SpinnerNumberModel(100, 0, 10000, 10)));
        tb.add(new JLabel("MB/s"));
        return tb;
    }

    public void setTransferCountListener(GenericCountListener<TransferPanel> listener) {
        this.countListener = listener;
    }

    private void updateCount() {
        if (countListener != null) {
            // Contamos cuántas no están finalizadas, o simplemente el total
            int activeCount = 0;
            for (int i = 0; i < model.getRowCount(); i++) {
                if (!"FINALIZADO".equals(model.getValueAt(i, 3))) {
                    activeCount++;
                }
            }
            //countListener.onCountChanged(activeCount);
            countListener.onCountChanged(this, activeCount);
        }else {
            Logger.logInfo("CountListener Vacio");
        }
    }

    private void setupRenderersAndEditors() {
        // Ocultar ID
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);

        // Renderers de texto
        // Dentro de setupRenderersAndEditors
        DefaultTableCellRenderer textRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, isS, hasF, r, c);
                l.setBorder(new EmptyBorder(0, 20, 0, 20)); // Más padding lateral

                if (c == 3) { // Columna ESTADO
                    String status = String.valueOf(v);
                    l.setFont(l.getFont().deriveFont(Font.BOLD)); // Resaltar estado
                    if (status.equals("FINALIZADO")) l.setForeground(new Color(46, 204, 113));
                    else if (status.contains("PAUSADO")) l.setForeground(new Color(241, 196, 15));
                    else l.setForeground(new Color(52, 152, 219));
                }
                return l;
            }
        };

        for (int i = 1; i < 5; i++) table.getColumnModel().getColumn(i).setCellRenderer(textRenderer);

        table.getColumnModel().getColumn(6).setCellRenderer(textRenderer);

        // Progreso
        table.getColumnModel().getColumn(5).setCellRenderer(new ProgressRenderer());

        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                super.getTableCellRendererComponent(t, v, isS, hasF, r, c);

                if (v instanceof Double speed) {
                    // Obtenemos el ID de la fila para buscar su ETA
                    int modelRow = t.convertRowIndexToModel(r);
                    String id = (String) t.getModel().getValueAt(modelRow, 0);
                    String eta = (String) t.getClientProperty("eta." + id);

                    if (speed <= 0) {
                        setText("---");
                    } else {
                        // Formato: "1.50 MB/s (00:15)"
                        setText(String.format("%.2f MB/s (%s)", speed, eta));
                    }
                }
                return this;
            }
        });

        // --- COLUMNA DE ACCIONES (Botones) ---
        ActionCellHandler actionHandler = new ActionCellHandler();
        table.getColumnModel().getColumn(7).setCellRenderer(actionHandler);
        table.getColumnModel().getColumn(7).setCellEditor(actionHandler);
        table.getColumnModel().getColumn(7).setPreferredWidth(120);
    }

    // --- LÓGICA DEL OBSERVER (Igual a la clase anterior) ---

    @Override
    public void addTransference(String mode, Transferencia t, TransferManager manager) {
        SwingUtilities.invokeLater(() -> {
            rowMap.put(t.getId(), model.getRowCount());
            managerMap.put(t.getId(), manager);
            model.addRow(new Object[]{
                    t.getId(),
                    t.getFileName().toUpperCase(),
                    mode.contains("SEND") ? "📤 Enviando" : "📥 Recibiendo",
                    "PENDIENTE",
                    "0.0 MB/s",
                    0,
                    //formatSize(t.getTamano()),
                    887,
                    t.getId() // Pasamos el ID a la celda de acciones


            });

            updateCount();
        });
    }

    // Sustituye tu método updateTransferenceFull por este:
    @Override
    public void updateTransferenceFull(TransferProgress p) {
        Integer modelRowIndex = rowMap.get(p.id());
        if (modelRowIndex == null) return;

        long now = System.currentTimeMillis();
        long lastUpdate = lastUpdateMap.getOrDefault(p.id(), 0L);

        // OPTIMIZACIÓN 1: Throttling estricto
        if (p.percentage() < 100 && (now - lastUpdate < REFRESH_RATE_MS)) {
            return;
        }
        lastUpdateMap.put(p.id(), now);

        // OPTIMIZACIÓN 2: Usar invokeLater de forma segura para no saturar el Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            // OPTIMIZACIÓN 3: Evitar el redibujado de TODA la fila.
            // Solo notificamos que cambiaron las celdas de VELOCIDAD (4) y PROGRESO (5)
            model.setValueAt(p.speedMBs(), modelRowIndex, 4);

            table.putClientProperty("eta." + p.id(), p.eta());

            model.setValueAt(p.percentage(), modelRowIndex, 5);

            // Solo cambiamos el estado si realmente cambió (ahorra eventos de tabla)
            if (p.percentage() >= 100) {
                model.setValueAt("FINALIZADO", modelRowIndex, 3);
                lastUpdateMap.remove(p.id());
            } else if (!"EN CURSO".equals(model.getValueAt(modelRowIndex, 3))) {
                model.setValueAt("EN CURSO", modelRowIndex, 3);
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

    // --- NOTIFICACIONES (Copiadas de tu clase anterior) ---

    @Override
    public boolean notifyTranference(FileHandshakeCommunication h) {
        final boolean[] accepted = {false};
        final int timeoutSeconds = 30;
        var info = h.getFileInfo();
        String sender = info.getSenderNick() != null ? info.getSenderNick() : "Nodo Remoto";

        // --- EFECTO DE SONIDO ---
        //playNotificationSound();
        try {
            generateTone(880, 100, 0.1);
        } catch (LineUnavailableException e) {
            Logger.logInfo(e.getMessage());
            //throw new RuntimeException(e);
        }

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

                // Timer para cerrar el diálogo
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

    /**
     * Lógica para reproducir el sonido de notificación
     */
    private void playNotificationSound() {
        try {
            // Opción 1: Beep básico del sistema (Rápido y sin archivos externos)
            Toolkit.getDefaultToolkit().beep();

        /* // Opción 2: Cargar un archivo .wav personalizado (Suave/Moderno)
        // Necesitas un archivo en src/main/resources/sounds/notification.wav
        InputStream is = getClass().getResourceAsStream("/sounds/notification.wav");
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
        Clip clip = AudioSystem.getClip();
        clip.open(audioStream);
        clip.start();
        */
        } catch (Exception e) {
            System.err.println("No se pudo reproducir el sonido: " + e.getMessage());
        }
    }

    public  void generateTone(int hz, int msecs, double vol) throws LineUnavailableException {
        float sampleRate = 8000f;
        byte[] buf = new byte[1];
        // Definir el formato de audio (8-bit, Mono, Signed)
        AudioFormat af = new AudioFormat(sampleRate, 8, 1, true, false);
        SourceDataLine sdl = AudioSystem.getSourceDataLine(af);

        sdl.open(af);
        sdl.start();

        for (int i = 0; i < msecs * (sampleRate / 1000); i++) {
            double angle = i / (sampleRate / hz) * 2.0 * Math.PI;
            buf[0] = (byte) (Math.sin(angle) * 127.0 * vol);
            sdl.write(buf, 0, 1);
        }

        sdl.drain(); // Espera a que termine de sonar
        sdl.stop();
        sdl.close();
    }

    @Override
    public void notifyTranference(FileHandshakeAction a) {
        SwingUtilities.invokeLater(() -> {
            switch (a) {
                case DECLINE_REQUEST:
                    showToast("Transferencia rechazada por el receptor", new Color(231, 76, 60)); // Rojo
                    break;
                case ERROR_TIMEOUT:
                    showToast("La solicitud ha expirado por inactividad", new Color(241, 196, 15)); // Amarillo
                    break;
                case SERVER_BUSY:
                    showToast("Conexión perdida con el nodo remoto", new Color(149, 165, 166)); // Gris
                    break;
                case ACCEPT_REQUEST:
                    showToast("¡Solicitud aceptada! Iniciando descarga...", new Color(46, 204, 113)); // Verde
                    break;
            }
        });
    }
    private void showToast(String message, Color accentColor) {
        // Creamos un panel flotante estilo "Snackbar"
        JPanel toast = new JPanel(new BorderLayout(10, 0));
        toast.setBackground(new Color(40, 42, 44)); // Fondo oscuro
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

        // Lógica para posicionar el toast en el cristal (GlassPane) de la ventana
        JLayeredPane layeredPane = getRootPane().getLayeredPane();
        toast.setSize(toast.getPreferredSize());

        // Posición: Centro-Abajo
        int x = (getWidth() - toast.getWidth()) / 2;
        int y = getHeight() - toast.getHeight() - 50;
        toast.setLocation(x, y);

        layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
        layeredPane.repaint();

        // Timer para desvanecer y eliminar
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

    // --- COMPONENTES INTERNOS ---


    private JPanel createEnhancedHeader() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 15, 0));

        // --- LADO IZQUIERDO: Título y Contador ---
        JLabel lblMain = new JLabel("Transferencias");
        lblMain.setFont(new Font("SansSerif", Font.BOLD, 20));
        header.add(lblMain, BorderLayout.WEST);

        // --- LADO DERECHO: Herramientas ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        toolbar.setOpaque(false);

        // 1. Buscador (Muy útil para listas largas)
        JTextField searchField = new JTextField(15);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Filtrar archivos...");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon()); // Si tienes iconos

        // 2. Spinner de Límite Global (Más compacto)
        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        limitPanel.setOpaque(false);
        JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(100, 0, 10000, 10));
        limitSpinner.setPreferredSize(new Dimension(80, 30));
        limitPanel.add(new JLabel("Límite:"));
        limitPanel.add(limitSpinner);
        limitPanel.add(new JLabel("MB/s"));

        // 3. Botones de Acción Global
        JButton btnClear = new JButton("Limpiar Terminados");
        styleSecondaryBtn(btnClear);

        JButton btnPauseAll = new JButton("Pausar Todo");
        styleSecondaryBtn(btnPauseAll);

        // Agregar componentes al toolbar
        toolbar.add(searchField);
        toolbar.add(new JSeparator(JSeparator.VERTICAL));
        toolbar.add(limitPanel);
        toolbar.add(btnPauseAll);
        toolbar.add(btnClear);

        header.add(toolbar, BorderLayout.CENTER);
        return header;
    }

    // Método auxiliar para que los botones de la barra no compitan con la tabla
    private void styleSecondaryBtn(JButton btn) {
        btn.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; " +
                        "background: #3d4446; " +
                        "focusWidth: 0; " +
                        "margin: 2,10,2,10");
    }

    // --- HANDLER PARA BOTONES EN LA TABLA ---
    private class ActionCellHandler extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
        private final JButton btnPause = new JButton("⏸");
        private final JButton btnResume = new JButton("▶");
        private final JButton btnCancel = new JButton("✕");
        private String editingId; // ID específico para la celda en edición

        public ActionCellHandler() {
            panel.setOpaque(false);
            setupBtn(btnPause, "#d97706");
            setupBtn(btnResume, "#16a34a");
            setupBtn(btnCancel, "#ef4444");

            btnPause.addActionListener(e -> handleAction("PAUSE"));
            btnResume.addActionListener(e -> handleAction("RESUME"));
            btnCancel.addActionListener(e -> handleAction("CANCEL"));

            panel.add(btnPause);
            panel.add(btnResume);
            panel.add(btnCancel);
        }

        private void handleAction(String action) {
            TransferManager m = managerMap.get(editingId);
            if (m != null) {
                switch (action) {
                    case "PAUSE" -> m.pause();
                    case "RESUME" -> m.resume();
                    case "CANCEL" -> m.cancel();
                }

                // Forzar actualización visual inmediata del estado en el modelo
                // Usamos rowMap para encontrar la fila correcta de forma segura
                Integer modelRow = rowMap.get(editingId);
                if (modelRow != null) {
                    model.setValueAt(action.equals("PAUSE") ? "PAUSADO" : "EN CURSO", modelRow, 3);
                }
            }
            // CRÍTICO: Detener la edición para que la tabla repinte los botones correctamente
            fireEditingStopped();
        }

        private void setupBtn(JButton b, String colorHex) {
            b.putClientProperty(FlatClientProperties.STYLE,
                    "arc: 10; " +
                            "background: " + colorHex + "; " +
                            "foreground: #ffffff; " +
                            "borderPainted: false; " +
                            "focusWidth: 0; " +
                            "hoverBackground: darken(" + colorHex + ", 10%)");
            b.setPreferredSize(new Dimension(30, 26));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
            int modelRow = t.convertRowIndexToModel(r);
            int progress = (int) model.getValueAt(modelRow, 5);
            btnPause.setEnabled(progress < 100);
            btnResume.setEnabled(progress < 100);
            return panel;
        }



        @Override
        public Component getTableCellEditorComponent(JTable t, Object v, boolean isS, int r, int c) {
            this.editingId = (String) v;
            return panel;
        }

        @Override public Object getCellEditorValue() { return editingId; }
    }

    private static class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressRenderer() {
            super(0, 100);
            this.setStringPainted(true);
            // Hacemos que la barra sea más sutil y moderna
            this.putClientProperty(FlatClientProperties.PROGRESS_BAR_SQUARE, false);
            this.putClientProperty("JComponent.outline", null);
            // Altura mayor para que se vea como en la imagen de referencia
            this.putClientProperty("JProgressBar.largeHeight", true);
            this.setBorder(new EmptyBorder(8, 10, 8, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
            int val = (v instanceof Integer) ? (Integer) v : 0;
            setValue(val);


            // Sincronización de colores con tu MainView
            if (val == 100) {
                //setForeground(new Color(46, 204, 113)); // Verde Success
                setForeground(SUCCESS_GREEN.darker());
                setString("Completado");
            } else {
                //setForeground(new Color(52, 152, 219)); // Azul Primary
                setForeground(NICOTINE_ORANGE.darker());
                setString(val + " %");
            }

            // Importante: Si la fila está seleccionada, FlatLaf maneja el fondo
            // pero queremos que la barra mantenga su color base
            setBackground(new Color(45, 52, 54)); // Color fondo tarjeta

            return this;
        }
    }
}
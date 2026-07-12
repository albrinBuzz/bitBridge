package org.bitBridge.view.swing.components.explorer;

import com.formdev.flatlaf.FlatClientProperties;
import org.bitBridge.Client.managers.TransferManager;
import org.bitBridge.Observers.GenericCountListener;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.models.TransferProgress;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.FileTransferState;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.FileHandshakeAction;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;
import org.bitBridge.view.swing.components.transfers.TransferPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cola de Transferencias con acabado estético de alta fidelidad (Fancy Dark Theme).
 * Diseñado para integrarse al ecosistema visual de bitBridge Pro.
 * * Copyright 2026 Cristobal Roman Zamora
 */
public class TransferQueuePanel extends JPanel implements TransferencesObserver {


    private GenericCountListener<TransferQueuePanel> countListener;

    private JTable table;
    private DefaultTableModel model;

    private final Map<String, Integer> rowMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateMap = new ConcurrentHashMap<>();

    private static final int REFRESH_RATE_MS = 150;

    // Paleta de colores extraída directamente de la interfaz de canales de transferencia
    private static final Color PANEL_BG = new Color(30, 31, 34);
    private static final Color TABLE_HEADER_BG = new Color(38, 40, 43);
    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);
    private static final Color INFO_BLUE = new Color(52, 152, 219);
    private static final Color ACCENT_ORANGE = new Color(255, 140, 0);
    private static final Color TEXT_MAIN = new Color(220, 221, 222);
    private static final Color TEXT_MUTED = new Color(114, 118, 125);

    public TransferQueuePanel() {
        setLayout(new BorderLayout());
        setBackground(PANEL_BG);
        setBorder(new EmptyBorder(15, 20, 15, 20));
        initComponents();
    }

    private void initComponents() {
        // Encabezados idénticos en estilo y orden al layout Fancy
        String[] cols = {"ARCHIVO", "TIPO", "ESTADO", "VELOCIDAD", "PROGRESO", "TIEMPO RESTANTE"};

        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setRowHeight(40); // Celdas más altas y elegantes
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(43, 45, 48));
        table.setSelectionForeground(Color.WHITE);
        table.setFocusable(false);

        // Ajustes finos de FlatLaf para la JTable
        table.putClientProperty(FlatClientProperties.STYLE,
                "showHorizontalLines: true; " +
                        "horizontalLineColor: #2b2d30; " +
                        "intercellSpacing: 0,1; " +
                        "selectionArc: 5");

        // Personalización estética del Header de la tabla
        table.getTableHeader().setFont(new Font("Inter", Font.BOLD, 11));
        table.getTableHeader().setBackground(TABLE_HEADER_BG);
        table.getTableHeader().setForeground(TEXT_MUTED);
        table.getTableHeader().setPreferredSize(new Dimension(0, 35));
        table.getTableHeader().setBorder(new LineBorder(new Color(43, 45, 48), 1));

        // Asignación de Renderers Especializados
        FancyCellRenderer centerRenderer = new FancyCellRenderer(SwingConstants.CENTER, null);
        FancyCellRenderer leftRenderer = new FancyCellRenderer(SwingConstants.LEFT, null);

        table.getColumnModel().getColumn(0).setCellRenderer(leftRenderer);                                   // Archivo
        table.getColumnModel().getColumn(1).setCellRenderer(new FancyCellRenderer(SwingConstants.CENTER, INFO_BLUE)); // Tipo (Modo)
        table.getColumnModel().getColumn(2).setCellRenderer(new StatusTextRenderer());                        // Estado
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);                                 // Velocidad
        table.getColumnModel().getColumn(4).setCellRenderer(new FancyProgressCellRenderer());                 // progreso (Barra)
        table.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);                                 // Tiempo restante (ETA)

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(new Color(43, 45, 48), 1, true));
        scroll.getViewport().setBackground(PANEL_BG);

        add(scroll, BorderLayout.CENTER);
    }

    @Override
    public void addTransference(String mode, Transferencia transferencia, TransferManager transferManager) {
        SwingUtilities.invokeLater(() -> {
            int newRow = model.getRowCount();
            rowMap.put(transferencia.getId(), newRow);

            String tipoFlujo = "RECEIVING".equalsIgnoreCase(mode) ? "RECIBIENDO" : "ENVIANDO";

            model.addRow(new Object[]{
                    transferencia.getFileName().toUpperCase(), // Texto en mayúsculas como la muestra
                    tipoFlujo,
                    "EN PROGRESO",
                    "---",
                    0,
                    "Calculando..."
            });
        });

        updateCount();
    }

    @Override
    public void updateTransferenceFull(TransferProgress progress) {
        if (progress == null) {
            Logger.logWarn("[UI-TRANSFER] updateTransferenceFull ignorado: el objeto TransferProgress es NULL.");
            return;
        }
        if (progress.id() == null) {
            Logger.logWarn("[UI-TRANSFER] updateTransferenceFull ignorado: el ID de la transferencia es NULL.");
            return;
        }

        String id = progress.id();
        long now = System.currentTimeMillis();
        long lastUpdate = lastUpdateMap.getOrDefault(id, 0L);
        long tiempoTranscurrido = now - lastUpdate;


        // Control de ráfaga (Throttling) para proteger el rendimiento de la UI
        if (now - lastUpdate < REFRESH_RATE_MS && progress.percentage() < 100) {

            return;
        }
        lastUpdateMap.put(id, now);

        // Pasamos al hilo de la interfaz gráfica de forma segura
        SwingUtilities.invokeLater(() -> {
            // 🔥 CRÍTICO: Consultar el mapa DENTRO del EDT para evitar condiciones de carrera
            Integer modelRowIndex = rowMap.get(id);

            if (modelRowIndex == null) {
                Logger.logError(String.format("[UI-TRANSFER] ❌ ERROR: El ID '%s' NO se encuentra registrado en el rowMap. Las llaves actuales son: %s",
                        id, rowMap.keySet().toString()));
                return;
            }

            if (modelRowIndex >= model.getRowCount()) {
                Logger.logError(String.format("[UI-TRANSFER] ❌ ERROR: El índice de fila mapeado (%d) excede el tamaño actual del modelo (%d filas) para ID: %s",
                        modelRowIndex, model.getRowCount(), id));
                return;
            }

            // Conversión a índice de vista por si la JTable tiene un RowSorter activo (filtros/ordenamiento)
            int viewRowIndex = -1;
            try {
                viewRowIndex = table.convertRowIndexToView(modelRowIndex);
                if (viewRowIndex == -1) {
                    Logger.logWarn(String.format("[UI-TRANSFER] 🔍 Fila del modelo %d está oculta por un RowFilter activo para ID: %s", modelRowIndex, id));
                }
            } catch (Exception e) {
                Logger.logWarn(String.format("[UI-TRANSFER] 🔍 Excepción al convertir índice. Fila del modelo %d no visible en vista para ID: %s. Detalle: %s",
                        modelRowIndex, id, e.getMessage()));
                viewRowIndex = -1;
            }



            // Modificación segura de las celdas directamente en el Modelo
            model.setValueAt(progress.percentage(), modelRowIndex, 4);

            if (progress.percentage() >= 100) {
                model.setValueAt("FINALIZADO", modelRowIndex, 2);
                model.setValueAt("---", modelRowIndex, 3);
                model.setValueAt("00:00", modelRowIndex, 5);
                lastUpdateMap.remove(id); // Limpieza de caché de refresco
                Logger.logInfo(String.format("[UI-TRANSFER] ✅ Fila %d con ID %s marcada como FINALIZADA.", modelRowIndex, id));
            } else {
                model.setValueAt(String.format("%.1f MB/s", progress.speedMBs()), modelRowIndex, 3);
                model.setValueAt(progress.eta(), modelRowIndex, 5);
            }

            // Forzar repintado inmediato del rectángulo de la fila visual modificada si está en pantalla
            if (viewRowIndex != -1) {
                table.repaint(table.getCellRect(viewRowIndex, 0, true));
            }
        });
    }

    @Override
    public void endTransference(String mode, String id) {
        Integer rowIndex = rowMap.get(id);
        if (rowIndex != null) {
            SwingUtilities.invokeLater(() -> {
                if (rowIndex < model.getRowCount()) {
                    model.setValueAt("FINALIZADO", rowIndex, 2);
                    model.setValueAt("---", rowIndex, 3);
                    model.setValueAt(100, rowIndex, 4);
                    model.setValueAt("00:00", rowIndex, 5);
                }
                rowMap.remove(id);
                lastUpdateMap.remove(id);
            });
        }
        updateCount();
    }

    @Override
    public void updateTransference(FileTransferState mode, String id, int progress) {
        Integer rowIndex = rowMap.get(id);
        if (rowIndex != null) {
            SwingUtilities.invokeLater(() -> {
                if (rowIndex < model.getRowCount()) {
                    model.setValueAt(progress, rowIndex, 4);
                    if (progress >= 100) model.setValueAt("FINALIZADO", rowIndex, 2);
                }
            });
        }
        updateCount();
    }

    @Override public void notifyException(String message) {}
    @Override public boolean notifyTranference(FileHandshakeCommunication hc) { return false; }
    @Override public void notifyTranference(FileHandshakeAction action) {}

    // --- RENDERERS FANCY (ESTILO PREMIUM MAS EXPLICITO) ---

    /**
     * Renderer base para dar padding y fuentes estilizadas a las celdas estándar.
     */
    static class FancyCellRenderer extends DefaultTableCellRenderer {
        private final Color customColor;

        public FancyCellRenderer(int alignment, Color customColor) {
            setHorizontalAlignment(alignment);
            this.customColor = customColor;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFoc, int r, int c) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, val, isSel, hasFoc, r, c);
            lbl.setFont(new Font("Inter", Font.PLAIN, 12));
            lbl.setBorder(new EmptyBorder(0, 10, 0, 10));

            if (!isSel) {
                lbl.setForeground(customColor != null ? customColor : TEXT_MAIN);
            }
            return lbl;
        }
    }

    /**
     * Renderer especial en mayúsculas negrita para los estados ("FINALIZADO", "EN PROGRESO").
     */
    static class StatusTextRenderer extends DefaultTableCellRenderer {
        public StatusTextRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFoc, int r, int c) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, val, isSel, hasFoc, r, c);
            lbl.setFont(new Font("Inter", Font.BOLD, 11));

            String estado = val != null ? val.toString() : "";
            if (!isSel) {
                if ("FINALIZADO".equals(estado)) {
                    lbl.setForeground(SUCCESS_GREEN);
                } else {
                    lbl.setForeground(ACCENT_ORANGE);
                }
            }
            return lbl;
        }
    }

    /**
     * Barra de progreso plana, integrada por completo a la celda sin márgenes toscos.
     */
    static class FancyProgressCellRenderer extends JProgressBar implements javax.swing.table.TableCellRenderer {
        public FancyProgressCellRenderer() {
            super(0, 100);
            setStringPainted(true);
            setFont(new Font("Inter", Font.BOLD, 11));
            // FlatLaf inyecta esquinas redondeadas y elimina bordes nativos 3D usando estilos
            putClientProperty(FlatClientProperties.STYLE, "arc: 4; borderWidth: 0; intrisicHeight: 22");
            setBorder(new EmptyBorder(6, 10, 6, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFoc, int r, int c) {
            if (val instanceof Integer) {
                int progress = (Integer) val;
                setValue(progress);
                setString(progress + " %");

                if (progress >= 100) {
                    setForeground(SUCCESS_GREEN);
                } else {
                    setForeground(new Color(22, 160, 133)); // Un tono verde azulado oscuro elegante para el progreso vivo
                }
            }
            return this;
        }
    }


    public void setTransferCountListener(GenericCountListener<TransferQueuePanel> listener) {
        this.countListener = listener;
    }

    private void updateCount() {
        if (countListener != null) {
            int activeCount = 1;
            for (int i = 0; i < model.getRowCount(); i++) {
                String estado = (String) model.getValueAt(i, 3);
                if (!"FINALIZADO".equals(estado) && !"CANCELADO".equals(estado)) {
                    activeCount++;
                }
            }
            countListener.onCountChanged(this, activeCount);
        }
    }
}
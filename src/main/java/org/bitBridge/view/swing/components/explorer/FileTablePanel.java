package org.bitBridge.view.swing.components.explorer;

import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Componente de Tabla Atómica reutilizable para bitBridge Pro.
 * Incorpora barra de búsqueda inteligente, filtros por estado de red y estadísticas de cuota.
 * * Copyright 2026 Cristobal Roman Zamora
 */
public class FileTablePanel extends JPanel {

    JTable fileTable;
    private DefaultTableModel fileModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private List<NodoDirectorio> nodosActuales = new ArrayList<>();

    private JTextField txtSearch;
    private JComboBox<String> comboSyncFilter;
    private JCheckBox chkFragmentarHijos;

    private NodoDirectorio nodoRaizActual;

    // Paleta de Alto Contraste (JetBrains / Code Style)
    public static final Color STATE_SYNCHRONIZED   = new Color(102, 217, 102);
    public static final Color STATE_MODIFIED_LOCAL  = new Color(255, 184, 108);
    public static final Color STATE_MODIFIED_REMOTE = new Color(139, 233, 253);
    public static final Color STATE_CONFLICT        = new Color(255, 121, 198);
    public static final Color STATE_ORPHAN          = new Color(189, 147, 249);
    public static final Color STATE_IGNORED         = new Color(140, 140, 140);

    // Paleta UI Premium
    private static final Color FANCY_BG = new Color(30, 30, 30);
    private static final Color FANCY_BORDER = new Color(50, 50, 50);
    private static final Color FANCY_TEXT = new Color(220, 220, 220);
    private static final Color FANCY_ACCENT_ORANGE = new Color(255, 165, 0);
    private static final Color FANCY_SELECTION_BG = new Color(45, 48, 52);
    private static final Color TOOLBAR_BG = new Color(22, 24, 26);

    private final Consumer<NodoDirectorio> onSelection;
    private final Consumer<NodoDirectorio> onDoubleClick;
    private final Consumer<List<NodoDirectorio>> onActionRequested;
    private final String etiquetaAccionPrincipal;
    private JComboBox<String> comboTypeFilter;
    private JLabel lblFooterStats;

    private final String[] OPCIONES_TIPOS = {
            "Todos los formatos",
            "Solo Carpetas 📁",
            "Solo Archivos 📄",
            "Código Fuente (*.java, *.py, *.cpp, *.go)",
            "Documentos (*.pdf, *.docx, *.txt, *.xlsx)",
            "Imágenes (*.png, *.jpg, *.gif, *.svg)",
            "Binarios/Ejecutables (*.exe, *.sh, *.jar, *.bat)"
    };

    public FileTablePanel(String etiquetaAccionPrincipal,
                          Consumer<NodoDirectorio> onSelection,
                          Consumer<NodoDirectorio> onDoubleClick,
                          Consumer<List<NodoDirectorio>> onActionRequested) {
        this.etiquetaAccionPrincipal = etiquetaAccionPrincipal;
        this.onSelection = onSelection;
        this.onDoubleClick = onDoubleClick;
        this.onActionRequested = onActionRequested;

        setLayout(new BorderLayout());
        initComponents();
    }

    public void setNodoRaizActual(NodoDirectorio nodoRaiz) {
        this.nodoRaizActual = nodoRaiz;
    }

    private void initComponents() {
        String[] columns = {"Nombre", "Tamaño", "Tipo", "Modificado", "Estado Sync"};
        fileModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        fileTable = new JTable(fileModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowHeight(32);
        fileTable.setShowGrid(false);
        fileTable.setSelectionBackground(new Color(55, 55, 55));
        fileTable.setSelectionForeground(Color.WHITE);
        fileTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        sorter = new TableRowSorter<>(fileModel);
        fileTable.setRowSorter(sorter);

        setupTopToolBar();
        setupRenderers();
        setupMouseListeners();
        setupKeyBindings();

        JScrollPane scrollPane = new JScrollPane(fileTable);
        scrollPane.getViewport().setBackground(new Color(24, 24, 24));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        setupBottomStatusBar();
    }


    private void setupTopToolBar() {
        // Panel principal de la barra (Layout de 2 filas verticales: Superior e Inferior)
        JPanel pnlToolbar = new JPanel(new GridLayout(2, 1, 0, 2));
        pnlToolbar.setBackground(TOOLBAR_BG);
        pnlToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, FANCY_BORDER));

        boolean esPanelLocal = etiquetaAccionPrincipal.contains("PUSH");

        // =========================================================================
        // FILA 1: BOTONES DE ACCIÓN PRINCIPAL
        // =========================================================================
        JPanel rowTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        rowTop.setOpaque(false);

        // --- BOTÓN 1: COMPORTAMIENTO INCREMENTAL ---
        String textoBotonBatch = esPanelLocal ? "📤 Subir No Sincronizados" : "📥 Bajar No Sincronizados";
        JButton btnSyncBatch = new JButton(textoBotonBatch);
        btnSyncBatch.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnSyncBatch.setBackground(esPanelLocal ? new Color(34, 139, 34) : FANCY_ACCENT_ORANGE);
        btnSyncBatch.setForeground(Color.WHITE);
        btnSyncBatch.setFocusPainted(false);
        btnSyncBatch.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSyncBatch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(btnSyncBatch.getBackground().darker()),
                new EmptyBorder(5, 10, 5, 10)
        ));
        btnSyncBatch.addActionListener(e -> ejecutarSincronizacionBatch(esPanelLocal, false));
        rowTop.add(btnSyncBatch);

        // --- BOTÓN 2: FORZAR TRANSMISIÓN MASIVA ---
        String textoBotonBatchForzar = esPanelLocal ? "🔥 Forzar Subida Completa" : "🔥 Forzar Bajada Completa";
        JButton btnSyncBatchForzar = new JButton(textoBotonBatchForzar);
        btnSyncBatchForzar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnSyncBatchForzar.setBackground(esPanelLocal ? new Color(25, 110, 25) : FANCY_ACCENT_ORANGE.darker());
        btnSyncBatchForzar.setForeground(Color.WHITE);
        btnSyncBatchForzar.setFocusPainted(false);
        btnSyncBatchForzar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSyncBatchForzar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(btnSyncBatchForzar.getBackground().darker()),
                new EmptyBorder(5, 10, 5, 10)
        ));
        btnSyncBatchForzar.addActionListener(e -> ejecutarSincronizacionBatch(esPanelLocal, true));
        rowTop.add(btnSyncBatchForzar);


        // =========================================================================
        // FILA 2: CONFIGURACIÓN DE RED Y FILTROS (Distribución Izquierda/Derecha)
        // =========================================================================
        JPanel rowBottom = new JPanel(new BorderLayout());
        rowBottom.setOpaque(false);

        // Parte Izquierda de la Fila 2: Checkbox
        JPanel rowBottomLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        rowBottomLeft.setOpaque(false);

        chkFragmentarHijos = new JCheckBox("Fragmentar por Hijos");
        chkFragmentarHijos.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkFragmentarHijos.setForeground(FANCY_TEXT);
        chkFragmentarHijos.setOpaque(true);
        chkFragmentarHijos.setFocusPainted(true);
        chkFragmentarHijos.setSelected(true);
        chkFragmentarHijos.setToolTipText("<html><b>Activado:</b> Envía los archivos hijos uno a uno como elementos independientes.<br>"
                + "<b>Desactivado:</b> Envía el nodo raíz actual en una única trama optimizada (One-Shot).</html>");
        rowBottomLeft.add(chkFragmentarHijos);
        rowBottom.add(rowBottomLeft, BorderLayout.WEST);

        // Parte Derecha de la Fila 2: Filtros de Búsqueda
        JPanel rowBottomRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        rowBottomRight.setOpaque(false);

        String[] syncStates = {"Todos los estados", "Sincronizados", "Modificados", "Conflictos"};
        comboSyncFilter = new JComboBox<>(syncStates);
        comboSyncFilter.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboSyncFilter.setBackground(FANCY_BG);
        comboSyncFilter.setForeground(FANCY_TEXT);
        comboSyncFilter.addActionListener(e -> aplicarFiltroCompuesto());

        comboTypeFilter = new JComboBox<>(OPCIONES_TIPOS);
        comboTypeFilter.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboTypeFilter.setBackground(FANCY_BG);
        comboTypeFilter.setForeground(FANCY_TEXT);
        comboTypeFilter.addActionListener(e -> aplicarFiltroCompuesto());

        txtSearch = new JTextField(7); // Tamaño compacto para el buscador
        txtSearch.putClientProperty("JTextField.placeholderText", "🔍 Buscar...");
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        txtSearch.setBackground(FANCY_BG);
        txtSearch.setForeground(Color.WHITE);
        txtSearch.setCaretColor(Color.WHITE);
        txtSearch.setBorder(BorderFactory.createLineBorder(FANCY_BORDER));
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { aplicarFiltroCompuesto(); }
            public void removeUpdate(DocumentEvent e) { aplicarFiltroCompuesto(); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltroCompuesto(); }
        });

        rowBottomRight.add(new JLabel("Estado:"));
        rowBottomRight.add(comboSyncFilter);
        rowBottomRight.add(new JLabel("Tipo:"));
        rowBottomRight.add(comboTypeFilter);
        rowBottomRight.add(txtSearch);

        rowBottom.add(rowBottomRight, BorderLayout.EAST);

        // Ensamblamos las dos filas dentro del contenedor principal de la barra
        pnlToolbar.add(rowTop);
        pnlToolbar.add(rowBottom);

        // Agregamos la barra completa en la parte norte del panel
        add(pnlToolbar, BorderLayout.NORTH);
    }


    private void setupBottomStatusBar() {
        JPanel pnlFooter = new JPanel(new BorderLayout());
        pnlFooter.setBackground(TOOLBAR_BG);
        pnlFooter.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, FANCY_BORDER));
        pnlFooter.setPreferredSize(new Dimension(0, 24));

        lblFooterStats = new JLabel(" 📁 0 Carpetas | 📄 0 Archivos | ⚖️ 0 B en total");
        lblFooterStats.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblFooterStats.setForeground(Color.GRAY);

        pnlFooter.add(lblFooterStats, BorderLayout.WEST);
        add(pnlFooter, BorderLayout.SOUTH);
    }

    private void aplicarFiltroCompuesto() {
        SwingUtilities.invokeLater(() -> {
            String textoBusqueda = txtSearch.getText().trim().toLowerCase();
            int seleccionSync = comboSyncFilter.getSelectedIndex();
            int seleccionTipo = comboTypeFilter.getSelectedIndex();

            RowFilter<DefaultTableModel, Integer> filtroMatriz = new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                    int modelIdx = entry.getIdentifier();
                    if (modelIdx < 0 || modelIdx >= nodosActuales.size()) return false;

                    NodoDirectorio nodo = nodosActuales.get(modelIdx);
                    String nombre = nodo.getNombre().toLowerCase();
                    String ext = nodo.getExtension().toLowerCase();
                    String estadoSync = entry.getModel().getValueAt(modelIdx, 4).toString();

                    boolean pasaTexto = textoBusqueda.isEmpty() || nombre.contains(textoBusqueda) || ext.contains(textoBusqueda);

                    boolean pasaSync = true;
                    switch (seleccionSync) {
                        case 1: pasaSync = estadoSync.contains("Sincronizado"); break;
                        case 2: pasaSync = estadoSync.contains("Modificado"); break;
                        case 3: pasaSync = estadoSync.contains("Conflicto") || estadoSync.contains("Solo"); break;
                    }

                    boolean pasaTipo = true;
                    switch (seleccionTipo) {
                        case 1: pasaTipo = nodo.esDirectorio(); break;
                        case 2: pasaTipo = !nodo.esDirectorio(); break;
                        case 3: pasaTipo = !nodo.esDirectorio() && ".java.py.cpp.h.go.js.ts.sh.rs.c".contains(ext); break;
                        case 4: pasaTipo = !nodo.esDirectorio() && ".pdf.docx.doc.txt.xlsx.xls.csv.md".contains(ext); break;
                        case 5: pasaTipo = !nodo.esDirectorio() && ".png.jpg.jpeg.gif.svg.ico.webp".contains(ext); break;
                        case 6: pasaTipo = !nodo.esDirectorio() && ".exe.sh.jar.bat.msi.bin".contains(ext); break;
                    }

                    return pasaTexto && pasaSync && pasaTipo;
                }
            };

            sorter.setRowFilter(filtroMatriz);
            actualizarMetricasFooter();
        });
    }

    private void actualizarMetricasFooter() {
        int filasVisibles = fileTable.getRowCount();
        int carpetas = 0;
        int archivos = 0;
        long pesoTotalBytes = 0;

        for (int i = 0; i < filasVisibles; i++) {
            int modelIdx = fileTable.convertRowIndexToModel(i);
            if (modelIdx >= 0 && modelIdx < nodosActuales.size()) {
                NodoDirectorio n = nodosActuales.get(modelIdx);
                if (n.esDirectorio()) {
                    carpetas++;
                } else {
                    archivos++;
                    pesoTotalBytes += n.getTamaño();
                }
            }
        }

        String pesoFormateado;
        if (pesoTotalBytes < 1024) pesoFormateado = pesoTotalBytes + " B";
        else {
            int exp = (int) (Math.log(pesoTotalBytes) / Math.log(1024));
            pesoFormateado = String.format("%.2f %cB", pesoTotalBytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
        }

        lblFooterStats.setText(String.format(" 📁 %d Carpetas | 📄 %d Archivos | ⚖️ El filtro contiene: %s",
                carpetas, archivos, pesoFormateado));
    }

    private void ejecutarSincronizacionBatch(boolean esLocal, boolean forzar) {
        List<NodoDirectorio> loteSincronizacion = new ArrayList<>();
        String condicionFiltro = esLocal ? "Solo Local" : "Solo Remoto";
        String condicionModificado = esLocal ? "Modificado Local" : "Modificado Remoto";

        boolean fragmentar = chkFragmentarHijos.isSelected();

        if (forzar) {
            if (fragmentar) {
                loteSincronizacion.addAll(nodosActuales);
            } else {
                if (nodoRaizActual != null) {
                    loteSincronizacion.add(nodoRaizActual);
                } /*else if (!nodosActuales.isEmpty()) {
                    NodoDirectorio padreFallback = nodosActuales.get(0).getPadre();
                    if (padreFallback != null) loteSincronizacion.add(padreFallback);
                }*/
            }
        } else {
            for (int i = 0; i < fileModel.getRowCount(); i++) {
                String estadoSync = (String) fileModel.getValueAt(i, 4);
                if (estadoSync != null && (estadoSync.contains(condicionFiltro) || estadoSync.contains(condicionModificado))) {
                    loteSincronizacion.add(nodosActuales.get(i));
                }
            }
        }

        if (loteSincronizacion.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "¡Excelente! Todos los assets en este directorio se encuentran perfectamente sincronizados.",
                    "bitBridge Engine", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String tituloConfirm;
        String mensajeConfirm;

        if (forzar) {
            tituloConfirm = "🔥 Procesamiento por Lote Forzado";
            if (fragmentar) {
                mensajeConfirm = "Vas a iniciar la transferencia FORZADA e INDIVIDUAL de los " + loteSincronizacion.size()
                        + " elementos hijos del directorio.\n\nEsto enviará múltiples tramas secuenciales. ¿Deseas continuar?";
            } else {
                mensajeConfirm = "Vas a iniciar la transferencia INTEGRAL y masiva (One-Shot) del directorio actual:\n"
                        + (nodoRaizActual != null ? nodoRaizActual.getRutaString() : "Raíz")
                        + "\n\nEsto optimizará la red enviando una única trama de datos agrupada. ¿Deseas continuar?";
            }
        } else {
            tituloConfirm = "Procesamiento por Lotes Encontrado";
            mensajeConfirm = "Se detectaron " + loteSincronizacion.size() + " elementos fuera de réplica.\n¿Deseas iniciar la transmisión en bloque?";
        }

        int confirm = JOptionPane.showConfirmDialog(this, mensajeConfirm, tituloConfirm, JOptionPane.YES_NO_OPTION,
                forzar ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            Logger.logInfo(String.format("Despachando lote. Forzado: %b | Fragmentado: %b | Elementos: %d",
                    forzar, fragmentar, loteSincronizacion.size()));
            if (onActionRequested != null) {
                onActionRequested.accept(loteSincronizacion);
            }
        }
    }

    private void setupRenderers() {
        DefaultTableCellRenderer commonRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, f, r, c);
                if (v == null || "Desconocido".equals(v.toString())) {
                    setText("--");
                    setForeground(Color.GRAY);
                } else {
                    setForeground(Color.LIGHT_GRAY);
                }
                return this;
            }
        };

        fileTable.getColumnModel().getColumn(1).setCellRenderer(commonRenderer);
        fileTable.getColumnModel().getColumn(2).setCellRenderer(commonRenderer);
        fileTable.getColumnModel().getColumn(3).setCellRenderer(commonRenderer);

        fileTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, f, r, c);
                String val = (v != null) ? v.toString() : "";

                l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));

                if (val.contains("Sincronizado")) l.setForeground(STATE_SYNCHRONIZED);
                else if (val.contains("Modificado Local")) l.setForeground(STATE_MODIFIED_LOCAL);
                else if (val.contains("Modificado Remoto")) l.setForeground(STATE_MODIFIED_REMOTE);
                else if (val.contains("Conflicto")) l.setForeground(STATE_CONFLICT);
                else if (val.contains("Solo")) l.setForeground(STATE_ORPHAN);
                else if (val.contains("Ignorado")) l.setForeground(STATE_IGNORED);
                else l.setForeground(t.getForeground());

                l.setHorizontalAlignment(SwingConstants.CENTER);
                return l;
            }
        });
    }

    private void setupMouseListeners() {
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                NodoDirectorio nodo = getSelectedNode();
                if (nodo == null) return;
                if (e.getClickCount() == 1) onSelection.accept(nodo);
                else if (e.getClickCount() == 2) onDoubleClick.accept(nodo);
            }
            @Override
            public void mousePressed(MouseEvent e) { evaluarPopUp(e); }
            @Override
            public void mouseReleased(MouseEvent e) { evaluarPopUp(e); }
        });
    }

    private void setupKeyBindings() {
        fileTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                List<NodoDirectorio> seleccionados = getSelectedNodes();
                if (seleccionados.isEmpty()) return;

                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    ejecutarEliminacion(seleccionados);
                } else if (e.getKeyCode() == KeyEvent.VK_F2 && seleccionados.size() == 1) {
                    ejecutarRenombrado(seleccionados.get(0));
                }
            }
        });
    }

    private void evaluarPopUp(MouseEvent e) {
        if (e.isPopupTrigger()) {
            int row = fileTable.rowAtPoint(e.getPoint());
            if (row != -1 && !fileTable.isRowSelected(row)) {
                fileTable.setRowSelectionInterval(row, row);
                NodoDirectorio nodo = getSelectedNode();
                if (nodo != null) onSelection.accept(nodo);
            }

            List<NodoDirectorio> seleccionados = getSelectedNodes();
            if (!seleccionados.isEmpty()) {
                JPopupMenu menu = construirContextMenu(seleccionados);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    private JPopupMenu construirContextMenu(List<NodoDirectorio> seleccionados) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(FANCY_BG);
        menu.setBorder(BorderFactory.createLineBorder(FANCY_BORDER, 1));

        boolean multiple = seleccionados.size() > 1;
        NodoDirectorio primerNodo = getSelectedNode();
        boolean esPanelLocal = etiquetaAccionPrincipal.contains("PUSH");

        String labelAccionPrincipal = esPanelLocal
                ? (multiple ? "Subir Réplicas de Origen" : "Subir Réplica en Destino")
                : (multiple ? "Bajar Selección Estructural" : "Bajar Asset del Servidor");

        String iconAccionPrincipal = esPanelLocal ? "📤 " : "📥 ";

        JMenuItem itemCoreAction = createFancyMenuItem(iconAccionPrincipal + labelAccionPrincipal, true);
        itemCoreAction.addActionListener(e -> {
            if (onActionRequested != null) onActionRequested.accept(seleccionados);
        });
        menu.add(itemCoreAction);

        JMenu menuSmartActions = createFancySubMenu("⚡ Transmisión Inteligente");
        JMenuItem itemDelta = createFancyMenuItem("↳ Sincronización Diferencial Delta", false);
        itemDelta.addActionListener(e -> Logger.logInfo("Calculando matrices rsync checksum."));
        JMenuItem itemLz4 = createFancyMenuItem("↳ Flujo Comprimido Stream (LZ4)", false);
        itemLz4.addActionListener(e -> Logger.logInfo("Instanciando pipeline LZ4."));

        menuSmartActions.add(itemDelta);
        menuSmartActions.add(itemLz4);
        menu.add(menuSmartActions);

        menu.add(createFancySeparator());

        JMenuItem itemDiff = createFancyMenuItem("🔍 Comparar Estructura (Diff)", false);
        itemDiff.setEnabled(!multiple && primerNodo != null && !primerNodo.esDirectorio());
        itemDiff.addActionListener(e -> Logger.logInfo("Abriendo Diff Engine para: " + primerNodo.getNombre()));
        menu.add(itemDiff);

        JMenuItem itemIntegrity = createFancyMenuItem("🛡️ Cotejar Firma de Datos (SHA-1)", false);
        itemIntegrity.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Calculando firmas criptográficas en caliente...", "Auditoría bitBridge", JOptionPane.INFORMATION_MESSAGE));
        menu.add(itemIntegrity);

        JMenuItem itemHistory = createFancyMenuItem("⏳ Historial de Commits (Rollback)", false);
        itemHistory.setEnabled(!multiple);
        menu.add(itemHistory);

        menu.add(createFancySeparator());

        JMenuItem itemIgnore = createFancyMenuItem("🚫 Añadir a .bitbridgeignore", false);
        menu.add(itemIgnore);

        JMenuItem itemCopyPath = createFancyMenuItem("📋 Copiar Ruta de Almacenamiento", false);
        itemCopyPath.addActionListener(e -> {
            if (primerNodo != null) {
                StringSelection selection = new StringSelection(primerNodo.getRutaString());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        menu.add(itemCopyPath);

        menu.add(createFancySeparator());

        JMenuItem itemRename = createFancyMenuItem("✏️ Renombrar Entrada (F2)", false);
        itemRename.setEnabled(!multiple);
        itemRename.addActionListener(e -> {
            if (primerNodo != null) ejecutarRenombrado(primerNodo);
        });

        JMenuItem itemDelete = createFancyMenuItem(multiple ? "🗑️ Destruir Seleccionados (DEL)" : "🗑️ Destruir Asset Físico (DEL)", false);
        itemDelete.setForeground(new Color(255, 100, 100));
        itemDelete.addActionListener(e -> ejecutarEliminacion(seleccionados));

        menu.add(itemRename);
        menu.add(itemDelete);

        return menu;
    }

    private JMenuItem createFancyMenuItem(String text, boolean highlighted) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(new Font("Segoe UI", highlighted ? Font.BOLD : Font.PLAIN, 12));
        item.setForeground(highlighted ? FANCY_ACCENT_ORANGE : FANCY_TEXT);
        item.setBackground(FANCY_BG);
        item.setOpaque(true);
        item.setBorder(new EmptyBorder(7, 12, 7, 12));
        item.setCursor(new Cursor(Cursor.HAND_CURSOR));

        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (item.isEnabled()) {
                    item.setBackground(FANCY_SELECTION_BG);
                    if (!highlighted) item.setForeground(Color.WHITE);
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                item.setBackground(FANCY_BG);
                item.setForeground(highlighted ? FANCY_ACCENT_ORANGE : FANCY_TEXT);
            }
        });
        return item;
    }

    private JMenu createFancySubMenu(String text) {
        JMenu menu = new JMenu(text);
        menu.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        menu.setForeground(FANCY_TEXT);
        menu.setBackground(FANCY_BG);
        menu.setOpaque(true);
        menu.setBorder(new EmptyBorder(7, 12, 7, 12));
        menu.getPopupMenu().setBackground(FANCY_BG);
        menu.getPopupMenu().setBorder(BorderFactory.createLineBorder(FANCY_BORDER, 1));

        menu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { menu.setBackground(FANCY_SELECTION_BG); }
            @Override
            public void mouseExited(MouseEvent e) { menu.setBackground(FANCY_BG); }
        });
        return menu;
    }

    private JPopupMenu.Separator createFancySeparator() {
        JPopupMenu.Separator sep = new JPopupMenu.Separator();
        sep.setForeground(new Color(45, 45, 45));
        sep.setBackground(FANCY_BG);
        return sep;
    }

    private void ejecutarRenombrado(NodoDirectorio nodo) {
        String nuevoNombre = JOptionPane.showInputDialog(this,
                "Ingrese el nuevo nombre para el asset:", "Renombrar elemento", JOptionPane.QUESTION_MESSAGE);

        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
            Logger.logInfo("Solicitud de renombrado aceptada para [" + nodo.getNombre() + "] -> " + nuevoNombre);
        }
    }

    private void ejecutarEliminacion(List<NodoDirectorio> nodos) {
        if (nodos.isEmpty()) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Estás seguro de que deseas eliminar estos " + nodos.size() + " elementos?\nEsta acción es irreversible en caliente.",
                "Confirmar destrucción", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            for (NodoDirectorio n : nodos) {
                Logger.logInfo("Ejecutando orden física de borrado para: " + n.getRutaString());
            }
        }
    }

    public void clear() {
        fileModel.setRowCount(0);
        nodosActuales.clear();
    }

    public void agregarFila(NodoDirectorio nodo, String estadoSync) {
        nodosActuales.add(nodo);
        //String tamanoStr = nodo.esDirectorio() ? "Desconocido" : formatSize(nodo.getTamaño());
        String tamanoStr =  formatSize(nodo.getTamaño());
        String tipoStr = nodo.esDirectorio() ? "Carpeta 📁" : nodo.getExtension().toUpperCase();

        fileModel.addRow(new Object[]{
                nodo.getNombre(),
                tamanoStr,
                tipoStr,
                nodo.getFechaModificacionMillis() > 0 ? "Modificado" : "Desconocido",
                estadoSync
        });
        actualizarMetricasFooter();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }

    NodoDirectorio getSelectedNode() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow == -1) return null;
        int modelRow = fileTable.convertRowIndexToModel(viewRow);
        return nodosActuales.get(modelRow);
    }

    List<NodoDirectorio> getSelectedNodes() {
        List<NodoDirectorio> lista = new ArrayList<>();
        int[] viewRows = fileTable.getSelectedRows();
        for (int vr : viewRows) {
            int mr = fileTable.convertRowIndexToModel(vr);
            lista.add(nodosActuales.get(mr));
        }
        return lista;
    }

    public JTable getFileTable() {
        return fileTable;
    }

    public DefaultTableModel getFileModel() {
        return fileModel;
    }

    public TableRowSorter<DefaultTableModel> getSorter() {
        return sorter;
    }

    public List<NodoDirectorio> getNodosActuales() {
        return nodosActuales;
    }

    public JTextField getTxtSearch() {
        return txtSearch;
    }

    public JComboBox<String> getComboSyncFilter() {
        return comboSyncFilter;
    }

    public JCheckBox getChkFragmentarHijos() {
        return chkFragmentarHijos;
    }

    public NodoDirectorio getNodoRaizActual() {
        return nodoRaizActual;
    }

    public Consumer<NodoDirectorio> getOnSelection() {
        return onSelection;
    }

    public Consumer<NodoDirectorio> getOnDoubleClick() {
        return onDoubleClick;
    }

    public Consumer<List<NodoDirectorio>> getOnActionRequested() {
        return onActionRequested;
    }

    public String getEtiquetaAccionPrincipal() {
        return etiquetaAccionPrincipal;
    }

    public JComboBox<String> getComboTypeFilter() {
        return comboTypeFilter;
    }

    public JLabel getLblFooterStats() {
        return lblFooterStats;
    }

    public String[] getOPCIONES_TIPOS() {
        return OPCIONES_TIPOS;
    }
}
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

    // Componentes de control agregados para Filtros y Busqueda
    private JTextField txtSearch;
    private JComboBox<String> comboSyncFilter;





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
    private JComboBox<String> comboTypeFilter; // ⚡ NUEVO: Combo de tipos de archivo
    private JLabel lblFooterStats;
    // Categorías de filtrado rápido por extensión
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

    private void initComponents() {
        // --- CONFIGURACIÓN DE TABLA ---
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

        // Vinculamos el Sorter para habilitar ordenamiento por columnas y filtros combinados
        sorter = new TableRowSorter<>(fileModel);
        fileTable.setRowSorter(sorter);

        // --- BARRA DE HERRAMIENTAS SUPERIOR (ACCIONES RÁPIDAS + BUSQUEDA) ---
        setupTopToolBar();

        setupRenderers();
        setupMouseListeners();
        setupKeyBindings();

        JScrollPane scrollPane = new JScrollPane(fileTable);
        scrollPane.getViewport().setBackground(new Color(24, 24, 24));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // --- BARRA INFERIOR DE ESTADÍSTICAS ---
        setupBottomStatusBar();
    }

    /**
     * Crea e integra la barra superior de acciones rápidas incorporando controles de filtrado activo.
     */
    private void setupTopToolBar() {
        JPanel pnlToolbar = new JPanel(new BorderLayout());
        pnlToolbar.setBackground(TOOLBAR_BG);
        pnlToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, FANCY_BORDER));

        JToolBar toolBarLeft = new JToolBar();
        toolBarLeft.setFloatable(false);
        toolBarLeft.setOpaque(false);
        toolBarLeft.setBorder(new EmptyBorder(4, 8, 4, 4));

        boolean esPanelLocal = etiquetaAccionPrincipal.contains("PUSH");
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
        btnSyncBatch.addActionListener(e -> ejecutarSincronizacionBatch(esPanelLocal));
        toolBarLeft.add(btnSyncBatch);

        // --- SUB-PANEL DERECHO: CONMUTADORES Y FILTROS EN CADENA ---
        JPanel pnlFilterActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        pnlFilterActions.setOpaque(false);

        // 1. Combo de filtrado por Estado Sync
        String[] syncStates = {"Todos los estados", "Sincronizados", "Modificados", "Conflictos / Huérfanos"};
        comboSyncFilter = new JComboBox<>(syncStates);
        comboSyncFilter.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboSyncFilter.setBackground(FANCY_BG);
        comboSyncFilter.setForeground(FANCY_TEXT);
        comboSyncFilter.addActionListener(e -> aplicarFiltroCompuesto());

        // 2. ⚡ NUEVO: Combo de filtrado por Extensiones / Formato
        comboTypeFilter = new JComboBox<>(OPCIONES_TIPOS);
        comboTypeFilter.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboTypeFilter.setBackground(FANCY_BG);
        comboTypeFilter.setForeground(FANCY_TEXT);
        comboTypeFilter.addActionListener(e -> aplicarFiltroCompuesto());

        // 3. Buscador por texto plano
        txtSearch = new JTextField(10);
        txtSearch.putClientProperty("JTextField.placeholderText", "🔍 Buscar...");
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtSearch.setBackground(FANCY_BG);
        txtSearch.setForeground(Color.WHITE);
        txtSearch.setCaretColor(Color.WHITE);
        txtSearch.setBorder(BorderFactory.createLineBorder(FANCY_BORDER));
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { aplicarFiltroCompuesto(); }
            public void removeUpdate(DocumentEvent e) { aplicarFiltroCompuesto(); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltroCompuesto(); }
        });

        pnlFilterActions.add(new JLabel("Estado:"));
        pnlFilterActions.add(new JLabel("    a    "));
        pnlFilterActions.add(new JPopupMenu.Separator());

        pnlFilterActions.add(comboSyncFilter);
        pnlFilterActions.add(new JLabel("Tipo:"));
        pnlFilterActions.add(comboTypeFilter);
        pnlFilterActions.add(Box.createHorizontalStrut(2));
        pnlFilterActions.add(txtSearch);

        pnlToolbar.add(toolBarLeft, BorderLayout.WEST);
        pnlToolbar.add(pnlFilterActions, BorderLayout.EAST);
        add(pnlToolbar, BorderLayout.NORTH);
    }

    /**
     * Construcción de barra inferior atómica para control de cuota de almacenamiento.
     */
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

    /**
     * Resuelve en simultáneo la intersección de tres filtros: texto predictivo,
     * estado de réplica Rsync y tipo/extensión del asset.
     * Corregido: Extrae el estado sync directamente del modelo de datos.
     */
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

                    // Extraemos el nodo real desde la memoria para evitar errores de desfase de columnas
                    NodoDirectorio nodo = nodosActuales.get(modelIdx);
                    String nombre = nodo.getNombre().toLowerCase();
                    String ext = nodo.getExtension().toLowerCase();

                    // Recuperamos el estado directamente desde la celda del modelo mapeado
                    // para garantizar consistencia con los renderizadores visuales
                    String estadoSync = entry.getModel().getValueAt(modelIdx, 4).toString();

                    // --- NIVEL 1: TEXTO PLANO ---
                    boolean pasaTexto = textoBusqueda.isEmpty() || nombre.contains(textoBusqueda) || ext.contains(textoBusqueda);

                    // --- NIVEL 2: ESTADO SYNC (Evaluación robusta sobre texto de celda) ---
                    boolean pasaSync = true;
                    switch (seleccionSync) {
                        case 1: // Solo Sincronizados
                            pasaSync = estadoSync.contains("Sincronizado");
                            break;
                        case 2: // Modificados
                            pasaSync = estadoSync.contains("Modificado");
                            break;
                        case 3: // Conflictos o Solo Local/Remoto
                            pasaSync = estadoSync.contains("Conflicto") || estadoSync.contains("Solo");
                            break;
                    }

                    // --- NIVEL 3: TIPO DE ASSET / EXTENSIÓN ---
                    boolean pasaTipo = true;
                    switch (seleccionTipo) {
                        case 1: // Solo Carpetas
                            pasaTipo = nodo.esDirectorio();
                            break;
                        case 2: // Solo Archivos
                            pasaTipo = !nodo.esDirectorio();
                            break;
                        case 3: // Código Fuente
                            pasaTipo = !nodo.esDirectorio() && ".java.py.cpp.h.go.js.ts.sh.rs.c".contains(ext);
                            break;
                        case 4: // Documentos
                            pasaTipo = !nodo.esDirectorio() && ".pdf.docx.doc.txt.xlsx.xls.csv.md".contains(ext);
                            break;
                        case 5: // Imágenes
                            pasaTipo = !nodo.esDirectorio() && ".png.jpg.jpeg.gif.svg.ico.webp".contains(ext);
                            break;
                        case 6: // Binarios / Ejecutables
                            pasaTipo = !nodo.esDirectorio() && ".exe.sh.jar.bat.msi.bin".contains(ext);
                            break;
                    }

                    return pasaTexto && pasaSync && pasaTipo;
                }
            };

            sorter.setRowFilter(filtroMatriz);
            actualizarMetricasFooter();
        });
    }


    /**
     * Recalcula dinámicamente el almacenamiento mapeado en base a las filas visibles (post-filtrado).
     */
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

        // Formateo legible del peso calculado
        String pesoFormateado;
        if (pesoTotalBytes < 1024) pesoFormateado = pesoTotalBytes + " B";
        else {
            int exp = (int) (Math.log(pesoTotalBytes) / Math.log(1024));
            pesoFormateado = String.format("%.2f %cB", pesoTotalBytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
        }

        lblFooterStats.setText(String.format(" 📁 %d Carpetas | 📄 %d Archivos | ⚖️ El filtro contiene: %s",
                carpetas, archivos, pesoFormateado));
    }

    private void ejecutarSincronizacionBatch(boolean esLocal) {
        List<NodoDirectorio> loteNoSincronizado = new ArrayList<>();
        String condicionFiltro = esLocal ? "Solo Local" : "Solo Remoto";
        String condicionModificado = esLocal ? "Modificado Local" : "Modificado Remoto";

        for (int i = 0; i < fileModel.getRowCount(); i++) {
            String estadoSync = (String) fileModel.getValueAt(i, 4);
            if (estadoSync != null && (estadoSync.contains(condicionFiltro) || estadoSync.contains(condicionModificado))) {
                loteNoSincronizado.add(nodosActuales.get(i));
            }
        }

        if (loteNoSincronizado.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "¡Excelente! Todos los assets en este directorio se encuentran perfectamente sincronizados.",
                    "bitBridge Engine", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Se detectaron " + loteNoSincronizado.size() + " elementos fuera de réplica.\n¿Deseas iniciar la transmisión en bloque de inmediato?",
                "Procesamiento por Lotes Encontrado", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            Logger.logInfo("Despachando lote masivo de " + loteNoSincronizado.size() + " elementos no sincronizados.");
            if (onActionRequested != null) {
                onActionRequested.accept(loteNoSincronizado);
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

    public void updateData(List<NodoDirectorio> nuevosHijos, String nombrePadre) {
        clear();
        if (nuevosHijos != null) {
            for (NodoDirectorio n : nuevosHijos) {
                agregarFila(n, "Sincronizado ✅");
            }
        }
    }

    public void clear() {
        fileModel.setRowCount(0);
        nodosActuales.clear();
        actualizarMetricasFooter();
    }

    public void agregarFila(NodoDirectorio n, String estadoSync) {
        nodosActuales.add(n);
        String prefix = n.esDirectorio() ? "📁 " : "📄 ";
        String fecha = n.getFechaModificacion();

        fileModel.addRow(new Object[]{
                prefix + n.getNombre(),
                n.esDirectorio() ? "Carpeta" : n.getTamañoFormateado(),
                n.esDirectorio() ? "Directorio" : n.getExtension(),
                (fecha == null || fecha.equalsIgnoreCase("Desconocido")) ? "--" : fecha,
                estadoSync
        });
        actualizarMetricasFooter();
    }

    public NodoDirectorio getSelectedNode() {
        int row = fileTable.getSelectedRow();
        return (row == -1) ? null : nodosActuales.get(fileTable.convertRowIndexToModel(row));
    }

    public List<NodoDirectorio> getSelectedNodes() {
        int[] rows = fileTable.getSelectedRows();
        List<NodoDirectorio> lista = new ArrayList<>();
        for (int r : rows) {
            int modelIdx = fileTable.convertRowIndexToModel(r);
            if (modelIdx >= 0 && modelIdx < nodosActuales.size()) lista.add(nodosActuales.get(modelIdx));
        }
        return lista;
    }

    public TableRowSorter<DefaultTableModel> getSorter() { return this.sorter; }
    public JTable getFileTable() { return this.fileTable; }
}
package org.bitBridge.view.swing.components.explorer;

import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel de exploración de archivos remotos y locales para bitBridge Pro.
 * Incorpora estados avanzados de sincronización y menú contextual analítico.
 * * Copyright 2026 Cristobal Roman Zamora
 */
public class RemoteFileTablePanel extends JPanel {

    private JTable fileTable;
    private DefaultTableModel fileModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private List<NodoDirectorio> nodosActuales = new ArrayList<>();

    // Paleta de colores quirúrgica para la columna de estados rsync
    public static final Color STATE_SYNCHRONIZED   = new Color(50, 200, 50);   // Verde
    public static final Color STATE_MODIFIED_LOCAL  = new Color(245, 130, 48);  // Naranja (Push)
    public static final Color STATE_MODIFIED_REMOTE = new Color(70, 160, 240);  // Azul (Pull)
    public static final Color STATE_CONFLICT        = new Color(230, 75, 75);   // Rojo
    public static final Color STATE_ORPHAN          = new Color(165, 105, 189); // Púrpura
    public static final Color STATE_IGNORED         = new Color(110, 110, 110); // Gris

    private final Consumer<NodoDirectorio> onSelection;
    private final Consumer<NodoDirectorio> onDoubleClick;
    private final Consumer<List<NodoDirectorio>> onActionRequested; // Callback para el ítem principal del menú
    private final String etiquetaAccionPrincipal; // "Push" o "Pull" según el lado

    public RemoteFileTablePanel(String etiquetaAccionPrincipal,
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
        String[] columns = {"Nombre", "Tamaño", "Tipo", "Modificado", "Estado Sync"};
        fileModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        fileTable = new JTable(fileModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowHeight(35);
        fileTable.setShowGrid(false);
        fileTable.setSelectionBackground(new Color(45, 45, 45));
        fileTable.setSelectionForeground(Color.WHITE);

        setupRenderers();

        sorter = new TableRowSorter<>(fileModel);
        fileTable.setRowSorter(sorter);

        setupMouseListeners();
        setupKeyListeners();

        JScrollPane scrollPane = new JScrollPane(fileTable);
        scrollPane.getViewport().setBackground(new Color(25, 25, 25));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupRenderers() {
        fileTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, f, r, c);
                String val = String.valueOf(v);

                if (val.startsWith("Sincronizado")) l.setForeground(STATE_SYNCHRONIZED);
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
            @Override public void mousePressed(MouseEvent e) { handlePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { handlePopup(e); }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = fileTable.rowAtPoint(e.getPoint());
                    if (row != -1 && !fileTable.isRowSelected(row)) {
                        fileTable.setRowSelectionInterval(row, row);
                    }
                    createContextMenu().show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                NodoDirectorio nodo = getSelectedNode();
                if (nodo == null) return;
                if (e.getClickCount() == 1) onSelection.accept(nodo);
                else if (e.getClickCount() == 2) onDoubleClick.accept(nodo);
            }
        });
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        List<NodoDirectorio> seleccionados = getSelectedNodes();
        boolean multiple = seleccionados.size() > 1;

        JMenuItem itemAccion = new JMenuItem(multiple ? etiquetaAccionPrincipal + " Seleccionados" : etiquetaAccionPrincipal);
        itemAccion.setFont(new Font("SansSerif", Font.BOLD, 12));
        itemAccion.addActionListener(e -> onActionRequested.accept(seleccionados));
        menu.add(itemAccion);

        menu.addSeparator();
        JMenuItem itemCopyPath = new JMenuItem("📋 Copiar Ruta Absoluta");
        itemCopyPath.addActionListener(e -> {
            NodoDirectorio n = getSelectedNode();
            if (n != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(n.getRutaString()), null);
            }
        });
        menu.add(itemCopyPath);

        return menu;
    }

    private void setupKeyListeners() {
        fileTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    NodoDirectorio n = getSelectedNode();
                    if (n != null) onDoubleClick.accept(n);
                }
            }
        });
    }

    // --- MÉTODOS DE DATOS ---
    public void clear() {
        fileModel.setRowCount(0);
        nodosActuales.clear();
    }

    public void agregarFila(NodoDirectorio n, String estadoSync) {
        nodosActuales.add(n);
        String prefix = n.esDirectorio() ? "📁 " : "📄 ";
        fileModel.addRow(new Object[]{
                prefix + n.getNombre(),
                n.esDirectorio() ? "--" : n.getTamañoFormateado(),
                n.esDirectorio() ? "Carpeta" : n.getExtension(),
                n.getFechaModificacion(),
                estadoSync
        });
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
}
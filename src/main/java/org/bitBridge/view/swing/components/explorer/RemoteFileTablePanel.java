package org.bitBridge.view.swing.components.explorer;

import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.NodoDirectorio;
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

public class RemoteFileTablePanel extends JPanel {

    private JTable fileTable;
    private DefaultTableModel fileModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private List<NodoDirectorio> nodosActuales = new ArrayList<>();

    // Colores de estado
    private static final Color ACCENT_GREEN = new Color(50, 200, 50);
    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    // Colores de estado
    private static final Color ACCENT_RED = new Color(230, 75, 75); // Rojo sutil y visible
    // Callbacks
    private final Consumer<NodoDirectorio> onSelection;
    private final Consumer<NodoDirectorio> onDoubleClick;
    private final Consumer<NodoDirectorio> onPullRequest;

    public RemoteFileTablePanel(Consumer<NodoDirectorio> onSelection,
                                Consumer<NodoDirectorio> onDoubleClick,
                                Consumer<NodoDirectorio> onPullRequest) {

        this.onSelection = onSelection;
        this.onDoubleClick = onDoubleClick;
        this.onPullRequest = onPullRequest;

        setLayout(new BorderLayout());
        initComponents();
    }

    private void initComponents() {
        String[] columns = {"Nombre", "Tamaño", "Tipo", "Modificado", "Estado"};
        fileModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        fileTable = new JTable(fileModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowHeight(35);
        fileTable.setShowGrid(false);
        fileTable.setSelectionBackground(new Color(45, 45, 45));
        fileTable.setSelectionForeground(Color.WHITE);
        fileTable.setIntercellSpacing(new Dimension(0, 0));

        // Dentro de RemoteFileTablePanel
        fileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                List<NodoDirectorio> seleccion = getSelectedNodes();
                if (!seleccion.isEmpty()) {
                    // Notificamos al inspector de la selección actual
                    //inspector.updateInfo(seleccion);
                }
            }
        });
        // Aplicar Renderers personalizados
        setupRenderers();

        sorter = new TableRowSorter<>(fileModel);
        fileTable.setRowSorter(sorter);

        setupMouseListeners();
        setupKeyListeners();

        add(new JScrollPane(fileTable), BorderLayout.CENTER);
    }

    private void setupRenderers() {
        // Renderer para la columna de Estado (Columna 4)
        fileTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, f, r, c);
                String val = String.valueOf(v);

                // Asignación de colores quirúrgica basada en el estado de sincronización
                if ("Sincronizado".equals(val)) {
                    l.setForeground(ACCENT_GREEN);
                } else if (val.startsWith("No Sincronizado")) {
                    l.setForeground(ACCENT_RED);
                } else if (val.startsWith("Solo Local")) {
                    l.setForeground(NICOTINE_ORANGE); // Color amarillo/naranja para archivos huérfanos locales
                } else if ("Busy".equals(val)) {
                    l.setForeground(Color.CYAN);
                } else {
                    l.setForeground(t.getForeground()); // Color por defecto si no aplica ninguno
                }

                // Mantener el texto centrado para mejor lectura estética
                l.setHorizontalAlignment(SwingConstants.CENTER);
                return l;
            }
        });
    }

    private void setupKeyListeners() {
        fileTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                NodoDirectorio nodo = getSelectedNode();
                if (nodo == null) return;

                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    onDoubleClick.accept(nodo);
                } else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    // Aquí podrías disparar un onRemove.accept(nodo)
                    System.out.println("Solicitud eliminar: " + nodo.getNombre());
                }
            }
        });
    }

    private void setupMouseListeners() {
        JPopupMenu fileMenu = createContextMenu();

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handlePopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handlePopup(e); }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = fileTable.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        fileTable.setRowSelectionInterval(row, row);
                        fileMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                NodoDirectorio nodo = getSelectedNode();
                if (nodo == null) return;

                if (e.getClickCount() == 1) {
                    onSelection.accept(nodo);
                } else if (e.getClickCount() == 2) {
                    onDoubleClick.accept(nodo);
                }
            }
        });
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        // --- SECCIÓN: TRANSFERENCIA ---
        JMenuItem itemPull = new JMenuItem("📥 Descargar (PULL)");
        itemPull.setFont(new Font("SansSerif", Font.BOLD, 12));
        itemPull.addActionListener(e -> {
            NodoDirectorio n = getSelectedNode();
            if (n != null) onPullRequest.accept(n);
        });

        JMenu menuSmartPull = new JMenu("⚡ Descarga Inteligente");
        menuSmartPull.add(new JMenuItem("Sincronización Delta"));
        menuSmartPull.add(new JMenuItem("Descarga Comprimida (LZ4)"));

        // --- SECCIÓN: ACCIONES ---
        JMenuItem itemRename = new JMenuItem("✏️ Renombrar");
        JMenuItem itemDelete = new JMenuItem("🗑️ Eliminar");
        itemDelete.setForeground(new Color(255, 80, 80));

        JMenuItem itemCopyPath = new JMenuItem("📋 Copiar Ruta Absoluta");
        itemCopyPath.addActionListener(e -> {
            NodoDirectorio n = getSelectedNode();
            if (n != null) {
                StringSelection selection = new StringSelection(n.getRutaString());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });

        menu.add(itemPull);
        menu.add(menuSmartPull);
        menu.addSeparator();
        menu.add(itemRename);
        menu.add(itemDelete);
        menu.addSeparator();
        menu.add(itemCopyPath);

        return menu;
    }

    public void updateData(List<NodoDirectorio> nodosRemotos, String directorioPadre) {

        fileModel.setRowCount(0);

        // 1. Obtener la raíz configurada limpia
        String raizCompartidaStr = ConfiguracionApp.getInstancia().getSharedDir();
        File raizFile = new File(raizCompartidaStr);
        String nombreCarpetaRaiz = raizFile.getName(); // Esto devolverá "BitBridge_Shared"

        // 2. Sanitizar el directorio padre
        if (directorioPadre == null || directorioPadre.trim().isEmpty() || directorioPadre.equals(".")) {
            directorioPadre = "";
        } else {
            directorioPadre = directorioPadre.trim();
            // SI el directorio recibido es exactamente el mismo nombre de la raíz, lo volvemos vacío
            if (directorioPadre.equalsIgnoreCase(nombreCarpetaRaiz)) {
                directorioPadre = "";
            }
        }

        // 3. Ahora la combinación de Path será 100% segura y precisa
        Path carpetaActualLocal = Paths.get(raizCompartidaStr, directorioPadre);

        Logger.logInfo("Sincronizando directorio local corregido: " + carpetaActualLocal.toString());
        Logger.logInfo("Filtro de directorio remoto: " + directorioPadre);

        // 3. Escanear ÚNICAMENTE el nivel local plano actual
        List<File> archivosLocales = new ArrayList<>();
        File carpetaLocal = carpetaActualLocal.toFile();
        if (carpetaLocal.exists() && carpetaLocal.isDirectory()) {
            File[] lista = carpetaLocal.listFiles();
            if (lista != null) {
                for (File f : lista) {
                    archivosLocales.add(f);
                }
            }
        }

        List<String> nombresProcesados = new ArrayList<>();
        this.nodosActuales = new ArrayList<>();

        // =========================================================================
        // PASO 1: Procesar Nodos Remotos que pertenecen a este nivel
        // =========================================================================
        for (NodoDirectorio n : nodosRemotos) {
            String nombre = n.getNombre();
            nombresProcesados.add(nombre);
            this.nodosActuales.add(n);

            String prefix = n.esDirectorio() ? "📁 " : "📄 ";
            String estadoSincronizacion = "Sincronizado";

            // Construcción quirúrgica: El archivo local debe vivir exactamente en la carpeta actual
            File archivoLocal = carpetaActualLocal.resolve(nombre).toFile();
            //Logger.logInfo(archivoLocal.getAbsolutePath());

            if (!archivoLocal.exists()) {
                estadoSincronizacion = "No Sincronizado (Falta archivo)";
            } else if (!n.esDirectorio()) {
                // Capa A: Descarte rápido por tamaño en bytes
                if (archivoLocal.length() != n.getTamaño()) {
                    estadoSincronizacion = "No Sincronizado (Tamaño modificado)";
                } else {
                    // Capa B: Integridad profunda por Hash
                    try {
                        String hashLocal = org.bitBridge.utils.HashUtil.getFileChecksum(archivoLocal);
                        if (!hashLocal.equalsIgnoreCase(n.getHash())) {
                            estadoSincronizacion = "No Sincronizado (Contenido diferente)";
                        }
                    } catch (Exception e) {
                        estadoSincronizacion = "No Sincronizado (Error de lectura)";
                    }
                }
            } else if (!archivoLocal.isDirectory()) {
                estadoSincronizacion = "No Sincronizado (Se esperaba carpeta)";
            }

            fileModel.addRow(new Object[]{
                    prefix + nombre,
                    n.esDirectorio() ? "--" : n.getTamañoFormateado(),
                    n.esDirectorio() ? "Carpeta" : n.getExtension(),
                    n.getFechaModificacion(),
                    estadoSincronizacion
            });
        }

        // =========================================================================
        // PASO 2: Encontrar archivos Locales Huérfanos estrictamente en este nivel
        // =========================================================================
        for (File archivoLocal : archivosLocales) {
            String nombreLocal = archivoLocal.getName();

            // Si el servidor no envió este archivo en su lista de este nivel, es "Solo Local"
            if (!nombresProcesados.contains(nombreLocal)) {
                boolean esDir = archivoLocal.isDirectory();
                String prefix = esDir ? "📁 " : "📄 ";

                // Instanciamos el nodo artificial apuntando a su ruta exacta actual
                NodoDirectorio nodoHuerfano = new NodoDirectorio(archivoLocal.toPath());
                this.nodosActuales.add(nodoHuerfano);

                // Calcular tamaño formateado local
                String tamFormateado = "--";
                String extension = "Carpeta";
                if (!esDir) {
                    long bytes = archivoLocal.length();
                    extension = nombreLocal.contains(".") ? nombreLocal.substring(nombreLocal.lastIndexOf('.')).toUpperCase() : "Archivo";
                    if (bytes < 1024) {
                        tamFormateado = bytes + " B";
                    } else {
                        int exp = (int) (Math.log(bytes) / Math.log(1024));
                        tamFormateado = String.format("%.2f %cB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
                    }
                }

                fileModel.addRow(new Object[]{
                        prefix + nombreLocal,
                        tamFormateado,
                        extension,
                        "Local",
                        "Solo Local (No en Réplica)" // Pinta en Nicotine Orange
                });
            }
        }
    }

    public String getRaiz(String rutaCompleta){
        return new File(rutaCompleta).getAbsolutePath();
    }


    public NodoDirectorio getSelectedNode() {
        int row = fileTable.getSelectedRow();
        if (row == -1) return null;
        try {
            return nodosActuales.get(fileTable.convertRowIndexToModel(row));
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    // NUEVO MÉTODO: Para obtener todos los archivos seleccionados a la vez
    public List<NodoDirectorio> getSelectedNodes() {
        int[] selectedRows = fileTable.getSelectedRows();
        List<NodoDirectorio> seleccionados = new ArrayList<>();

        for (int row : selectedRows) {
            int modelRow = fileTable.convertRowIndexToModel(row);
            seleccionados.add(nodosActuales.get(modelRow));
        }
        return seleccionados;
    }

    public TableRowSorter<DefaultTableModel> getSorter() { return sorter; }
}
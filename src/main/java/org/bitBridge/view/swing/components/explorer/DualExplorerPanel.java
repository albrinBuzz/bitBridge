package org.bitBridge.view.swing.components.explorer;

import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Layout de Doble Espejo Sincronizado Lado a Lado.
 * Controla visualmente el foco del panel activo, encapsula la lógica Quick-Check
 * y propaga los nodos raíz actuales para soportar sincronizaciones masivas One-Shot.
 * * Copyright 2026 Cristobal Roman Zamora
 */
public class DualExplorerPanel extends JPanel {

    private FileTablePanel localTable;
    private FileTablePanel remoteTable;

    private JPanel leftHeader;
    private JPanel rightHeader;
    private JLabel lblLocalPath;
    private JLabel lblRemotePath;

    private static final Color HEADER_BG_ACTIVE = new Color(45, 54, 66);
    private static final Color HEADER_BG_INACTIVE = new Color(28, 28, 28);
    private static final Color FOCUS_BORDER_COLOR = new Color(255, 165, 0); // Nicotine Orange

    private Consumer<NodoDirectorio> onLocalFolderNav = n -> {};
    private Consumer<NodoDirectorio> onRemoteFolderNav = n -> {};
    private Consumer<List<NodoDirectorio>> onPushExecution = l -> {};
    private Consumer<List<NodoDirectorio>> onPullExecution = l -> {};
    private Runnable onFocusChanged = () -> {};

    private boolean isLocalFocused = true;

    public DualExplorerPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(20, 20, 20));
        initComponents();
    }

    private void initComponents() {
        // --- PANEL LOCAL (IZQUIERDO) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setOpaque(false);

        leftHeader = new JPanel(new BorderLayout());
        leftHeader.setBackground(HEADER_BG_ACTIVE);
        leftHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, FOCUS_BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        lblLocalPath = new JLabel("💻 LOCAL: /");
        lblLocalPath.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblLocalPath.setForeground(Color.WHITE);
        leftHeader.add(lblLocalPath, BorderLayout.WEST);
        leftPanel.add(leftHeader, BorderLayout.NORTH);

        localTable = new FileTablePanel("📤 PUSH",
                nodo -> {},
                nodo -> { navegarLocalInterno(nodo); },
                nodos -> { if(onPushExecution != null) onPushExecution.accept(nodos); }
        );
        leftPanel.add(localTable, BorderLayout.CENTER);

        // --- PANEL REMOTO (DERECHO) ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);

        rightHeader = new JPanel(new BorderLayout());
        rightHeader.setBackground(HEADER_BG_INACTIVE);
        rightHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(50, 50, 50)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        lblRemotePath = new JLabel("🛰️ REMOTO: /");
        lblRemotePath.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblRemotePath.setForeground(Color.LIGHT_GRAY);
        rightHeader.add(lblRemotePath, BorderLayout.WEST);
        rightPanel.add(rightHeader, BorderLayout.NORTH);

        remoteTable = new FileTablePanel("📥 PULL",
                nodo -> {},
                nodo -> { navegarRemotoInterno(nodo); },
                nodos -> { if(onPullExecution != null) onPullExecution.accept(nodos); }
        );
        rightPanel.add(remoteTable, BorderLayout.CENTER);

        // --- MANEJADORES DE FOCO ---
        localTable.fileTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { switchFocus(true); }
        });
        remoteTable.fileTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { switchFocus(false); }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(0.5);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(5);
        splitPane.setBorder(new EmptyBorder(0,0,0,0));

        add(splitPane, BorderLayout.CENTER);
    }

    private void switchFocus(boolean local) {
        this.isLocalFocused = local;
        if (local) {
            leftHeader.setBackground(HEADER_BG_ACTIVE);
            leftHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 3, 0, FOCUS_BORDER_COLOR),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)));
            lblLocalPath.setForeground(Color.WHITE);

            rightHeader.setBackground(HEADER_BG_INACTIVE);
            rightHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(50, 50, 50)),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)));
            lblRemotePath.setForeground(Color.GRAY);
            remoteTable.fileTable.clearSelection();
        } else {
            rightHeader.setBackground(HEADER_BG_ACTIVE);
            rightHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 3, 0, FOCUS_BORDER_COLOR),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)));
            lblRemotePath.setForeground(Color.WHITE);

            leftHeader.setBackground(HEADER_BG_INACTIVE);
            leftHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(50, 50, 50)),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)));
            lblLocalPath.setForeground(Color.GRAY);
            localTable.fileTable.clearSelection();
        }
        if (onFocusChanged != null) onFocusChanged.run();
    }

    private void navegarLocalInterno(NodoDirectorio nodo) {
        if (onLocalFolderNav != null) onLocalFolderNav.accept(nodo);
    }

    private void navegarRemotoInterno(NodoDirectorio nodo) {
        if (onRemoteFolderNav != null) onRemoteFolderNav.accept(nodo);
    }

    /**
     * ⚡ SOBRECARGA: Mantiene compatibilidad con invocaciones antiguas de firmas de strings,
     * derivando la ejecución sin romper vistas ya existentes.
     */
    public void coordinarEstructuras(List<NodoDirectorio> locales, List<NodoDirectorio> remotos, String pathL, String pathR) {
        coordinarEstructuras(null, locales, null, remotos, pathL, pathR);
    }

    /**
     * 🚀 ENFOQUE INTEGRAL PROPAGADO: Registra los contenedores estructurales de origen
     * para alimentar el pipeline atómico en caso de forzar transmisiones masivas directas.
     */
    public void coordinarEstructuras(NodoDirectorio raizLocal, List<NodoDirectorio> locales,
                                     NodoDirectorio raizRemoto, List<NodoDirectorio> remotos,
                                     String pathL, String pathR) {
        lblLocalPath.setText("💻 LOCAL: " + pathL);
        lblRemotePath.setText("🛰️ REMOTO: " + pathR);

        localTable.clear();
        remoteTable.clear();

        // 🔑 PROPAGACIÓN HACIA LAS TABLAS ATÓMICAS:
        localTable.setNodoRaizActual(raizLocal);
        remoteTable.setNodoRaizActual(raizRemoto);

        Map<String, NodoDirectorio> mapaRemoto = new HashMap<>();
        if (remotos != null) {
            for (NodoDirectorio r : remotos) mapaRemoto.put(r.getNombre(), r);
        }

        Map<String, NodoDirectorio> mapaLocal = new HashMap<>();
        if (locales != null) {
            for (NodoDirectorio l : locales) mapaLocal.put(l.getNombre(), l);
        }

        if (locales != null) {
            for (NodoDirectorio l : locales) {
                String nombre = l.getNombre();
                String estadoSync = "Solo Local";
                if (mapaRemoto.containsKey(nombre)) {
                    estadoSync = calcularEstadoQuickCheck(l, mapaRemoto.get(nombre));
                }
                localTable.agregarFila(l, estadoSync);
            }
        }

        if (remotos != null) {
            for (NodoDirectorio r : remotos) {
                String nombre = r.getNombre();
                String estadoSync = "Solo Remoto";
                if (mapaLocal.containsKey(nombre)) {
                    estadoSync = calcularEstadoQuickCheck(mapaLocal.get(nombre), r);
                }
                remoteTable.agregarFila(r, estadoSync);
            }
        }
    }

    private String calcularEstadoQuickCheck(NodoDirectorio l, NodoDirectorio r) {
        // 1. Detección inmediata de Huérfanos Absolutos
        if (l == null && r != null) return "Modificado Remoto"; // No existe localmente, el remoto tiene los datos
        if (l != null && r == null) return "Modificado Local";  // Existe local, pero no ha sido subido al remoto
        if (l == null && r == null) return "Sincronizado ✅";

        // 2. Conflicto Estructural Crítico (Ej: Un archivo reemplazó a una carpeta o viceversa)
        if (l.esDirectorio() != r.esDirectorio()) {
            return "⚠️ Conflicto";
        }

        long sizeL = l.getTamaño();
        long sizeR = r.getTamaño();
        long timeL = l.getFechaModificacionMillis();
        long timeR = r.getFechaModificacionMillis();

        // 3. Caso de Éxito: Tamaños idénticos en bytes
        // Inmune a las fechas alteradas por el software de Backup local.
        if (sizeL == sizeR) {
            return "Sincronizado ✅";
        }

        // =========================================================================
        // ⚡ EVALUACIÓN DETALLADA POR VARIACIÓN DE VOLUMEN (EL TAMAÑO MANDA)
        // =========================================================================

        // CASO A: El tamaño local es MAYOR que el remoto
        // Esto significa que el origen local tiene más archivos, subcarpetas o datos nuevos.
        if (sizeL > sizeR) {
            // Confirmación por tiempo: Si además la fecha local es más reciente o igual, es un Modificado Local indiscutible.
            // Si la fecha remota fuese mayor (falso positivo del servidor), el peso local sigue mandando aquí.
            return "Modificado Local";
        }

        // CASO B: El tamaño remoto es MAYOR que el local
        // Esto significa que el servidor remoto contiene datos nuevos, más archivos o cambios que no están abajo.
        if (sizeR > sizeL) {
            // Aun si la fecha del backup local dice ser "más nueva", el volumen del remoto demuestra
            // matemáticamente que el local está incompleto y le faltan archivos.
            return "Modificado Remoto";
        }

        // 4. Salida de emergencia por descalce cronológico residual
        // (En caso de que los tamaños fuesen distintos pero no cayera en las condiciones anteriores)
        return (timeL > timeR) ? "Modificado Local" : "Modificado Remoto";
    }




    public boolean isLocalFocused() { return isLocalFocused; }
    public void setOnFocusChanged(Runnable callback) { this.onFocusChanged = callback; }
    public void setOnLocalFolderNav(Consumer<NodoDirectorio> callback) { this.onLocalFolderNav = callback; }
    public void setOnRemoteFolderNav(Consumer<NodoDirectorio> callback) { this.onRemoteFolderNav = callback; }
    public void setOnPushExecution(Consumer<List<NodoDirectorio>> callback) { this.onPushExecution = callback; }
    public void setOnPullExecution(Consumer<List<NodoDirectorio>> callback) { this.onPullExecution = callback; }

    public FileTablePanel getLocalTablePanel() { return localTable; }
    public FileTablePanel getRemoteTablePanel() { return remoteTable; }

}
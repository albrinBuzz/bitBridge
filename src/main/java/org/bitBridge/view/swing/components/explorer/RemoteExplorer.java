package org.bitBridge.view.swing.components.explorer;

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.RemoteDirectoryListener;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.model.basic.FilePullRequest;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Explorador Unificado Lado a Lado (Local vs Remoto) para bitBridge Pro.
 * Conecta eventos asíncronos de red de Netty/NIO con el motor Rsync Quick-Check de la UI.
 * * Copyright 2026 Cristobal Roman Zamora
 */
public class RemoteExplorer extends JFrame implements RemoteDirectoryListener {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARKER = new Color(20, 20, 20);

    // Contexto de Rutas y Datos
    private NodoDirectorio raizDatos;
    private NodoDirectorio nodoRemotoActual;
    private String rutaLocalActual = "";
    private String rutaRemotaActual = "";

    // Pilas de navegación del ecosistema de red (Historial Remoto)
    private Deque<NodoDirectorio> backStack = new ArrayDeque<>();
    private Deque<NodoDirectorio> forwardStack = new ArrayDeque<>();

    // Componentes core del Layout
    private FileInspectorPanel inspector;
    private DualExplorerPanel dualExplorerPanel; // ⚡ Reemplaza a RemoteFileTablePanel
    private BreadcrumbBar breadcrumbBar;

    private JComboBox<String> comboFiltro;
    private JButton btnBack;
    private JButton btnForward;
    private JButton btnHome;

    private Client client;
    private String targetIp;

    private final String[] OPCIONES_FILTRO = {
            "Todos los archivos",
            "Solo Carpetas",
            "Imágenes (jpg, png, gif)",
            "Multimedia (mp4, mkv, mp3)",
            "Documentos (pdf, docx, txt)",
            "Ejecutables (exe, sh, bat)"
    };

    public RemoteExplorer(Client client, String targetIp) throws IOException {
        this.client = client;
        this.targetIp = targetIp;
        // Cargamos el punto de montaje local por defecto del cliente
        this.rutaLocalActual = ConfiguracionApp.getInstancia().getSharedDir();
        setupGUI();

        // Solicitar el escaneo inicial raíz al nodo remoto
        solicitarDirectorioRemoto("");
    }

    public RemoteExplorer(String rutaInicial) throws IOException {
        this.rutaLocalActual = rutaInicial;
        setupGUI();
    }

    private void setupGUI() {
        FlatOneDarkIJTheme.setup();

        setTitle("BitBridge Pro - Advanced Remote Assets Explorer");
        setSize(1600, 950);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        if (client != null) {
            client.addDirectoryListener(this);
        }

        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (client != null) {
                    client.removeDirectoryListener(RemoteExplorer.this);
                    Logger.logInfo("Explorador remoto cerrado.");
                }
            }
        });

        inspector = new FileInspectorPanel();
        breadcrumbBar = new BreadcrumbBar(nodo -> navegarA(nodo));

        // =========================================================================
        // CONSTRUCCIÓN E INTERCONEXIÓN DEL PANEL DUAL DE TABLAS
        // =========================================================================
        dualExplorerPanel = new DualExplorerPanel();

        // 1. Enlazar navegación de carpetas por doble clic
        dualExplorerPanel.setOnLocalFolderNav(nodo -> {
            if (nodo.esDirectorio()) {
                File nuevaRuta = new File(rutaLocalActual, nodo.getNombre());
                if (nuevaRuta.isDirectory()) {
                    this.rutaLocalActual = nuevaRuta.getAbsolutePath();
                    Logger.logInfo("Navegando Local a: " + rutaLocalActual);
                    // Aquí refrescas tus archivos locales usando tu motor de File
                }
            }
        });

        dualExplorerPanel.setOnRemoteFolderNav(nodo -> {
            if (nodo.esDirectorio()) {
                Logger.logInfo("Navegando Remoto a: " + nodo.getNombre());
                navegarA(nodo); // Tu método existente para despachar el paquete Netty
            }
        });

        // 2. Control de selección sincronizado con el Inspector lateral
        dualExplorerPanel.getLocalTablePanel().getFileTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && dualExplorerPanel.isLocalFocused()) {
                NodoDirectorio n = dualExplorerPanel.getLocalTablePanel().getSelectedNode();
                if (n != null) {
                    inspector.updateInfo(n);
                    inspector.configurarModoBoton(true); // Activa PUSH (Verde)
                }
            }
        });

        dualExplorerPanel.getRemoteTablePanel().getFileTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !dualExplorerPanel.isLocalFocused()) {
                NodoDirectorio n = dualExplorerPanel.getRemoteTablePanel().getSelectedNode();
                if (n != null) {
                    inspector.updateInfo(n);
                    inspector.configurarModoBoton(false); // Activa PULL (Naranja)
                }
            }
        });

        // 3. Manejo interactivo del Foco de Paneles (Clics en áreas vacías)
        dualExplorerPanel.setOnFocusChanged(() -> {
            boolean localEnFoco = dualExplorerPanel.isLocalFocused();
            inspector.configurarModoBoton(localEnFoco);

            NodoDirectorio n = localEnFoco
                    ? dualExplorerPanel.getLocalTablePanel().getSelectedNode()
                    : dualExplorerPanel.getRemoteTablePanel().getSelectedNode();

            if (n != null) {
                inspector.updateInfo(n);
            } else {
                inspector.clear();
            }
        });

        // 4. Disparador del botón de transferencia unificado hacia el pipeline de Red/Rsync
        inspector.getBtnPullAction().addActionListener(e -> {
            boolean esPush = dualExplorerPanel.isLocalFocused();
            if (esPush) {
                List<NodoDirectorio> seleccionados = dualExplorerPanel.getLocalTablePanel().getSelectedNodes();
                if (!seleccionados.isEmpty()) {
                    Logger.logInfo("Disparando pipeline de subida PUSH para " + seleccionados.size() + " elementos.");
                    // Tu lógica existente para subir datos
                }
            } else {
                List<NodoDirectorio> seleccionados = dualExplorerPanel.getRemoteTablePanel().getSelectedNodes();
                if (!seleccionados.isEmpty()) {
                    Logger.logInfo("Disparando pipeline de descarga PULL para " + seleccionados.size() + " elementos.");
                    // Tu lógica existente para procesar solicitudes de FilePullRequest
                }
            }
        });

        // 1. TOOLBAR SUPERIOR GENERAL
        add(createGlobalToolBar(), BorderLayout.NORTH);

        // 2. PANEL CENTRAL (Split lateral)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(250);
        mainSplit.setLeftComponent(createSidePanel());
        mainSplit.setRightComponent(createMainExplorationTabs());

        add(mainSplit, BorderLayout.CENTER);

        // 3. BARRA DE ESTADO INFERIOR
        add(createStatusBar(), BorderLayout.SOUTH);
    }
    /**
     * Intercepta las colecciones de datos físicos e inyecta la matriz cruzada a ambas tablas.
     */
    private void refrescarEspejoRsync(List<NodoDirectorio> remotosNuevos) {
        List<NodoDirectorio> locales = new ArrayList<>();
        File dirLocal = new File(this.rutaLocalActual);

        if (dirLocal.exists() && dirLocal.isDirectory()) {
            File[] files = dirLocal.listFiles();
            if (files != null) {
                for (File f : files) {
                    locales.add(new NodoDirectorio(f.toPath()));
                }
            }
        }

        // Si no se proveen nodos remotos nuevos (ej. navegamos en local), reusamos los elementos de la tabla derecha
        List<NodoDirectorio> remotosAUsar = (remotosNuevos != null && !remotosNuevos.isEmpty())
                ? remotosNuevos
                : dualExplorerPanel.getRemoteTablePanel().getSelectedNodes(); // O preservas estado previo

        dualExplorerPanel.coordinarEstructuras(locales, remotosAUsar, this.rutaLocalActual, this.rutaRemotaActual);
    }

    private void solicitarDirectorioRemoto(String rutaDestino) {
        if (client == null) return;
        try {
            client.requestFileList(targetIp, rutaDestino);
        } catch (IOException e) {
            Logger.logError("Error enviando petición de listado: " + e.getMessage());
        }
    }

    private void ejecutarPull(NodoDirectorio nodo) {
        new Thread(() -> {
            try {
                Logger.logInfo("Petición de PULL enviada: " + nodo.getNombre());

                FilePullRequest request = new FilePullRequest(
                        nodo.getRutaString(),
                        nodo.getNombre(),
                        targetIp,
                        client.getHostName(),
                        nodo.esDirectorio()
                );

                client.enviarComunicacion(request);

                SwingUtilities.invokeLater(() -> {
                    Logger.logInfo("Transferencia iniciada para: " + nodo.getNombre());
                });

            } catch (Exception ex) {
                Logger.logError("Fallo en descarga: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Error de red: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // =========================================================================
    // 🌐 CAPA DE ESCUCHA ASÍNCRONA (EVENTOS NETWORK)
    // =========================================================================
    @Override
    public void onDirectoryDataReceived(NodoDirectorio nodo) {
        Logger.logInfo("Datos entrantes del nodo: " + nodo.getNombre());

        if (this.raizDatos == null) {
            this.raizDatos = nodo;
        }

        if (this.nodoRemotoActual != null) {
            backStack.push(this.nodoRemotoActual);
            forwardStack.clear();
        }

        this.nodoRemotoActual = nodo;
        this.rutaRemotaActual = nodo.getRutaString();

        SwingUtilities.invokeLater(() -> {
            // Sincronizar y actualizar las dos tablas en paralelo
            refrescarEspejoRsync(nodoRemotoActual.getHijos());

            // Actualizar elementos dinámicos de cabecera
            breadcrumbBar.updatePath(raizDatos, nodoRemotoActual);
            actualizarEstadoBotones();
        });
    }

    private void navegarA(NodoDirectorio destino) {
        if (destino == null || !destino.esDirectorio()) return;
        Logger.logInfo("Navegando hacia nodo remoto: " + destino.getNombre());
        solicitarDirectorioRemoto(destino.getRutaString());
    }

    private void navegarAtras() {
        if (!backStack.isEmpty()) {
            forwardStack.push(nodoRemotoActual);
            NodoDirectorio destino = backStack.pop();
            solicitarDirectorioRemoto(destino.getRutaString());
        }
    }

    private void navegarAdelante() {
        if (!forwardStack.isEmpty()) {
            backStack.push(nodoRemotoActual);
            NodoDirectorio destino = forwardStack.pop();
            solicitarDirectorioRemoto(destino.getRutaString());
        }
    }

    private void actualizarEstadoBotones() {
        btnBack.setEnabled(!backStack.isEmpty());
        btnForward.setEnabled(!forwardStack.isEmpty());
    }

    // =========================================================================
    // ⚙️ FILTRADO COMPUESTO DE EVENTOS DE INTERFAZ
    // =========================================================================
    private void aplicarFiltro(String textoBusqueda, String categoria) {
        // Obtenemos los sorters de ambas tablas para filtrarlas en simultáneo
        TableRowSorter<DefaultTableModel> sorterLocal = dualExplorerPanel.getLocalTablePanel().getSorter();
        TableRowSorter<DefaultTableModel> sorterRemoto = dualExplorerPanel.getRemoteTablePanel().getSorter();

        RowFilter<DefaultTableModel, Integer> filtroComun = new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String nombre = entry.getStringValue(0).toLowerCase();
                String tipo = entry.getStringValue(2).toLowerCase();
                String busqueda = textoBusqueda.toLowerCase();

                boolean cumpleCategoria = true;
                switch (categoria) {
                    case "Solo Carpetas": cumpleCategoria = nombre.startsWith("📁"); break;
                    case "Imágenes (jpg, png, gif)": cumpleCategoria = "jpg png gif jpeg".contains(tipo); break;
                    case "Multimedia (mp4, mkv, mp3)": cumpleCategoria = "mp4 mkv mp3 avi".contains(tipo); break;
                    case "Documentos (pdf, docx, txt)": cumpleCategoria = "pdf docx txt odt".contains(tipo); break;
                    case "Ejecutables (exe, sh, bat)": cumpleCategoria = "exe sh bat jar".contains(tipo); break;
                }

                if (busqueda.startsWith(".")) {
                    return tipo.contains(busqueda.replace(".", "")) && cumpleCategoria;
                }
                if (busqueda.equals("carpetas") || busqueda.equals("dir")) {
                    return nombre.startsWith("📁");
                }

                boolean cumpleTexto = nombre.contains(busqueda) || tipo.contains(busqueda);
                return cumpleCategoria && cumpleTexto;
            }
        };

        if (textoBusqueda.trim().isEmpty() && categoria.equals(OPCIONES_FILTRO[0])) {
            sorterLocal.setRowFilter(null);
            sorterRemoto.setRowFilter(null);
        } else {
            sorterLocal.setRowFilter(filtroComun);
            sorterRemoto.setRowFilter(filtroComun);
        }
    }

    // =========================================================================
    // 🛠️ MÉTODOS DE CONSTRUCCIÓN VISUAL DE SOPORTE
    // =========================================================================
    private JPanel createGlobalToolBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setBackground(BG_DARKER);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, Color.DARK_GRAY));

        bar.add(new JButton("🔌 Conectar a Nodo"));
        bar.add(new JButton("📁 Compartir Local"));
        bar.add(new JSeparator(SwingConstants.VERTICAL));

        JLabel sysStats = new JLabel(" CPU: 12% | RAM: 1.2GB/64GB | Latencia: 15ms ");
        sysStats.setForeground(Color.GRAY);
        bar.add(sysStats);

        return bar;
    }

    private JPanel createSidePanel() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(BG_DARKER);

        DefaultListModel<String> favModel = new DefaultListModel<>();
        favModel.addElement("⭐ Servidor Principal i7");
        favModel.addElement("⭐ Backup Gigabyte B760");
        favModel.addElement("📁 /home/user/logs");
        favModel.addElement("📁 /var/www/assets");

        JList<String> favList = new JList<>(favModel);
        favList.setBackground(BG_DARKER);
        favList.setFixedCellHeight(35);
        favList.setBorder(new TitledBorder(new LineBorder(Color.DARK_GRAY), "Favoritos"));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Infraestructura");
        DefaultMutableTreeNode node1 = new DefaultMutableTreeNode("NODO-REMOTO-01");
        node1.add(new DefaultMutableTreeNode("BitBridge-Shared"));
        node1.add(new DefaultMutableTreeNode("System-Backups"));
        root.add(node1);

        JTree tree = new JTree(root);
        tree.setBackground(BG_DARKER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(favList), new JScrollPane(tree));
        split.setDividerLocation(200);

        side.add(split, BorderLayout.CENTER);
        return side;
    }

    private JPanel createNavigationControls() {
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navButtons.setOpaque(false);

        btnBack = new JButton("⬅");
        btnForward = new JButton("➡");
        btnHome = new JButton("🏠");

        btnBack.setToolTipText("Atrás");
        btnForward.setToolTipText("Adelante");
        btnHome.setToolTipText("Ir al Directorio Raíz");

        btnBack.addActionListener(e -> navegarAtras());
        btnForward.addActionListener(e -> navegarAdelante());
        btnHome.addActionListener(e -> navegarA(raizDatos));

        navButtons.add(btnBack);
        navButtons.add(btnForward);
        navButtons.add(btnHome);

        return navButtons;
    }

    private JTabbedPane createMainExplorationTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("🌐 Explorador Dual Sincronizado", createRemoteExplorerPanel());
        tabs.addTab("📥 Cola de Transferencias", createQueuePanel());
        return tabs;
    }

    private JPanel createRemoteExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARKER);

        JPanel navContainer = new JPanel(new BorderLayout());
        navContainer.setOpaque(false);

        JToolBar actions = new JToolBar();
        actions.setFloatable(false);
        actions.setBackground(BG_DARKER);

        actions.add(createNavigationControls());
        actions.addSeparator();

        actions.add(createNavButton("➕ Nueva Carpeta", "Crear"));
        actions.addSeparator();
        actions.add(Box.createHorizontalGlue());

        JTextField search = new JTextField(15);
        search.putClientProperty("JTextField.placeholderText", "🔍 Ej: .pdf, carpetas...");

        comboFiltro = new JComboBox<>(OPCIONES_FILTRO);
        comboFiltro.setMaximumSize(new Dimension(200, 30));
        comboFiltro.addActionListener(e -> aplicarFiltro(search.getText(), (String) comboFiltro.getSelectedItem()));

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
            private void filtrar() {
                SwingUtilities.invokeLater(() -> aplicarFiltro(search.getText(), (String) comboFiltro.getSelectedItem()));
            }
        });

        actions.add(new JLabel("  Tipo: "));
        actions.add(comboFiltro);
        actions.addSeparator();
        actions.add(new JLabel("  🔍 Buscar: "));
        actions.add(search);

        navContainer.add(actions, BorderLayout.NORTH);
        navContainer.add(breadcrumbBar, BorderLayout.SOUTH);

        // Ajustamos la UI central inyectando nuestro panel dual interactivo al lado del inspector
        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dualExplorerPanel, inspector);
        contentSplit.setDividerLocation(1150);
        contentSplit.setResizeWeight(0.85);

        panel.add(navContainer, BorderLayout.NORTH);
        panel.add(contentSplit, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createQueuePanel() {
        JPanel p = new JPanel(new BorderLayout());
        String[] cols = {"Archivo", "Progreso", "Velocidad", "Estado"};
        DefaultTableModel m = new DefaultTableModel(cols, 0);
        m.addRow(new Object[]{"movie.mkv", "45%", "120 MB/s", "Descargando..."});
        m.addRow(new Object[]{"backup.zip", "100%", "0 MB/s", "Completado"});

        JTable t = new JTable(m);
        t.setRowHeight(30);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private JButton createNavButton(String text, String tt) {
        JButton b = new JButton(text);
        b.setToolTipText(tt);
        return b;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(BG_DARKER);
        status.setBorder(new EmptyBorder(5, 10, 5, 10));
        JLabel left = new JLabel("BitBridge Engine v2.6.0-PRO | Sockets: 156 OK");
        JLabel right = new JLabel(" DL: 850 Mbps | UL: 120 Mbps ");
        right.setForeground(NICOTINE_ORANGE);
        status.add(left, BorderLayout.WEST);
        status.add(right, BorderLayout.EAST);
        return status;
    }
}
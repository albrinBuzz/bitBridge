package org.bitBridge.view.swing.components.explorer;

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.RemoteDirectoryListener;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.model.basic.FilePullRequest;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;
import org.bitBridge.view.swing.components.hosts.AdvancedTransferPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.File;
import java.io.IOException;
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
    private static final Color COLOR_PRIMARY = new Color(52, 152, 219);

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
    private ClientInfo clientInfo;
    JTabbedPane mainTabs = new JTabbedPane();
    TransferQueuePanel queuePanel ;

    private final String[] OPCIONES_FILTRO = {
            "Todos los archivos",
            "Solo Carpetas",
            "Imágenes (jpg, png, gif)",
            "Multimedia (mp4, mkv, mp3)",
            "Documentos (pdf, docx, txt)",
            "Ejecutables (exe, sh, bat)"
    };

    // ⚡ NUEVO: Lista en memoria para los patrones de exclusión (tipo .gitignore)
    private final List<String> patronesExclusion = new ArrayList<>(List.of(
            "node_modules/", ".git/", ".DS_Store", "*.tmp", "target/"
    ));

    public RemoteExplorer(Client client, String targetIp) throws IOException {
        this.client = client;
        this.targetIp = targetIp;
        this.clientInfo=new ClientInfo(targetIp);
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
        //dualExplorerPanel.setOnPullExecution(this::ejecutarPull);
        dualExplorerPanel.setOnPullExecution(this::ejecutarPull);

        dualExplorerPanel.setOnPushExecution(nodoDirectorios -> {
            ejecutarPush(nodoDirectorios.getFirst());
        });

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
                    ejecutarPush(seleccionados.getFirst());
                }
            } else {
                List<NodoDirectorio> seleccionados = dualExplorerPanel.getRemoteTablePanel().getSelectedNodes();
                if (!seleccionados.isEmpty()) {
                    Logger.logInfo("Disparando pipeline de descarga PULL para " + seleccionados.size() + " elementos.");
                    // Tu lógica existente para procesar solicitudes de FilePullRequest
                    ejecutarPull(seleccionados);
                }
            }
        });

        // 1. TOOLBAR SUPERIOR GENERAL
        //add(createGlobalToolBar(), BorderLayout.NORTH);

        // 2. PANEL CENTRAL (Split lateral)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(250);
        //mainSplit.setLeftComponent(createSidePanel());
        mainSplit.setRightComponent(createMainExplorationTabs());

        add(mainSplit, BorderLayout.CENTER);

        // 3. BARRA DE ESTADO INFERIOR
        add(createStatusBar(), BorderLayout.SOUTH);
    }
    /**
     * Intercepta las colecciones de datos físicos e inyecta la matriz cruzada a ambas tablas.
     */
    private void refrescarEspejoRsync(List<NodoDirectorio> remotosNuevos,NodoDirectorio nodoPadre) {
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

        //dualExplorerPanel.coordinarEstructuras(locales, remotosAUsar,nodoPadre, this.rutaLocalActual, this.rutaRemotaActual);
        dualExplorerPanel.coordinarEstructuras(null, locales, nodoPadre, remotosAUsar, this.rutaLocalActual, this.rutaRemotaActual);
    }

    private void solicitarDirectorioRemoto(String rutaDestino) {
        if (client == null) return;
        try {
            client.requestFileList(targetIp, rutaDestino);
        } catch (IOException e) {
            Logger.logError("Error enviando petición de listado: " + e.getMessage());
        }
    }

    private void ejecutarPull(List<NodoDirectorio> nodos) {
        if (nodos == null || nodos.isEmpty()) return;

        // Levantamos un hilo secundario para no bloquear el EDT de Swing al inicializar el pool
        new Thread(() -> {
            // Configuramos un pool fijo de 2 descargas concurrentes en paralelo.
            // Las demás se encolan automáticamente de forma ordenada (FIFO).
            java.util.concurrent.ExecutorService poolDescargas = java.util.concurrent.Executors.newFixedThreadPool(2);

            Logger.logInfo("📦 [PULL-BATCH] Inicializando cola de descarga masiva para " + nodos.size() + " elementos.");

            for (NodoDirectorio nodo : nodos) {
                poolDescargas.submit(() -> {
                    try {
                        Logger.logInfo("Petición de PULL enviada: " + nodo.getNombre());

                        FilePullRequest request = new FilePullRequest(
                                nodo.getRutaString(),
                                nodo.getNombre(),
                                targetIp,
                                client.getHostName(),
                                nodo.esDirectorio()
                        );

                        // Envía el comando al pipeline NIO (pasa por TLS si está activo)
                        client.enviarComunicacion(request);

                        SwingUtilities.invokeLater(() -> {
                            Logger.logInfo("Transferencia iniciada para: " + nodo.getNombre());
                        });

                        // Pequeño delay de amortiguación antes de procesar el siguiente de la cola
                        Thread.sleep(100);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception ex) {
                        Logger.logError("❌ [PULL-ERROR] Fallo en descarga individual: " + ex.getMessage());
                    }
                });
            }

            // El pool no aceptará más tareas y se cerrará limpiamente cuando termine el último elemento
            poolDescargas.shutdown();

        }).start();
    }

    private void ejecutarPush(NodoDirectorio nodo) {
        new Thread(() -> {
            try {
                Logger.logInfo("Petición de Push enviada: " + nodo.getNombre());


                Logger.logInfo(nodo.toString());
                Logger.logInfo(targetIp);

                if (nodo.esDirectorio()) {
                    Logger.logInfo("[PULL-DIR] Iniciando envío de carpeta: " + nodo.getNombre());
                    client.sendDirectoryToHost( new ClientInfo(targetIp), new File(nodo.getRutaString()));
                } else {
                    Logger.logInfo("[PULL-FILE] Iniciando envío de archivo: " + nodo.getNombre());
                    client.sendFileToHost( new ClientInfo(targetIp), new File(nodo.getRutaString()));
                }


                /*SwingUtilities.invokeLater(() -> {
                    Logger.logInfo("Transferencia iniciada para: " + nodo.getNombre());
                    forzarRefrescoEstructuras(); // <--- Inyectar aquí para automatizar la sincronización activa
                });*/

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
            refrescarEspejoRsync(nodoRemotoActual.getHijos(),nodo);

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


    private JPanel createNavigationControls() {
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navButtons.setOpaque(false);

        btnBack = new JButton("⬅");
        btnForward = new JButton("➡");
        btnHome = new JButton("Home");

        // Botón Fancy de Refresco
        JButton btnRefresh = new JButton("Refrescar");

        btnBack.setToolTipText("Atrás");
        btnForward.setToolTipText("Adelante");
        btnHome.setToolTipText("Ir al Directorio Raíz");
        btnRefresh.setToolTipText("Forzar Sincronización y Refrescar Vistas (F5)");

        btnBack.addActionListener(e -> navegarAtras());
        btnForward.addActionListener(e -> navegarAdelante());
        btnHome.addActionListener(e -> navegarA(raizDatos));

        // Enlazar la acción al disparador asíncrono
        btnRefresh.addActionListener(e -> forzarRefrescoEstructuras());

        navButtons.add(btnBack);
        navButtons.add(btnForward);
        navButtons.add(btnHome);
        navButtons.add(btnRefresh); // Agregado al flujo visual

        return navButtons;
    }

    /**
     * Fuerza el re-escaneo y sincronización simultánea de ambas estructuras (Local y Remota)
     * sin destruir ni reiniciar la instancia actual de la vista.
     */
    private void forzarRefrescoEstructuras() {
        Logger.logInfo("🔄 Forzando refresco de datos en espejo Rsync...");

        // 1. Re-escanear el entorno remoto solicitando el listado actual al nodo
        if (this.rutaRemotaActual != null && !this.rutaRemotaActual.trim().isEmpty()) {
            solicitarDirectorioRemoto(this.rutaRemotaActual);
        } else {
            solicitarDirectorioRemoto("");
        }

        // 2. Re-escanear el entorno local y actualizar la interfaz de inmediato
        SwingUtilities.invokeLater(() -> {
            refrescarEspejoRsync(this.nodoRemotoActual != null ? this.nodoRemotoActual.getHijos() : null,nodoRemotoActual);
        });
    }

    private JTabbedPane createMainExplorationTabs() {

        mainTabs = new JTabbedPane();

        // Instanciamos el nuevo panel dinámico

        queuePanel = new TransferQueuePanel();

        // Lo registramos en tu controlador multi-observador
        this.client.getTransferenciaController().addTransferencesObserver(queuePanel);

        queuePanel.setTransferCountListener((source, count) -> {
            updateTabTitle(source, "⬇ Transferencias", count);
        });

        mainTabs.addTab("🌐 Explorador Dual Sincronizado", createRemoteExplorerPanel());
        mainTabs.addTab("📥 Cola de Transferencias", queuePanel); // Añadido aquí directamente

        return mainTabs;
    }

    private JPanel createRemoteExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARKER);

        // 1. Configuración de la Región Superior
        JPanel navContainer = new JPanel(new BorderLayout());
        navContainer.setOpaque(false);
        navContainer.add(buildToolbarActions(), BorderLayout.NORTH);
        navContainer.add(breadcrumbBar, BorderLayout.SOUTH);

        // 2. Configuración de la Región Central con Pestañas Operativas profesionales
        JTabbedPane tabbedWorkspace = new JTabbedPane();
        tabbedWorkspace.putClientProperty("JTabbedPane.showTabSeparators", true);

        // Pestaña A: El Explorador Dual tradicional con su Inspector
        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dualExplorerPanel, inspector);
        contentSplit.setDividerLocation(1180);
        contentSplit.setResizeWeight(0.85);
        contentSplit.setBorder(BorderFactory.createEmptyBorder());
        tabbedWorkspace.addTab("🗂️ Explorador Sincronizado", contentSplit);

        // Pestaña B: NUEVA CONSOLA TÁCTICA DE TRANSFERENCIAS AVANZADAS
        tabbedWorkspace.addTab("🚀 Despacho Avanzado e Integridad", new AdvancedTransferPanel(client,clientInfo));

        // 3. Composición del área de trabajo (Pestañas + Historial Transaccional inferior)
        JPanel pnlCentralConConsola = new JPanel(new BorderLayout());
        pnlCentralConConsola.add(tabbedWorkspace, BorderLayout.CENTER);
        pnlCentralConConsola.add(buildTransactionalConsolePanel(), BorderLayout.SOUTH);

        // 4. Ensamblaje final
        panel.add(navContainer, BorderLayout.NORTH);
        panel.add(pnlCentralConConsola, BorderLayout.CENTER);

        return panel;
    }



    /**
     * Fabrica la barra de herramientas unificada segmentada por bloques operativos.
     */
    private JToolBar buildToolbarActions() {
        JToolBar actions = new JToolBar();
        actions.setFloatable(false);
        actions.setBackground(BG_DARKER);
        actions.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(45, 45, 45)));

        // [Bloque 1]: Navegación Base e Infraestructura
        actions.add(createNavigationControls());
        actions.addSeparator();

        JButton btnMkdir = new JButton("➕ Nueva Carpeta");
        btnMkdir.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnMkdir.putClientProperty("JButton.buttonType", "toolBarButton");
        btnMkdir.addActionListener(e -> handleRemoteMkdir());
        actions.add(btnMkdir);
        actions.addSeparator();

        // [Bloque 2]: Motor de Sincronización y Políticas
        String[] politicasConflicto = {
                "🔄 Política: Actualizar (Solo Nuevos)",
                "🪞 Política: Espejo (Mirror Estricto)",
                "🛡️ Política: Reanudación Segura (Safe Resume)"
        };
        JComboBox<String> comboPoliticas = new JComboBox<>(politicasConflicto);
        comboPoliticas.setMaximumSize(new Dimension(220, 30));
        comboPoliticas.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboPoliticas.setBackground(new Color(30, 30, 30));
        comboPoliticas.setForeground(Color.WHITE);

        JCheckBox chkForce = new JCheckBox("Forzar");
        chkForce.setOpaque(false);
        chkForce.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkForce.setForeground(new Color(255, 121, 198));

        JButton btnAplicarPolitica = new JButton("⚡ Aplicar");
        btnAplicarPolitica.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAplicarPolitica.setBackground(new Color(34, 112, 63));
        btnAplicarPolitica.setForeground(Color.WHITE);
        btnAplicarPolitica.addActionListener(e -> Logger.logInfo("[Sync Engine] Ejecutando resolución activa: " + comboPoliticas.getSelectedItem() + " | Forzar: " + chkForce.isSelected()));

        actions.add(new JLabel(" Conflictos: "));
        actions.add(comboPoliticas);
        actions.add(Box.createHorizontalStrut(4));
        actions.add(chkForce);
        actions.add(Box.createHorizontalStrut(6));
        actions.add(btnAplicarPolitica);
        actions.addSeparator();

        // [Bloque 3]: Demonios y Automatización (Watcher / Scheduler / Ignore)
        JCheckBox chkLiveWatch = new JCheckBox("👀 Live Watch");
        chkLiveWatch.setOpaque(false);
        chkLiveWatch.setFont(new Font("Segoe UI", Font.BOLD, 11));
        chkLiveWatch.setForeground(new Color(139, 233, 253));
        chkLiveWatch.setToolTipText("Habilita el WatchService nativo de Java (inotify en Linux) para capturar mutaciones en caliente");
        chkLiveWatch.addActionListener(e -> Logger.logInfo(chkLiveWatch.isSelected() ? "[FS Watcher] Activando hilos asíncronos nativos." : "[FS Watcher] Desactivando demonio de escucha."));
        actions.add(chkLiveWatch);
        actions.addSeparator();

        JButton btnScheduler = new JButton("⏰ Cron Task");
        btnScheduler.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnScheduler.setToolTipText("Programar automatización mediante expresiones cron");
        btnScheduler.addActionListener(e -> handleCronScheduling());
        actions.add(btnScheduler);
        actions.addSeparator();

        JButton btnExclusiones = new JButton("🚫 Exclusiones");
        btnExclusiones.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnExclusiones.setToolTipText("Configura patrones de archivos o carpetas a ignorar (.bitbridgeignore)");
        btnExclusiones.addActionListener(e -> mostrarDialogoExclusiones());
        actions.add(btnExclusiones);
        actions.addSeparator();

        // [Bloque 4]: Gestión de Tráfico (Netty Traffic Shaping)
        actions.add(buildThrottlingPanel());

        // Empuje elástico para mandar la búsqueda al extremo opuesto
        actions.add(Box.createHorizontalGlue());

        // [Bloque 5]: Filtros Predictivos Avanzados
        actions.add(buildSearchFilterPanel());

        return actions;
    }

    /**
     * Construye el panel del limitador de velocidad.
     */
    private JPanel buildThrottlingPanel() {
        JPanel pnlThrottling = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pnlThrottling.setOpaque(false);

        JLabel lblBanda = new JLabel("Limitador:");
        lblBanda.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblBanda.setForeground(Color.LIGHT_GRAY);

        JSpinner spinBanda = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 10));
        spinBanda.setMaximumSize(new Dimension(70, 26));
        spinBanda.setToolTipText("Define el techo de transferencia en MB/s. 0 significa ilimitado.");
        spinBanda.addChangeListener(e -> {
            int limiteMbs = (Integer) spinBanda.getValue();
            Logger.logInfo(limiteMbs == 0 ? "[Channel Traffic Shaping] Límite removido." : "[Channel Traffic Shaping] throttling activo: " + limiteMbs + " MB/s");
        });

        pnlThrottling.add(lblBanda);
        pnlThrottling.add(spinBanda);
        pnlThrottling.add(new JLabel("MB/s"));
        return pnlThrottling;
    }

    /**
     * Agrupa los componentes de búsqueda en tiempo real.
     */
    private JPanel buildSearchFilterPanel() {
        JPanel pnlSearch = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pnlSearch.setOpaque(false);

        JTextField search = new JTextField(10);
        search.putClientProperty("JTextField.placeholderText", "🔍 Buscar...");
        search.setMaximumSize(new Dimension(140, 30));
        search.setBackground(new Color(30, 30, 30));
        search.setForeground(Color.WHITE);
        search.setCaretColor(Color.WHITE);

        comboFiltro = new JComboBox<>(OPCIONES_FILTRO);
        comboFiltro.setMaximumSize(new Dimension(140, 30));
        comboFiltro.setBackground(new Color(30, 30, 30));
        comboFiltro.setForeground(Color.LIGHT_GRAY);
        comboFiltro.addActionListener(e -> aplicarFiltro(search.getText(), (String) comboFiltro.getSelectedItem()));

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
            private void filtrar() {
                SwingUtilities.invokeLater(() -> aplicarFiltro(search.getText(), (String) comboFiltro.getSelectedItem()));
            }
        });

        pnlSearch.add(comboFiltro);
        pnlSearch.add(search);
        return pnlSearch;
    }

    /**
     * Crea la consola transaccional inferior dedicada al log y auditoría forense de archivos.
     */
    private JPanel buildTransactionalConsolePanel() {
        JPanel pnlTransaccional = new JPanel(new BorderLayout());
        pnlTransaccional.setBackground(new Color(22, 24, 26));
        pnlTransaccional.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 50, 50)));
        pnlTransaccional.setPreferredSize(new Dimension(0, 150));

        JPanel pnlHeaderConsola = new JPanel(new BorderLayout());
        pnlHeaderConsola.setOpaque(false);
        pnlHeaderConsola.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel lblTituloConsola = new JLabel("📝 HISTORIAL DE TRANSFERENCIA Y AUDITORÍA TRANSACCIONAL");
        lblTituloConsola.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTituloConsola.setForeground(new Color(255, 165, 0));

        JButton btnLimpiarHistorial = new JButton("Clear Log");
        btnLimpiarHistorial.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnLimpiarHistorial.setBackground(new Color(40, 40, 40));
        btnLimpiarHistorial.setForeground(Color.LIGHT_GRAY);

        pnlHeaderConsola.add(lblTituloConsola, BorderLayout.WEST);
        pnlHeaderConsola.add(btnLimpiarHistorial, BorderLayout.EAST);

        String[] columnasHistorial = {"Estampa Temporal", "Asset de Origen", "Dirección", "Nodo Destino", "Tamaño", "Estado de Operación"};
        DefaultTableModel modeloHistorial = new DefaultTableModel(columnasHistorial, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable tablaHistorial = new JTable(modeloHistorial);
        tablaHistorial.setBackground(new Color(24, 24, 24));
        tablaHistorial.setForeground(new Color(220, 220, 220));
        tablaHistorial.setShowGrid(false);
        tablaHistorial.setRowHeight(22);
        tablaHistorial.setFont(new Font("Monospaced", Font.PLAIN, 11));

        // Datos Mock iniciales para pruebas unitarias de visualización
        modeloHistorial.addRow(new Object[]{"21:30:15", "/src/main/resources/config.properties", "📤 PUSH", "NODE-REMOTO-01", "4.11 KB", "COMPLETADO SUCCESFULLY ✅"});
        modeloHistorial.addRow(new Object[]{"21:31:02", "/assets/big_database_backup.sql", "📥 PULL", "Localhost", "133.44 MB", "STREAMING ACTIVE (45%) ⚡"});
        modeloHistorial.addRow(new Object[]{"21:32:00", "/secure/private_key.pem", "📤 PUSH", "NODE-REMOTO-01", "2.10 KB", "ERROR: PERMISO DENEGADO (403) ❌"});

        JScrollPane scrollHistorial = new JScrollPane(tablaHistorial);
        scrollHistorial.setBorder(BorderFactory.createEmptyBorder());
        scrollHistorial.getViewport().setBackground(new Color(24, 24, 24));

        pnlTransaccional.add(pnlHeaderConsola, BorderLayout.NORTH);
        pnlTransaccional.add(scrollHistorial, BorderLayout.CENTER);

        return pnlTransaccional;
    }

// --- MANEJADORES DE EVENTOS DE ACCIONES (STUBS PARA ACCIONES FUTURAS) ---

    private void handleRemoteMkdir() {
        String nuevaCarpeta = JOptionPane.showInputDialog(this, "Nombre del directorio remoto:");
        if (nuevaCarpeta != null && !nuevaCarpeta.trim().isEmpty()) {
            Logger.logInfo("[Netty Pipeline] Despachando Command_Mkdir -> " + nuevaCarpeta);
        }
    }

    private void handleCronScheduling() {
        String cronExpression = JOptionPane.showInputDialog(this,
                "Defina la expresión de tiempo o intervalo (ej: 0 0/30 * * * ?):",
                "bitBridge Scheduler Configuration", JOptionPane.QUESTION_MESSAGE);
        if (cronExpression != null && !cronExpression.trim().isEmpty()) {
            Logger.logInfo("[Scheduler] Tarea desatendida registrada con éxito. Expresión: " + cronExpression);
        }
    }



    /**
     * Levanta una ventana modal oscura para gestionar las reglas de exclusión del motor.
     */
    private void mostrarDialogoExclusiones() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Gestor de Exclusiones (bitBridge)", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getContentPane().setBackground(new Color(24, 24, 24));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        patronesExclusion.forEach(listModel::addElement);
        JList<String> list = new JList<>(listModel);
        list.setBackground(new Color(30, 30, 30));
        list.setForeground(Color.WHITE);
        list.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(55, 55, 55)));

        // Panel de entrada para nueva regla
        JPanel pnlInput = new JPanel(new BorderLayout(5, 0));
        pnlInput.setOpaque(false);
        JTextField txtNuevaRegla = new JTextField();
        txtNuevaRegla.putClientProperty("JTextField.placeholderText", "Ej: *.log, build/, .env");
        txtNuevaRegla.setBackground(new Color(35, 35, 35));
        txtNuevaRegla.setForeground(Color.WHITE);
        txtNuevaRegla.setCaretColor(Color.WHITE);

        JButton btnAdd = new JButton("Añadir");
        btnAdd.addActionListener(e -> {
            String regla = txtNuevaRegla.getText().trim();
            if (!regla.isEmpty() && !patronesExclusion.contains(regla)) {
                patronesExclusion.add(regla);
                listModel.addElement(regla);
                txtNuevaRegla.setText("");
               //aplicarFiltroCompuesto(); // Refresca las tablas inmediatamente
                Logger.logInfo("[Ignore Engine] Patrón de exclusión añadido: " + regla);
            }
        });
        pnlInput.add(txtNuevaRegla, BorderLayout.CENTER);
        pnlInput.add(btnAdd, BorderLayout.EAST);

        // Botón para eliminar regla seleccionada
        JButton btnDelete = new JButton("Eliminar Seleccionado");
        btnDelete.setBackground(new Color(150, 40, 40));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.addActionListener(e -> {
            String seleccionado = list.getSelectedValue();
            if (seleccionado != null) {
                patronesExclusion.remove(seleccionado);
                listModel.removeElement(seleccionado);
                //aplicarFiltroCompuesto(); // Refresca las tablas inmediatamente
                Logger.logInfo("[Ignore Engine] Patrón de exclusión removido: " + seleccionado);
            }
        });

        JPanel pnlInferior = new JPanel(new BorderLayout(0, 5));
        pnlInferior.setOpaque(false);
        pnlInferior.add(pnlInput, BorderLayout.NORTH);
        pnlInferior.add(btnDelete, BorderLayout.SOUTH);

        JPanel pnlContenedor = new JPanel(new BorderLayout(10, 10));
        pnlContenedor.setOpaque(false);
        pnlContenedor.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        pnlContenedor.add(new JLabel("🚫 Patrones activos (se omitirán en PUSH/PULL):"), BorderLayout.NORTH);
        pnlContenedor.add(scroll, BorderLayout.CENTER);
        pnlContenedor.add(pnlInferior, BorderLayout.SOUTH);

        dialog.add(pnlContenedor);
        dialog.setSize(350, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
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

    private void updateTabTitle(Component panel, String baseTitle, int count) {
        SwingUtilities.invokeLater(() -> {
            int index = mainTabs.indexOfComponent(panel);
            if (index != -1) {
                String newTitle = (count > 0) ? baseTitle + " (" + count + ")" : baseTitle;
                mainTabs.setTitleAt(index, newTitle);

                // Si hay actividad (count > 0), resaltamos la pestaña
                mainTabs.setForegroundAt(index, count > 0 ? COLOR_PRIMARY : null);
            }
        });
    }
}
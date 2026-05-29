package org.bitBridge.view.swing.components.explorer;

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.RemoteDirectoryListener;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.FilePullRequest;
import org.bitBridge.shared.core.comunication.NodoDirectorio;

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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class RemoteExplorer extends JFrame implements RemoteDirectoryListener {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARKER = new Color(20, 20, 20);
    private static final Color ACCENT_GREEN = new Color(50, 200, 50);

    NodoDirectorio raizDatos;
    private NodoDirectorio nodoActual;
    Path rutaRaiz;


    private Deque<NodoDirectorio> backStack = new ArrayDeque<>();
    private Deque<NodoDirectorio> forwardStack = new ArrayDeque<>();
    private Deque<NodoDirectorio>directorios= new ArrayDeque<>();

    private FileInspectorPanel inspector;
    private RemoteFileTablePanel fileTablePanel;
    private BreadcrumbBar breadcrumbBar;

    private JComboBox<String> comboFiltro;
    private JButton btnBack;
    private JButton btnForward;
    private JButton btnHome;
    private Client client;
    private String targetIp;
    // Opciones del filtro
    private final String[] OPCIONES_FILTRO = {
            "Todos los archivos",
            "Solo Carpetas",
            "Imágenes (jpg, png, gif)",
            "Multimedia (mp4, mkv, mp3)",
            "Documentos (pdf, docx, txt)",
            "Ejecutables (exe, sh, bat)"
    };


    public RemoteExplorer(Client client,String targeIp) throws IOException {
        //setupTheme();
        this.client=client;
        this.targetIp=targeIp;
        setupGUI();
    }


    public RemoteExplorer(String rutaInicial) throws IOException {

        rutaRaiz = Paths.get(rutaInicial);
        //raizDatos = new NodoDirectorio(rutaRaiz);
        raizDatos=NodoDirectorio.escanear(rutaRaiz);
        nodoActual=NodoDirectorio.escanear(rutaRaiz);

        setupGUI();

    }

    private void setupGUI(){
        FlatOneDarkIJTheme.setup();

        setTitle("BitBridge Pro - Advanced Remote Assets Explorer");
        setSize(1500, 950);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. TOOLBAR SUPERIOR
        add(createGlobalToolBar(), BorderLayout.NORTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                // Correcto: Referenciamos a la instancia de RemoteExplorer que implementa la interfaz
                client.removeDirectoryListener(RemoteExplorer.this);
                Logger.logInfo("Explorador remoto para " + targetIp + " cerrado y desuscrito.");
            }
        });
        inspector = new FileInspectorPanel();

        fileTablePanel = new RemoteFileTablePanel(
                nodo -> inspector.updateInfo(nodo),       // Clic simple -> Update inspector
                nodo -> {                                 // Doble Clic -> Navegar
                    if (nodo.esDirectorio()) navegarA(nodo);
                },
                this::ejecutarPull                // Clic derecho PULL -> Acción red
        );


        breadcrumbBar = new BreadcrumbBar(this::navegarA);


        // 2. PANEL CENTRAL (Split lateral)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(250);
        mainSplit.setLeftComponent(createSidePanel());
        mainSplit.setRightComponent(createMainExplorationTabs());

        add(mainSplit, BorderLayout.CENTER);

        // 3. BARRA DE ESTADO
        add(createStatusBar(), BorderLayout.SOUTH);
    }


    /**
     * Lógica centralizada para manejar la solicitud de descarga de un archivo.
     * @param nodo El nodo (archivo o carpeta) seleccionado en la tabla.
     */
    private void ejecutarPull(NodoDirectorio nodo) {
        // 1. Validación rápida


        // 2. Ejecución en segundo plano (para que la app no se trabe)
        new Thread(() -> {
            try {
                Logger.logInfo("Petición de PULL enviada: " + nodo.getNombre());

                // Aquí llamas a tu socket/cliente
                // Ejemplo de uso en RemoteExplorer
                FilePullRequest request = new FilePullRequest(
                        nodo.getRutaString(),
                        nodo.getNombre(),
                        targetIp,           // Nodo remoto
                        client.getHostName(),
                        nodo.esDirectorio()// Tu nick (para que el mensaje sepa volver)
                );

                client.enviarComunicacion(request);


                // 3. Feedback visual ligero en el hilo de la UI
                SwingUtilities.invokeLater(() -> {
                    // Si tienes una barra de estado, úsala aquí. Si no, este log basta:
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

        // Marcadores
        DefaultListModel<String> favModel = new DefaultListModel<>();
        favModel.addElement("⭐ Servidor Principal i7");
        favModel.addElement("⭐ Backup Gigabyte B760");
        favModel.addElement("📁 /home/user/logs");
        favModel.addElement("📁 /var/www/assets");

        JList<String> favList = new JList<>(favModel);
        favList.setBackground(BG_DARKER);
        favList.setFixedCellHeight(35);
        favList.setBorder(new TitledBorder(new LineBorder(Color.DARK_GRAY), "Favoritos"));

        // Árbol
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
        btnHome = new JButton("🏠"); // Botón Home

        // Estilo
        btnBack.setToolTipText("Atrás");
        btnForward.setToolTipText("Adelante");
        btnHome.setToolTipText("Ir al Directorio Raíz");

        // Listeners
        btnBack.addActionListener(e -> navegarAtras());
        btnForward.addActionListener(e -> navegarAdelante());

        btnHome.addActionListener(e -> {
            // Al ir a Home, tratamos la raíz como una navegación nueva
            // para que se guarde en el historial de 'atrás'
            navegarA(raizDatos);
        });

        navButtons.add(btnBack);
        navButtons.add(btnForward);
        navButtons.add(btnHome);

        return navButtons;
    }

    private JTabbedPane createMainExplorationTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("🌐 Explorador Remoto", createRemoteExplorerPanel());
        tabs.addTab("📥 Cola de Transferencias", createQueuePanel());
        return tabs;
    }

    private JPanel createRemoteExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARKER);

        // --- BARRA DE NAVEGACIÓN (FILE MANAGER STYLE) ---
        JPanel navContainer = new JPanel(new BorderLayout());
        navContainer.setOpaque(false);


        JToolBar actions = new JToolBar();
        actions.setFloatable(false);
        actions.setBackground(BG_DARKER);

        actions.add(createNavigationControls());
        actions.addSeparator();

        actions.add(createNavButton("➕ Nueva Carpeta", "Crear"));
        actions.add(createNavButton("📤 Subir (Push)", "Upload"));
        actions.addSeparator();
        actions.add(createNavButton("✂️ Cortar", null));
        actions.add(createNavButton("📋 Pegar", null));
        actions.add(createNavButton("🗑️ Eliminar", null));
        actions.add(Box.createHorizontalGlue());

        JTextField search = new JTextField(15);
        //search.putClientProperty("JTextField.placeholderText", "🔍 Filtrar archivos...");
        actions.add(new JLabel("Filtro: "));
        actions.add(search);

        //JTextField search = new JTextField(15);
        search.putClientProperty("JTextField.placeholderText", "🔍 Ej: .pdf, carpetas, o nombre...");

        // Dentro de createRemoteExplorerPanel...
        comboFiltro = new JComboBox<>(OPCIONES_FILTRO);
        comboFiltro.setMaximumSize(new Dimension(200, 30));

// Listener para el Combo
        comboFiltro.addActionListener(e -> {
            aplicarFiltro(search.getText(), (String) comboFiltro.getSelectedItem());
        });

        // Listener para el campo de búsqueda (actualizado)
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
            private void filtrar() {
                SwingUtilities.invokeLater(() ->
                        aplicarFiltro(search.getText(), (String) comboFiltro.getSelectedItem()));
            }
        });

// Agregar a la barra de acciones
        actions.add(new JLabel("  Tipo: "));
        actions.add(comboFiltro);
        actions.addSeparator();
        actions.add(new JLabel("  🔍 Buscar: "));
        actions.add(search);

        navContainer.add(actions, BorderLayout.NORTH);
        //navContainer.add(breadcrumbPanel, BorderLayout.SOUTH); // Usar nuestra variable de instancia

        navContainer.add(breadcrumbBar, BorderLayout.SOUTH);

        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                fileTablePanel,
                inspector);

        contentSplit.setDividerLocation(950);
        contentSplit.setResizeWeight(0.8);

        panel.add(navContainer, BorderLayout.NORTH);
        panel.add(contentSplit, BorderLayout.CENTER);

        return panel;
    }





    /**
     * Centraliza la lógica de navegación.
     * Actualiza el nodo actual, la tabla de archivos y los breadcrumbs.
     */
    private void navegarA(NodoDirectorio destino) {
        Logger.logInfo("llendo a "+destino.getNombre());

        if (destino == null || !destino.esDirectorio()) {
            return;
        }
        try {
            client.requestFileList(targetIp,destino.getRutaString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 1. Si tenemos un nodo previo, lo guardamos en el stack de retroceso
        if (this.nodoActual != null) {
            //backStack.push(this.nodoActual.getRutaCompleta().toFile());
            backStack.push(nodoActual);
            // Limpiamos el forward stack al navegar a una nueva ruta
            forwardStack.clear();
        }

    }







    private void navegarAtras() {
        if (!backStack.isEmpty()) {
            Logger.logInfo("Yendo hacia atrás...");

            // 1. Guardamos el que estamos viendo ahora en el stack de "adelante"
            forwardStack.push(nodoActual);

            // 2. Sacamos el destino del stack de "atrás"
            NodoDirectorio destino = backStack.pop();

            // 3. Saltamos físicamente al nodo
            // Importante: ejecutarSalto ya debe llamar a actualizarBreadcrumbs()
            ejecutarSalto(destino);

        } else {
            Logger.logInfo("El stack de atrás está vacío");
        }
    }

    private void navegarAdelante() {
        if (!forwardStack.isEmpty()) {
            // Guardamos el actual en el stack de "atrás"
            //backStack.push(nodoActual.getRutaCompleta().toFile());
            backStack.push(nodoActual);
            // Solo una línea de código:
            breadcrumbBar.updatePath(raizDatos, nodoActual);
            //navegarA(forwardStack.pop());
            ejecutarSalto(forwardStack.pop());
            //File rutaSiguiente = forwardStack.pop();
            //ejecutarSalto(rutaSiguiente);
        }
    }

    /**
     * Salto técnico que evita duplicar el historial al navegar por los botones
     */
    private void ejecutarSalto(NodoDirectorio nodo) {
        this.nodoActual = nodo;
        //populateAdvancedMockData(nodoActual.getHijosRed());
        fileTablePanel.updateData(nodoActual.getHijosRed(),nodo.getNombre());
        // Solo una línea de código:
        breadcrumbBar.updatePath(raizDatos, nodoActual);
        actualizarEstadoBotones();
    }

    private void actualizarEstadoBotones() {
        btnBack.setEnabled(!backStack.isEmpty());
        btnForward.setEnabled(!forwardStack.isEmpty());
    }

    private void aplicarFiltro(String textoBusqueda, String categoria) {
        TableRowSorter<DefaultTableModel> tableSorter = fileTablePanel.getSorter();

        if (textoBusqueda.trim().isEmpty() && categoria.equals(OPCIONES_FILTRO[0])) {
            tableSorter.setRowFilter(null);
            return;
        }

        tableSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String nombre = entry.getStringValue(0).toLowerCase();
                String tipo = entry.getStringValue(2).toLowerCase();
                String busqueda = textoBusqueda.toLowerCase();

                // 1. Verificar categoría del Combo Box
                boolean cumpleCategoria = true;
                switch (categoria) {
                    case "Solo Carpetas": cumpleCategoria = nombre.startsWith("📁"); break;
                    case "Imágenes (jpg, png, gif)": cumpleCategoria = "jpg png gif jpeg".contains(tipo); break;
                    case "Multimedia (mp4, mkv, mp3)": cumpleCategoria = "mp4 mkv mp3 avi".contains(tipo); break;
                    case "Documentos (pdf, docx, txt)": cumpleCategoria = "pdf docx txt odt".contains(tipo); break;
                    case "Ejecutables (exe, sh, bat)": cumpleCategoria = "exe sh bat jar".contains(tipo); break;
                }

                if (busqueda.startsWith(".")) {
                    return tipo.contains(busqueda.replace(".", ""));
                }

                // Lógica 2: Filtro por carpetas explícito
                if (busqueda.equals("carpetas") || busqueda.equals("dir")) {
                    return nombre.startsWith("📁");
                }

                // 2. Verificar texto de búsqueda
                boolean cumpleTexto = nombre.contains(busqueda) || tipo.contains(busqueda);

                // Lógica 3: Búsqueda general por nombre
                //return nombre.contains(busqueda);
                return cumpleCategoria && cumpleTexto;

            }
        });
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new RemoteExplorer("/home/cris/Descargas").setVisible(true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onDirectoryDataReceived(NodoDirectorio nodo) {
        Logger.logInfo("Dato entrantes "+nodo.getNombre());
        if (this.raizDatos == null) {
            this.raizDatos = nodo;
            rutaRaiz = Paths.get(nodo.getRutaString());
        }

        if (!directorios.isEmpty()){
            backStack.push(directorios.getLast());
        }

        directorios.add(nodo);
        this.nodoActual = nodo;

        SwingUtilities.invokeLater(() -> {
            // 1. Enviamos los datos al nuevo panel de la tabla
            fileTablePanel.updateData(nodoActual.getHijosRed(),nodo.getNombre());

            // 2. Actualizamos el resto de la UI
            // Solo una línea de código:
            breadcrumbBar.updatePath(raizDatos, nodoActual);
            actualizarEstadoBotones();
        });
    }
}
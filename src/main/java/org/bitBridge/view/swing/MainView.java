package org.bitBridge.view.swing;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import org.bitBridge.Client.core.Client;
import org.bitBridge.server.core.Server;
import org.bitBridge.view.core.ConnectionState;
import org.bitBridge.view.core.IMainView;
import org.bitBridge.view.core.MainController;
import org.bitBridge.view.core.ServerState;
import org.bitBridge.view.swing.components.hosts.ChatPanel;
import org.bitBridge.view.swing.components.hosts.NetworkClientsPanel;
import org.bitBridge.view.swing.components.transfers.TransferPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class MainView extends JFrame implements IMainView {
    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_DARKER = new Color(25, 25, 25);
    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);
    private final Color COLOR_SUCCESS = new Color(46, 204, 113);
    private final Color COLOR_DANGER = new Color(231, 76, 60);
    private final Color COLOR_PRIMARY = new Color(52, 152, 219);
    private final Color COLOR_WARNING = new Color(241, 196, 15);


    private DefaultTableModel downloadModel;
    JTabbedPane mainTabs;
    //NetworkClientsPanel clientesPanel = new NetworkClientsPanel();
    private HeaderPanel headerPanel;
    private StatusBarPanel statusBar;

    private MainController controller;
    private Client client;
    private Server server;
    private TransferPanel transferPanel;
    private NetworkClientsPanel clientsPanel;
    ChatPanel chatPanel;

    public MainView() {

        setupTheme();

        server=Server.getInstance();
        client=new Client();
        controller=new MainController(this,client,server);

        transferPanel = new TransferPanel();
        clientsPanel=new NetworkClientsPanel(client);
        this.client.getTransferenciaController().setTransferencesObserver(transferPanel);

        //transferPanel.setTransferCountListener(this);
        chatPanel = new ChatPanel(client);

        setTitle("BitBridge - [P2P Network Node]");
        setSize(1350, 850);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. BARRA SUPERIOR CON CONTROL DE SERVIDOR
        headerPanel = new HeaderPanel(controller);
        add(headerPanel, BorderLayout.NORTH);

        mainTabs = new JTabbedPane();
        mainTabs.putClientProperty("JTabbedPane.showTabSeparators", true);

        // Pestañas principales
        mainTabs.addTab("🌐 Clientes en Red", clientsPanel); // NUEVA PESTAÑA

        clientsPanel.setHostCountListener((source, count) -> {
            updateTabTitle(source, "🌐 Clientes", count);
        });


        mainTabs.addTab("🔍 Buscar Archivos", createSearchPanel());

        //mainTabs.addTab("📥 Descargas", transferPanel);

        //mainTabs.addTab("⬇ Transferencias", (Component) this.client.getTransferenciaController().setTransferencesObserver(new TransferPanel()));
        mainTabs.addTab("⬇ Transferencias", transferPanel);
        transferPanel.setTransferCountListener((source, count) -> {
            updateTabTitle(source, "⬇ Transferencias", count);
        });

        mainTabs.addTab("📂 Explorar Compartidos", createSharedExplorerPanel());
        mainTabs.addTab("💬 Chat Privado", chatPanel);

        chatPanel.setCountListener((source, count) -> {
            // Usamos el método genérico que creamos antes
            updateTabTitle(source, "💬 Chat Privado", count);
        });

        // 4. Resetear contador al entrar a la pestaña
        mainTabs.addChangeListener(e -> {
            if (mainTabs.getSelectedComponent() == chatPanel) {
                chatPanel.resetUnreadCount();
            }
        });

        mainTabs.addTab("👥 Amigos", createFriendsManagerPanel());
        mainTabs.addTab("⚙ Intereses", createPlaceholderPanel("✨", "Búsquedas automáticas"));
// Dentro del constructor BitBridgeNicotineUI()
        mainTabs.addTab("📦 Repositorio Global", createGlobalRepositoryPanel());
        mainTabs.addTab("📈 Rendimiento Servidor", createServerPerformancePanel());
        // 3. SECCIÓN INFERIOR (LOGS Y STATUS)
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(createLogConsole(), BorderLayout.CENTER);
        statusBar = new StatusBarPanel();
        statusBar.updateNetworkStats(12.5, 1.2, 2233);
        add(statusBar, BorderLayout.SOUTH);
        //southPanel.add(createStatusBar(), BorderLayout.SOUTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainTabs, southPanel);
        mainSplit.setDividerLocation(580);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setBorder(null);




        add(mainSplit, BorderLayout.CENTER);
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

    public void initGui(){

    }


    @Override
    public void updateServerUI(ServerState state, String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            headerPanel.updateServerStatus(state, errorMessage);
            if (errorMessage != null) {
                showAlert("Error del Servidor", errorMessage);
            }
        });
    }

    @Override
    public void showAlert(String title, String content) {

    }

    @Override
    public void updateTheme(String themeName) {

    }

    @Override
    public void updateConnectionUI(ConnectionState state, String detail) {
        SwingUtilities.invokeLater(() -> {
            headerPanel.updateConnectionStatus(state, detail);
        });
    }


    private JPanel createGlobalRepositoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        return panel;
    }

    private JPanel createServerPerformancePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        return panel;
    }

    private JPanel createFriendsManagerPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        return panel;
    }


    // (Otros métodos como createSearchPanel, createSharedExplorerPanel, etc. se mantienen igual de tu estructura base)
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        return panel;
    }

    private JPanel createSharedExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        return panel;
    }



    private JScrollPane createLogConsole() {
        JTextArea log = new JTextArea();
        log.setBackground(BG_DARKER);
        log.setForeground(new Color(180, 180, 180));
        log.setFont(new Font("Monospaced", Font.PLAIN, 11));
        log.setEditable(false);
        log.setText(" 13/01/26 16:18:54 BitBridge Node Iniciado...\n 13/01/26 16:20:10 Escaneo de carpetas locales finalizado.\n 13/01/26 16:21:05 Conectado a 'Fedora-PC' vía P2P.");
        JScrollPane scroll = new JScrollPane(log);
        scroll.setPreferredSize(new Dimension(0, 100));
        return scroll;
    }

    /*private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(5, 10, 5, 10));
        JLabel traffic = new JLabel("⬇ 13.4 MB/s | ⬆ 2.1 MB/s | Paquetes: 104,201");
        traffic.setForeground(NICOTINE_ORANGE);
        status.add(new JLabel("Motor P2P: Activo | RAM: 156MB"), BorderLayout.WEST);
        status.add(traffic, BorderLayout.EAST);
        return status;
    }*/

    private JPanel createPlaceholderPanel(String icon, String text) {
        JPanel p = new JPanel(new GridBagLayout());
        JLabel l = new JLabel("<html><center><font size='60'>" + icon + "</font><br><br>" + text + "</center></html>");
        l.setForeground(Color.GRAY);
        p.add(l);
        return p;
    }

    private void setupTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("TabbedPane.selectedBackground", NICOTINE_ORANGE.darker());
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            //UIManager.put("ProgressBar.arc", 0); // Estilo Nicotine es cuadrado
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainView().setVisible(true));
    }
}
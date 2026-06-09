package org.bitBridge.view.swing.components.hosts;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.shared.Logger;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;

/**
 * Panel de Envío de Archivos y Carpetas V2 - bitBridge Pro.
 * Interfaz amigable con opciones extendidas de control para el usuario final.
 */
public class AdvancedTransferPanel extends JPanel {

    // --- Componentes de Red (Destinatarios) ---
    private JComboBox<String> comboModosRed;
    private JTextField txtNodeDestino;
    private JComboBox<String> comboConcurrenciaRed;
    private JComboBox<String> comboToleranciaFallos;
    private JSpinner spinIntervaloNodes;
    private CardLayout cardLayoutRed;
    private JPanel pnlCardsRed;

    // --- Componentes de Naturaleza (Archivo / Carpeta) ---
    private JToggleButton btnModoArchivo;
    private JToggleButton btnModoDirectorio;
    private CardLayout cardLayoutNat;
    private JPanel pnlCardsNat;

    // --- Parámetros Extendidos: Archivo ---
    private JComboBox<String> comboInyeccionIO;
    private JSpinner spinCanalesNetty;
    private JComboBox<String> comboLimiteVelocidad; // NUEVO: Control de ancho de banda
    private JComboBox<String> comboConflictoArchivo; // NUEVO: Qué hacer si el archivo ya existe
    private JCheckBox chkCompressFile;
    private JCheckBox chkHashFile;
    private JCheckBox chkResumeFile;
    private JCheckBox chkCifrarTransito; // NUEVO: Encriptación en tránsito simplificada

    // --- Parámetros Extendidos: Carpeta ---
    private JComboBox<String> comboSyncDir;
    private JTextField txtRegexExcluir;
    private JComboBox<String> comboFiltroTamaño; // NUEVO: Omitir archivos muy pesados
    private JCheckBox chkEmptyDirs;
    private JCheckBox chkPosixDir;
    private JCheckBox chkMoverDir;
    private JCheckBox chkPreservarFechas; // NUEVO: Mantener fechas de modificación nativas

    // --- Monitor de Cola de Carga ---
    private JTable tblPreVuelo;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;
    private JButton btnAgregarAsset;
    private JButton btnLimpiar;
    private JButton btnEnviarArchivo;
    private JButton btnEnviarDirectorio;

    private Client client;
    private ClientInfo clientInfo;

    public AdvancedTransferPanel(Client client, ClientInfo clientInfo) {
        this.client = client;
        this.clientInfo = clientInfo;

        initComponents();
        buildLayout();
        setupReactiveListeners();
    }

    public AdvancedTransferPanel() {
        initComponents();
        buildLayout();
        setupReactiveListeners();
    }

    private void initComponents() {
        // Red
        String[] modosRed = {"🎯 ENVIAR A UN USUARIO ESPECÍFICO", "📢 ENVIAR A TODOS LOS CONECTADOS"};
        comboModosRed = new JComboBox<>(modosRed);
        txtNodeDestino = new JTextField(clientInfo != null ? clientInfo.getNick() : "Usuario Remoto");
        comboConcurrenciaRed = new JComboBox<>(new String[]{"Enviar uno por uno (Ordenado)", "Enviar todos a la vez"});
        comboToleranciaFallos = new JComboBox<>(new String[]{"Continuar si alguien se desconecta", "Detener todo si hay un error"});
        spinIntervaloNodes = new JSpinner(new SpinnerNumberModel(0, 0, 5000, 100));
        cardLayoutRed = new CardLayout();
        pnlCardsRed = new JPanel(cardLayoutRed);

        // Selector de tipo de envío
        btnModoArchivo = new JToggleButton("📄 ENVIAR ARCHIVOS SOLTOS", true);
        btnModoDirectorio = new JToggleButton("📂 ENVIAR CARPETA COMPLETA");
        ButtonGroup groupNaturaleza = new ButtonGroup();
        groupNaturaleza.add(btnModoArchivo);
        groupNaturaleza.add(btnModoDirectorio);
        cardLayoutNat = new CardLayout();
        pnlCardsNat = new JPanel(cardLayoutNat);

        // --- Inicialización de Opciones Extendidas de Archivo ---
        comboInyeccionIO = new JComboBox<>(new String[]{"Envío estándar estable", "Envío optimizado de alta velocidad"});
        spinCanalesNetty = new JSpinner(new SpinnerNumberModel(2, 1, 16, 1));

        comboLimiteVelocidad = new JComboBox<>(new String[]{"Ilimitado (Máxima velocidad)", "Modo Económico (1 MB/s)", "Modo Fondo (512 KB/s)"});
        comboConflictoArchivo = new JComboBox<>(new String[]{"Reemplazar si ya existe", "Omitir y no enviar", "Renombrar automáticamente"});

        chkCompressFile = new JCheckBox("🗜️ Reducir tamaño de datos para ahorrar internet");
        chkHashFile = new JCheckBox("🧮 Verificar minuciosamente que llegó sin daños");
        chkResumeFile = new JCheckBox("⏳ Si se corta la conexión, reanudar donde quedó");
        chkCifrarTransito = new JCheckBox("🔒 Proteger datos con cifrado de alta seguridad");

        // --- Inicialización de Opciones Extendidas de Carpeta ---
        comboSyncDir = new JComboBox<>(new String[]{"Enviar solo archivos nuevos o modificados", "Hacer copia idéntica (Reemplazar todo)", "Combinar carpetas de forma estándar"});
        txtRegexExcluir = new JTextField(".*\\.tmp$|.*\\.git.*|.*node_modules.*");

        comboFiltroTamaño = new JComboBox<>(new String[]{"Enviar todo sin importar el peso", "Omitir archivos mayores a 100 MB", "Omitir archivos mayores a 1 GB"});

        chkEmptyDirs = new JCheckBox("📂 Conservar carpetas que estén vacías");
        chkPosixDir = new JCheckBox("🔰 Mantener los permisos de seguridad originales");
        chkMoverDir = new JCheckBox("🧹 Modo Mover (Borrar del equipo al terminar)");
        chkPreservarFechas = new JCheckBox("📅 Mantener la fecha de modificación original");

        // Tabla y preparación
        String[] columnas = {"Tipo", "Ubicación en el Equipo", "Tamaño Estimado", "Modo de Envío"};
        tableModel = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tblPreVuelo = new JTable(tableModel);
        lblSummary = new JLabel("<html><body>🎯 <b>Preparado:</b> Enviando a <b style='color:#f1c40f;'>UN USUARIO</b> listo para despachar <b style='color:#5294e2;'>Archivos</b>.</body></html>");
        btnAgregarAsset = new JButton("➕ Seleccionar Archivo(s)");
        btnLimpiar = new JButton("🧹 Vaciar Lista");

        // Botones de acción principal
        btnEnviarArchivo = new JButton("📤 ENVIAR ARCHIVOS AHORA");
        btnEnviarArchivo.setBackground(new Color(52, 152, 219));
        btnEnviarArchivo.setForeground(Color.WHITE);

        btnEnviarDirectorio = new JButton("📤 ENVIAR CARPETA AHORA");
        btnEnviarDirectorio.setBackground(new Color(230, 126, 34));
        btnEnviarDirectorio.setForeground(Color.WHITE);
    }

    private void buildLayout() {
        this.setLayout(new GridBagLayout());
        this.setBackground(new Color(24, 24, 27));
        this.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(6, 6, 6, 6);

        JPanel pnlIzquierdo = new JPanel(new GridBagLayout());
        pnlIzquierdo.setOpaque(false);
        GridBagConstraints gi = new GridBagConstraints();
        gi.fill = GridBagConstraints.BOTH;
        gi.weightx = 1.0;
        gi.gridx = 0;

        // Red Master
        JPanel pnlRedMaster = new JPanel(new GridBagLayout());
        pnlRedMaster.setBackground(new Color(30, 30, 35));
        pnlRedMaster.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 75)),
                "🌐 CONFIGURACIÓN DEL DESTINATARIO Y RED", 0, 0,
                new Font("Inter", Font.BOLD, 11), Color.YELLOW));

        GridBagConstraints cr = new GridBagConstraints();
        cr.fill = GridBagConstraints.HORIZONTAL; cr.insets = new Insets(5, 8, 5, 8); cr.weightx = 1.0;
        int rRed = 0;

        cr.gridx = 0; cr.gridy = rRed; pnlRedMaster.add(new JLabel("¿A quién va dirigido?:"), cr);
        cr.gridx = 1; pnlRedMaster.add(comboModosRed, cr); rRed++;

        pnlCardsRed.setOpaque(false);
        JPanel cardTargetHost = new JPanel(new GridLayout(2, 2, 6, 6)); cardTargetHost.setOpaque(false);
        cardTargetHost.add(new JLabel("Nombre del Destinatario:")); cardTargetHost.add(txtNodeDestino);
        cardTargetHost.add(new JLabel("Fluidez de Envío:")); cardTargetHost.add(comboConcurrenciaRed);

        JPanel cardBroadcast = new JPanel(new GridLayout(2, 2, 6, 6)); cardBroadcast.setOpaque(false);
        cardBroadcast.add(new JLabel("Manejo de Errores:")); cardBroadcast.add(comboToleranciaFallos);
        cardBroadcast.add(new JLabel("Pausa entre Envíos (ms):")); cardBroadcast.add(spinIntervaloNodes);

        pnlCardsRed.add(cardTargetHost, "RED_TARGET");
        pnlCardsRed.add(cardBroadcast, "RED_BROADCAST");

        cr.gridx = 0; cr.gridy = rRed; cr.gridwidth = 2; cr.weighty = 1.0;
        pnlRedMaster.add(pnlCardsRed, cr);

        gi.gridy = 0; gi.weighty = 0.30; pnlIzquierdo.add(pnlRedMaster, gi);

        // Naturaleza Master
        JPanel pnlNaturalezaMaster = new JPanel(new BorderLayout(0, 8));
        pnlNaturalezaMaster.setOpaque(false);

        JPanel pnlSelectorNaturaleza = new JPanel(new GridLayout(1, 2, 6, 0));
        pnlSelectorNaturaleza.setOpaque(false);
        pnlSelectorNaturaleza.add(btnModoArchivo); pnlSelectorNaturaleza.add(btnModoDirectorio);
        pnlNaturalezaMaster.add(pnlSelectorNaturaleza, BorderLayout.NORTH);

        pnlCardsNat.setOpaque(false);

        // Card Opciones de Archivos (Configuración Extendida)
        JPanel cardArchivo = new JPanel(new GridBagLayout());
        cardArchivo.setBackground(new Color(30, 30, 35));
        cardArchivo.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(52, 152, 219)), "OPCIONES ADICIONALES PARA ARCHIVOS", 0, 0,
                new Font("Inter", Font.BOLD, 11), new Color(52, 152, 219)));
        GridBagConstraints ca = new GridBagConstraints();
        ca.fill = GridBagConstraints.HORIZONTAL; ca.insets = new Insets(5, 8, 5, 8); ca.weightx = 1.0; int rA = 0;

        ca.gridx = 0; ca.gridy = rA; cardArchivo.add(new JLabel("Velocidad de Envío:"), ca);
        ca.gridx = 1; cardArchivo.add(comboLimiteVelocidad, ca); rA++;
        ca.gridx = 0; ca.gridy = rA; cardArchivo.add(new JLabel("Si el archivo ya existe:"), ca);
        ca.gridx = 1; cardArchivo.add(comboConflictoArchivo, ca); rA++;
        ca.gridx = 0; ca.gridy = rA; cardArchivo.add(new JLabel("Canales de Red:"), ca);
        ca.gridx = 1; cardArchivo.add(spinCanalesNetty, ca); rA++;

        ca.gridx = 0; ca.gridy = rA; ca.gridwidth = 2;
        JPanel pnlChecksFile = new JPanel(new GridLayout(4, 1)); pnlChecksFile.setOpaque(false);
        pnlChecksFile.add(chkCompressFile); pnlChecksFile.add(chkHashFile); pnlChecksFile.add(chkResumeFile); pnlChecksFile.add(chkCifrarTransito);
        cardArchivo.add(pnlChecksFile, ca); rA++;

        ca.gridx = 0; ca.gridy = rA; ca.gridwidth = 2; ca.insets = new Insets(12, 8, 4, 8);
        btnEnviarArchivo.setFont(new Font("Inter", Font.BOLD, 12));
        btnEnviarArchivo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cardArchivo.add(btnEnviarArchivo, ca);

        // Card Opciones de Carpetas (Configuración Extendida)
        JPanel cardDirectorio = new JPanel(new GridBagLayout());
        cardDirectorio.setBackground(new Color(30, 30, 35));
        cardDirectorio.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(230, 126, 34)), "OPCIONES ADICIONALES PARA CARPETAS", 0, 0,
                new Font("Inter", Font.BOLD, 11), new Color(230, 126, 34)));
        GridBagConstraints cd = new GridBagConstraints();
        cd.fill = GridBagConstraints.HORIZONTAL; cd.insets = new Insets(5, 8, 5, 8); cd.weightx = 1.0; int rD = 0;

        cd.gridx = 0; cd.gridy = rD; cardDirectorio.add(new JLabel("Modo de Sincronización:"), cd);
        cd.gridx = 1; cardDirectorio.add(comboSyncDir, cd); rD++;
        cd.gridx = 0; cd.gridy = rD; cardDirectorio.add(new JLabel("Tamaño Máximo Permitido:"), cd);
        cd.gridx = 1; cardDirectorio.add(comboFiltroTamaño, cd); rD++;
        cd.gridx = 0; cd.gridy = rD; cardDirectorio.add(new JLabel("Nombres a Ignorar (Filtro):"), cd);
        cd.gridx = 1; cardDirectorio.add(txtRegexExcluir, cd); rD++;

        cd.gridx = 0; cd.gridy = rD; cd.gridwidth = 2;
        JPanel pnlChecksDir = new JPanel(new GridLayout(4, 1)); pnlChecksDir.setOpaque(false);
        pnlChecksDir.add(chkEmptyDirs); pnlChecksDir.add(chkPosixDir); pnlChecksDir.add(chkMoverDir); pnlChecksDir.add(chkPreservarFechas);
        cardDirectorio.add(pnlChecksDir, cd); rD++;

        cd.gridx = 0; cd.gridy = rD; cd.gridwidth = 2; cd.insets = new Insets(12, 8, 4, 8);
        btnEnviarDirectorio.setFont(new Font("Inter", Font.BOLD, 12));
        btnEnviarDirectorio.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cardDirectorio.add(btnEnviarDirectorio, cd);

        pnlCardsNat.add(cardArchivo, "NAT_ARCHIVO");
        pnlCardsNat.add(cardDirectorio, "NAT_DIRECTORIO");
        pnlNaturalezaMaster.add(pnlCardsNat, BorderLayout.CENTER);

        gi.gridy = 1; gi.weighty = 0.70; pnlIzquierdo.add(pnlNaturalezaMaster, gi);

        // Panel Derecho (Lista de Pre-vuelo)
        JPanel pnlColaPreVuelo = new JPanel(new BorderLayout(0, 8));
        pnlColaPreVuelo.setBackground(new Color(30, 30, 35));
        pnlColaPreVuelo.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 65)),
                "📋 LISTA DE ELEMENTOS LISTOS PARA ENVIAR", 0, 0,
                new Font("Inter", Font.BOLD, 12), new Color(162, 155, 254)));

        JPanel pnlSummaryBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        pnlSummaryBanner.setBackground(new Color(22, 22, 26));
        pnlSummaryBanner.setBorder(BorderFactory.createLineBorder(new Color(45, 45, 50)));
        lblSummary.setFont(new Font("Inter", Font.PLAIN, 11));
        pnlSummaryBanner.add(lblSummary);
        pnlColaPreVuelo.add(pnlSummaryBanner, BorderLayout.NORTH);

        tblPreVuelo.setBackground(new Color(24, 24, 27));
        tblPreVuelo.setForeground(Color.WHITE);
        tblPreVuelo.setRowHeight(24);

        tblPreVuelo.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasF, int row, int col) {
                JLabel cell = (JLabel) super.getTableCellRendererComponent(table, val, isSel, hasF, row, col);
                cell.setHorizontalAlignment(SwingConstants.CENTER);
                cell.setFont(new Font("Inter", Font.BOLD, 11));
                if ("CARPETA".equals(val) || "DIRECTORIO".equals(val)) {
                    cell.setText("CARPETA");
                    cell.setBackground(new Color(230, 126, 34, 40)); cell.setForeground(new Color(230, 126, 34));
                } else {
                    cell.setText("ARCHIVO");
                    cell.setBackground(new Color(52, 152, 219, 40)); cell.setForeground(new Color(52, 152, 219));
                }
                if (isSel) { cell.setBackground(table.getSelectionBackground()); cell.setForeground(table.getSelectionForeground()); }
                return cell;
            }
        });

        JScrollPane scrollTabla = new JScrollPane(tblPreVuelo);
        scrollTabla.setBorder(BorderFactory.createLineBorder(new Color(45, 45, 50)));
        pnlColaPreVuelo.add(scrollTabla, BorderLayout.CENTER);

        JPanel pnlAccionesCola = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlAccionesCola.setOpaque(false);
        pnlAccionesCola.add(btnAgregarAsset); pnlAccionesCola.add(btnLimpiar);
        pnlColaPreVuelo.add(pnlAccionesCola, BorderLayout.SOUTH);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.45; gbc.weighty = 1.0; gbc.gridwidth = 1;
        this.add(pnlIzquierdo, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.55; gbc.weighty = 1.0; gbc.gridwidth = 1;
        this.add(pnlColaPreVuelo, gbc);
    }

    private void setupReactiveListeners() {
        comboModosRed.addActionListener(e -> {
            boolean esTarget = comboModosRed.getSelectedIndex() == 0;
            cardLayoutRed.show(pnlCardsRed, esTarget ? "RED_TARGET" : "RED_BROADCAST");
            String redStr = esTarget ? "DIRECTO" : "A TODOS";
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                tableModel.setValueAt(redStr, i, 3);
            }
            updateSummaryBanner();
        });

        btnModoArchivo.addActionListener(e -> {
            cardLayoutNat.show(pnlCardsNat, "NAT_ARCHIVO");
            btnAgregarAsset.setText("➕ Seleccionar Archivo(s)");
            updateSummaryBanner();
        });

        btnModoDirectorio.addActionListener(e -> {
            cardLayoutNat.show(pnlCardsNat, "NAT_DIRECTORIO");
            btnAgregarAsset.setText("➕ Seleccionar Carpeta");
            updateSummaryBanner();
        });

        btnAgregarAsset.addActionListener(e -> ejecutarExploradorLocal());
        btnLimpiar.addActionListener(e -> tableModel.setRowCount(0));

        // --- ENVÍO DE ARCHIVOS ---
        btnEnviarArchivo.addActionListener(e -> {
            if (tableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "Por favor, agrega al menos un archivo a la lista primero.", "Lista Vacía", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Mapeo lógico de las nuevas opciones para tu consola/backend
            String estrategiaVelocidad = (String) comboLimiteVelocidad.getSelectedItem();
            String gestionConflicto = (String) comboConflictoArchivo.getSelectedItem();
            boolean cifradoActivo = chkCifrarTransito.isSelected();

            Logger.logInfo("[ bitBridge Engine ] Despachando con políticas: " + estrategiaVelocidad + " | " + gestionConflicto + " | Cifrado: " + cifradoActivo);

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String naturaleza = (String) tableModel.getValueAt(i, 0);
                String rutaLocal = (String) tableModel.getValueAt(i, 1);

                if ("ARCHIVO".equals(naturaleza)) {
                    File archivoAEnviar = new File(rutaLocal);
                    Logger.logInfo("-> Enviando Archivo: " + archivoAEnviar.getAbsolutePath());

                    try {
                        if (client != null) {
                            client.sendFileToHost(clientInfo, archivoAEnviar);
                        }
                    } catch (Exception ex) {
                        Logger.logInfo("Error crítico de transferencia: " + ex.getMessage());
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        // --- ENVÍO DE CARPETAS ---
        btnEnviarDirectorio.addActionListener(e -> {
            if (tableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "Por favor, agrega una carpeta a la lista primero.", "Lista Vacía", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String filtroTamaño = (String) comboFiltroTamaño.getSelectedItem();
            boolean mantenerFechas = chkPreservarFechas.isSelected();

            Logger.logInfo("[ bitBridge Engine ] Despachando Carpeta con políticas: " + filtroTamaño + " | Conservar fechas: " + mantenerFechas);

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String naturaleza = (String) tableModel.getValueAt(i, 0);
                String rutaLocal = (String) tableModel.getValueAt(i, 1);

                if ("DIRECTORIO".equals(naturaleza) || "CARPETA".equals(naturaleza)) {
                    File carpetaAEnviar = new File(rutaLocal);
                    Logger.logInfo("-> Enviado Carpeta: " + carpetaAEnviar.getAbsolutePath());

                    if (client != null) {
                        client.sendDirectoryToHost(clientInfo, carpetaAEnviar);
                    }
                }
            }
        });
    }

    private void ejecutarExploradorLocal() {
        JFileChooser chooser = new JFileChooser();
        boolean esModoArchivo = btnModoArchivo.isSelected();

        if (esModoArchivo) {
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(true);
        } else {
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setMultiSelectionEnabled(false);
        }

        int resultado = chooser.showOpenDialog(this);
        if (resultado == JFileChooser.APPROVE_OPTION) {
            if (esModoArchivo) {
                File[] archivosSeleccionados = chooser.getSelectedFiles();
                for (File file : archivosSeleccionados) {
                    inyectarAssetEnTabla(file, "ARCHIVO");
                }
            } else {
                File directorioSeleccionado = chooser.getSelectedFile();
                inyectarAssetEnTabla(directorioSeleccionado, "CARPETA");
            }
        }
    }

    private void inyectarAssetEnTabla(File file, String naturaleza) {
        String estrategiaRed = (comboModosRed.getSelectedIndex() == 0) ? "DIRECTO" : "A TODOS";

        int rowIndex = tableModel.getRowCount();
        tableModel.addRow(new Object[]{naturaleza, file.getAbsolutePath(), "⚡ Analizando...", estrategiaRed});

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                if ("ARCHIVO".equals(naturaleza)) {
                    return formatearBytes(file.length());
                } else {
                    long totalBytes = obtenerTamañoDirectorioRecursivo(file);
                    return formatearBytes(totalBytes) + " (Carpeta Indexada)";
                }
            }

            @Override
            protected void done() {
                try {
                    String pesoFormateado = get();
                    tableModel.setValueAt(pesoFormateado, rowIndex, 2);
                } catch (Exception ex) {
                    tableModel.setValueAt("⚠️ No leído", rowIndex, 2);
                }
            }
        };
        worker.execute();
    }

    private long obtenerTamañoDirectorioRecursivo(File carpeta) {
        long total = 0;
        File[] archivos = carpeta.listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                if (f.isFile()) {
                    total += f.length();
                } else if (f.isDirectory()) {
                    total += obtenerTamañoDirectorioRecursivo(f);
                }
            }
        }
        return total;
    }

    private String formatearBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unidad = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %cB", bytes / Math.pow(1024, exp), unidad);
    }

    private void updateSummaryBanner() {
        String netModeStr = (comboModosRed.getSelectedIndex() == 0) ? "un usuario específico" : "todos los conectados";
        String natModeStr = btnModoArchivo.isSelected() ? "Archivos sueltos" : "Carpetas completas";
        String colorNat = btnModoArchivo.isSelected() ? "#5294e2" : "#e67e22";

        lblSummary.setText(String.format(
                "<html><body>🎯 <b>Modo actual:</b> Enviando a <b>%s</b> de tipo <b style='color:%s;'>%s</b>.</body></html>",
                netModeStr, colorNat, natModeStr
        ));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JFrame frame = new JFrame("bitBridge - Panel de Transferencias");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1020, 650); // Incrementada levemente la altura para alojar cómodamente los nuevos controles
        frame.setLocationRelativeTo(null);

        frame.add(new AdvancedTransferPanel());
        frame.setVisible(true);
    }
}
package org.bitBridge.view.swing.components.server;

import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class ConfiguracionView extends JDialog {
    private final ConfiguracionApp config = ConfiguracionApp.getInstancia();

    // Referencias a los paneles modulares
    private NetworkTab networkTab;
    private PerformanceTab performanceTab;
    private StorageTab storageTab;
    private SecurityTab securityTab;
    private AboutTab aboutTab;

    private final Color COLOR_PRIMARIO = new Color(0, 191, 255);
    private final Color COLOR_FONDO = new Color(30, 33, 37);

    public ConfiguracionView(Frame parent) {
        super(parent, "BitBridge Engine v2.6 - Advanced Settings", true);
        setSize(620, 780);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(COLOR_FONDO);
        setLayout(new BorderLayout());

        initUI();
    }

    private void initUI() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // Inicialización de módulos independientes
        networkTab = new NetworkTab();
        performanceTab = new PerformanceTab();
        storageTab = new StorageTab();
        securityTab = new SecurityTab();
        aboutTab = new AboutTab();

        tabs.addTab("🌐 Red & Nodo", networkTab);
        tabs.addTab("🚀 Rendimiento NIO", performanceTab);
        tabs.addTab("📁 Almacenamiento", storageTab);
        tabs.addTab("🛡️ Seguridad", securityTab);
        tabs.addTab("  Acerca de", aboutTab);

        add(tabs, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
    }

    // --- CLASES INTERNAS (MÓDULOS DE PESTAÑAS) ---


    private class NetworkTab extends BaseConfigPanel {
        private JTextField txtNombre, txtPuerto, txtMaxConn, txtBacklog;
        private JSlider sldSelectorThreads, sldWorkerThreads;
        private JLabel lblSelectorVal, lblWorkerVal, lblStatusNote;
        private JCheckBox chkNoDelay;

        private final ConfiguracionApp config = ConfiguracionApp.getInstancia();
        private final int CORES = Runtime.getRuntime().availableProcessors();
        private final Color COLOR_PRIMARIO = new Color(0, 191, 255);

        public NetworkTab() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

            // --- 1. CABECERA INFORMATIVA ---
            add(createExplanationHeader());
            add(Box.createVerticalStrut(15));

            // --- 2. ANÁLISIS DINÁMICO (MOVIDO ARRIBA PARA VISIBILIDAD) ---
            lblStatusNote = new JLabel();

            add(lblStatusNote);
            add(Box.createVerticalStrut(20));

            // --- 3. SECCIÓN: IDENTIDAD ---
            add(createSectionTitle("🆔 IDENTIFICACIÓN DEL NODO"));
            txtNombre = createStyledField(config.obtener(ConfigKey.SERVER_NAME, "BB-NODE-PRIMARY"));
            add(txtNombre);
            add(Box.createVerticalStrut(15));

            // --- 4. SECCIÓN: RED Y SOCKETS ---
            add(createSectionTitle("🌐 CONECTIVIDAD"));
            add(createFieldLabel("Puerto de Escucha:"));
            add(createPortPanel());
            add(Box.createVerticalStrut(10));

            JPanel dualRow = new JPanel(new GridLayout(1, 2, 12, 0));
            dualRow.setOpaque(false);
            dualRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

            txtMaxConn = createStyledField(config.obtener(ConfigKey.NET_MAX_CONN, "1000"));
            dualRow.add(createLabeledPanel("Capacidad Clientes:", txtMaxConn));

            txtBacklog = createStyledField(config.obtener(ConfigKey.NET_BACKLOG, "1024"));
            dualRow.add(createLabeledPanel("Cola Admisión:", txtBacklog));
            add(dualRow);

            add(Box.createVerticalStrut(20));

            // --- 5. SECCIÓN: HILOS (TUNING) ---
            add(createSectionTitle("🧵 MOTOR DE PROCESAMIENTO"));

            lblSelectorVal = createValueLabel();
            add(createCompactSliderRow("Recepción (I/O):",
                    sldSelectorThreads = createThreadSlider(1, CORES * 2, ConfigKey.NET_SELECTOR_THREADS, CORES),
                    lblSelectorVal));

            add(Box.createVerticalStrut(10));

            lblWorkerVal = createValueLabel();
            add(createCompactSliderRow("Ejecución (App):",
                    sldWorkerThreads = createThreadSlider(CORES, CORES * 16, ConfigKey.NET_WORKER_THREADS, CORES * 4),
                    lblWorkerVal));

            add(Box.createVerticalStrut(15));

            chkNoDelay = new JCheckBox("Modo Ultra-Latencia (TCP No Delay)");
            chkNoDelay.setSelected(config.obtenerBoolean(ConfigKey.NET_NODELAY, true));
            styleCheckBox(chkNoDelay);
            add(chkNoDelay);

            setupThreadListeners();

            updateRecommendation();
            add(Box.createVerticalGlue());
        }

        private JPanel createExplanationHeader() {
            JPanel pnl = new JPanel(new BorderLayout());
            pnl.setOpaque(false);
            pnl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));

            JLabel icon = new JLabel("⚙️");
            icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
            icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));

            JLabel text = new JLabel("<html><body style='width: 350px;'>" +
                    "<b style='color: #00bfff;'>Configuración del Motor de Red</b><br>" +
                    "<span style='color: #888;'>Ajuste cómo BitBridge gestiona las conexiones entrantes y la potencia de procesamiento dedicada a las tareas de red.</span>" +
                    "</body></html>");

            pnl.add(icon, BorderLayout.WEST);
            pnl.add(text, BorderLayout.CENTER);
            return pnl;
        }

        private void updateRecommendation() {
            int s = sldSelectorThreads.getValue();
            int w = sldWorkerThreads.getValue();
            String perfil, desc, impacto;
            Color accent;

            if (s <= CORES && w <= CORES * 4) {
                perfil = "EQUILIBRADO";
                desc = "Optimizado para la mayoría de los usuarios.";
                impacto = "✅ Bajo consumo de CPU | ✅ Latencia estable";
                accent = new Color(85, 255, 85);
            } else if (s > CORES || w > CORES * 8) {
                perfil = "ALTO RENDIMIENTO";
                desc = "Diseñado para servidores con tráfico masivo.";
                impacto = "⚠️ Mayor uso de energía | ✅ Máxima velocidad de respuesta";
                accent = new Color(0, 191, 255);
            } else {
                perfil = "MODO EXPERIMENTAL";
                desc = "Configuración manual personalizada.";
                impacto = "ℹ️ El rendimiento dependerá de su hardware.";
                accent = new Color(255, 170, 0);
            }

            lblStatusNote.setText("<html><div style='background: #1A1A1A; padding: 12px; border-left: 4px solid " +
                    String.format("#%02x%02x%02x", accent.getRed(), accent.getGreen(), accent.getBlue()) + ";'>" +
                    "<b style='color: " + String.format("#%02x%02x%02x", accent.getRed(), accent.getGreen(), accent.getBlue()) + ";'>" +
                    "PERFIL: " + perfil + "</b><br>" +
                    "<span style='color: #EEE;'>" + desc + "</span><br>" +
                    "<span style='color: #888; font-size: 9px;'>" + impacto + "</span></div></html>");
        }

        // --- HELPERS VISUALES ---

        private JPanel createPortPanel() {
            JPanel p = new JPanel(new BorderLayout(5, 0));
            p.setOpaque(false);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            txtPuerto = createStyledField(config.obtener(ConfigKey.SERVER_PORT, "8080"));
            txtPuerto.setEnabled(false);
            JButton btn = new JButton("🔓");
            btn.setPreferredSize(new Dimension(32, 28));
            btn.addActionListener(e -> {
                if(JOptionPane.showConfirmDialog(this, "¿Cambiar puerto crítico?", "Aviso", 0) == 0) txtPuerto.setEnabled(true);
            });
            p.add(txtPuerto, BorderLayout.CENTER);
            p.add(btn, BorderLayout.EAST);
            return p;
        }

        private JPanel createCompactSliderRow(String title, JSlider sld, JLabel val) {
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setOpaque(false);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            JLabel t = new JLabel(title);
            t.setFont(new Font("SansSerif", Font.BOLD, 11));
            t.setForeground(COLOR_PRIMARIO);
            t.setPreferredSize(new Dimension(95, 20));
            p.add(t, BorderLayout.WEST);
            p.add(sld, BorderLayout.CENTER);
            p.add(val, BorderLayout.EAST);
            return p;
        }

        private JPanel createLabeledPanel(String label, JTextField field) {
            JPanel p = new JPanel(new BorderLayout(5, 0));
            p.setOpaque(false);
            JLabel l = createFieldLabel(label);
            l.setPreferredSize(new Dimension(65, 20));
            p.add(l, BorderLayout.WEST);
            p.add(field, BorderLayout.CENTER);
            return p;
        }

        private JSlider createThreadSlider(int min, int max, ConfigKey key, int def) {
            JSlider sld = new JSlider(min, max, config.obtenerInt(key, def));
            sld.setOpaque(false);
            return sld;
        }

        private JLabel createValueLabel() {
            JLabel l = new JLabel("0 hilos", SwingConstants.RIGHT);
            l.setFont(new Font("Monospaced", Font.BOLD, 12));
            l.setPreferredSize(new Dimension(85, 20));
            return l;
        }

        private void setupThreadListeners() {

            sldSelectorThreads.addChangeListener(e -> {
                lblSelectorVal.setText(sldSelectorThreads.getValue() + " hilos");
                actualizarColorHilo(lblSelectorVal, sldSelectorThreads.getValue(), CORES);
                updateRecommendation();
            });

            sldWorkerThreads.addChangeListener(e -> {
                lblWorkerVal.setText(sldWorkerThreads.getValue() + " hilos");
                actualizarColorHilo(lblWorkerVal, sldWorkerThreads.getValue(), CORES * 4);
                updateRecommendation();
            });
            // Sincronizar colores y valores iniciales
            lblSelectorVal.setText(sldSelectorThreads.getValue() + " hilos");
            lblWorkerVal.setText(sldWorkerThreads.getValue() + " hilos");
            actualizarColorHilo(lblSelectorVal, sldSelectorThreads.getValue(), CORES);
            actualizarColorHilo(lblWorkerVal, sldWorkerThreads.getValue(), CORES * 4);
        }

        private void actualizarColorHilo(JLabel label, int valor, int recomendado) {
            if (valor > recomendado * 2.5) label.setForeground(new Color(255, 85, 85));
            else if (valor > recomendado) label.setForeground(new Color(255, 170, 0));
            else label.setForeground(new Color(85, 255, 85));
        }

        @Override
        public void save() {
            config.setProperty(ConfigKey.SERVER_NAME, txtNombre.getText());
            config.setProperty(ConfigKey.SERVER_PORT, txtPuerto.getText());
            config.setProperty(ConfigKey.NET_MAX_CONN, txtMaxConn.getText());
            config.setProperty(ConfigKey.NET_BACKLOG, txtBacklog.getText());
            config.setProperty(ConfigKey.NET_NODELAY, chkNoDelay.isSelected());
            config.setProperty(ConfigKey.NET_SELECTOR_THREADS, String.valueOf(sldSelectorThreads.getValue()));
            config.setProperty(ConfigKey.NET_WORKER_THREADS, String.valueOf(sldWorkerThreads.getValue()));
            config.guardarEnArchivo();
        }
    }

    private class PerformanceTab extends BaseConfigPanel {
        private JComboBox<String> cbMsgSize, cbDirSize, cbTransSize;
        private JSlider sldMsgCap, sldDirCap, sldTransCap;
        private JLabel lblDetailedRam, lblInfoNota;
        private JLabel lblMsgVal, lblDirVal, lblTransVal;

        public PerformanceTab() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));

            add(createSectionTitle("⚙️ OPTIMIZACIÓN DE MEMORIA NIO (OFF-HEAP)"));

            // --- NOTA EXPLICATIVA SOBRE CARGA Y CONCURRENCIA ---
            lblInfoNota = new JLabel();
            lblInfoNota.setAlignmentX(CENTER_ALIGNMENT);
            lblInfoNota.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));
            actualizarNotaInformativa();
            add(lblInfoNota);

            // --- FILA 1: MENSAJES ---
            lblMsgVal = createValueLabel();
            cbMsgSize = new JComboBox<>(new String[]{"4", "8", "16", "32"});
            cbMsgSize.setSelectedItem(config.obtener(ConfigKey.POOL_MSG_SIZE, "8"));
            sldMsgCap = createCapSlider(1000, 100000, ConfigKey.POOL_MSG_CAP, 10000);
            add(createControlRow("1. LATENCIA DE MENSAJERÍA (JSON/Pings):",
                    "Optimiza la respuesta de comandos y chat privado.",
                    cbMsgSize, sldMsgCap, lblMsgVal));

            add(Box.createVerticalStrut(20));

            // --- FILA 2: DIRECTORIOS ---
            lblDirVal = createValueLabel();
            cbDirSize = new JComboBox<>(new String[]{"64", "128", "256"});
            cbDirSize.setSelectedItem(config.obtener(ConfigKey.POOL_DIR_SIZE, "64"));
            sldDirCap = createCapSlider(100, 6000, ConfigKey.POOL_DIR_CAP, 1000);
            add(createControlRow("2. VELOCIDAD DE EXPLORACIÓN (Listado de Archivos):",
                    "Mejora el rendimiento al explorar carpetas con miles de archivos.",
                    cbDirSize, sldDirCap, lblDirVal));

            add(Box.createVerticalStrut(20));

            // --- FILA 3: TRANSFERENCIAS ---
            lblTransVal = createValueLabel();
            cbTransSize = new JComboBox<>(new String[]{"512", "1024", "2048", "4096"});
            cbTransSize.setSelectedItem(config.obtener(ConfigKey.POOL_TRANS_SIZE, "512"));
            sldTransCap = createCapSlider(10, 5000, ConfigKey.POOL_TRANS_CAP, 500);
            add(createControlRow("3. FLUJO DE TRANSFERENCIA (Streaming Data):",
                    "Determina cuántos datos pueden fluir en paralelo sin cuellos de botella.",
                    cbTransSize, sldTransCap, lblTransVal));

            add(Box.createVerticalStrut(30));

            // --- MONITOR DE RAM ---
            lblDetailedRam = new JLabel();
            lblDetailedRam.setAlignmentX(CENTER_ALIGNMENT);
            add(lblDetailedRam);

            setupListeners();
            actualizarCalculoRAM();
            add(Box.createVerticalGlue());
        }

        private void actualizarNotaInformativa() {
            // Esta nota cambia según la RAM total para aconsejar al usuario
            lblInfoNota.setText("<html><div style='text-align: center; width: 420px; color: #999; font-size: 10px;'>" +
                    "💡 <b style='color: #DDD;'>Análisis de Configuración:</b><br>" +
                    "A mayor memoria y cantidad de slots, el sistema responde mejor en <span style='color: #55FF55;'>entornos concurrentes</span> " +
                    "(muchos usuarios/archivos). Con valores bajos, BitBridge es más <span style='color: #55AAFF;'>ligero</span>, " +
                    "pero podría saturarse ante cargas de trabajo pesadas.</div></html>");
        }

        private JPanel createControlRow(String title, String subtitle, JComboBox<String> cb, JSlider sld, JLabel val) {
            JPanel pnl = new JPanel(new GridBagLayout());
            pnl.setOpaque(false);
            pnl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
            GridBagConstraints gbc = new GridBagConstraints();

            JLabel lblTitle = new JLabel(title);
            lblTitle.setForeground(COLOR_PRIMARIO);
            lblTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3; gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            pnl.add(lblTitle, gbc);

            JLabel lblSub = new JLabel(subtitle);
            lblSub.setForeground(new Color(110, 110, 110));
            lblSub.setFont(new Font("SansSerif", Font.ITALIC, 10));
            gbc.gridy = 1; gbc.insets = new Insets(2, 0, 8, 0);
            pnl.add(lblSub, gbc);

            gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE; gbc.insets = new Insets(0, 0, 0, 15);
            cb.setPreferredSize(new Dimension(80, 26));
            pnl.add(cb, gbc);

            gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
            pnl.add(sld, gbc);

            gbc.gridx = 2; gbc.weightx = 0; gbc.insets = new Insets(0, 15, 0, 0);
            val.setPreferredSize(new Dimension(95, 25));
            pnl.add(val, gbc);

            return pnl;
        }

        private JLabel createValueLabel() {
            JLabel l = new JLabel("0 u.");
            l.setForeground(COLOR_PRIMARIO); // Usamos tu color cian
            l.setFont(new Font("Monospaced", Font.BOLD, 13)); // Monospaced para que el texto no baile
            l.setHorizontalAlignment(SwingConstants.RIGHT);
            return l;
        }

        private JSlider createCapSlider(int min, int max, ConfigKey key, int def) {
            // 1. Obtener el valor de la configuración
            int val = config.obtenerInt(key, def);

            // 2. Validación de seguridad para evitar el crash
            if (min >= max) {
                max = min + 100; // Forzar un rango válido si hay error en parámetros
            }

            // 3. Asegurar que el valor actual esté dentro del rango
            if (val < min) val = min;
            if (val > max) val = max;

            // 4. Crear el slider con valores garantizados
            return new JSlider(min, max, val);
        }

        private void setupListeners() {
            ChangeListener cl = e -> {
                actualizarCalculoRAM();
                lblMsgVal.setText(String.format("%,d u.", sldMsgCap.getValue()));
                lblDirVal.setText(String.format("%,d u.", sldDirCap.getValue()));
                lblTransVal.setText(String.format("%,d u.", sldTransCap.getValue()));
            };
            sldMsgCap.addChangeListener(cl);
            sldDirCap.addChangeListener(cl);
            sldTransCap.addChangeListener(cl);

            ActionListener al = e -> actualizarCalculoRAM();
            cbMsgSize.addActionListener(al);
            cbDirSize.addActionListener(al);
            cbTransSize.addActionListener(al);
            cl.stateChanged(null); // Update inicial
        }

        private void actualizarCalculoRAM() {
            try {
                // 1. Obtener datos del sistema
                long totalSystemRAM = getTotalSystemRAM();
                long limiteSeguridad = (long) (totalSystemRAM * 0.25); // Umbral del 25%

                // 2. Cálculos de los Pools
                long mB = getBytes(cbMsgSize, sldMsgCap);
                long dB = getBytes(cbDirSize, sldDirCap);
                long tB = getBytes(cbTransSize, sldTransCap);
                long totalBytes = mB + dB + tB;
                double totalMB = totalBytes / (1024.0 * 1024.0);

                // 3. Determinación de Perfil y Riesgo
                boolean esPeligroso = totalBytes > limiteSeguridad;

                // Colores dinámicos
                String colorBase = esPeligroso ? "#FF5555" : (totalMB > 600 ? "#FFAA00" : "#55FF55");

                // Texto de Perfil
                String perfil;
                if (esPeligroso) {
                    perfil = "CRÍTICO / REQUIERE AJUSTE -Xmx";
                } else if (totalMB > 600) {
                    perfil = "EQUILIBRADO / CONCURRENTE";
                } else {
                    perfil = "LIGERO / BAJO CONSUMO";
                }

                // Mensaje de advertencia extra si es peligroso
                String alertaHTML = esPeligroso
                        ? "<div style='color: #FF5555; font-size: 9px; margin-top: 5px;'>⚠️ Supera el 25% de RAM física. Riesgo de OutMemory.</div>"
                        : "";

                // 4. Renderizado del Label
                lblDetailedRam.setText(String.format(
                        "<html><div style='background: #1A1C1E; padding: 18px; border: 1px solid %s; border-radius: 12px;'>" +
                                "<table width='420' style='color: #888; font-family: sans-serif; font-size: 11px;'>" +
                                "<tr><td>Reserva Mensajería:</td><td align='right' style='color:#DDD;'>%s</td></tr>" +
                                "<tr><td>Reserva Directorios:</td><td align='right' style='color:#DDD;'>%s</td></tr>" +
                                "<tr><td>Reserva Streaming:</td><td align='right' style='color:#DDD;'>%s</td></tr>" +
                                "<tr><td colspan='2' style='border-top: 1px solid #444; padding-top: 10px;'>" +
                                "<div style='text-align: center;'>" +
                                "<span style='font-size: 9px; color: #666;'>PERFIL ACTUAL: <b style='color:%s;'>%s</b></span><br>" +
                                "<b style='font-size: 22px; color: %s;'>%s TOTAL</b>" +
                                "%s" + // Aquí sale la alerta si aplica
                                "</div></td></tr>" +
                                "</table></div></html>",
                        esPeligroso ? "#FF5555" : "#333", // Borde rojo si es peligroso
                        formatBytes(mB), formatBytes(dB), formatBytes(tB),
                        colorBase, perfil, colorBase, formatBytes(totalBytes), alertaHTML
                ));

            } catch (Exception ignored) {}
        }

        private long getBytes(JComboBox<String> cb, JSlider sld) {
            return Long.parseLong((String) cb.getSelectedItem()) * 1024L * sld.getValue();
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }

        private long getTotalSystemRAM() {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return osBean.getTotalPhysicalMemorySize();
        }

        @Override
        public void save() {
            long totalBytes = getBytes(cbMsgSize, sldMsgCap) +
                    getBytes(cbDirSize, sldDirCap) +
                    getBytes(cbTransSize, sldTransCap);

            long totalMB = totalBytes / (1024 * 1024);
            long limiteSeguridad = (long) (getTotalSystemRAM() * 0.25);

            if (totalBytes > limiteSeguridad) {
                int opt = JOptionPane.showConfirmDialog(this,
                        "La configuración excede el límite recomendado.\n" +
                                "BitBridge intentará actualizar el archivo de inicio (.vmoptions)\n" +
                                "para asignar " + (totalMB + 512) + "MB de RAM.\n\n" +
                                "¿Deseas continuar?",
                        "Ajuste de Sistema", JOptionPane.YES_NO_OPTION);

                if (opt != JOptionPane.YES_OPTION) return;
            }

            // 1. Guardar las propiedades internas (config.properties)
            guardarPropiedadesInternas();

            // 2. MODIFICAR EL ARCHIVO DE INICIO REAL
            actualizarVmOptions(totalMB);

            JOptionPane.showMessageDialog(this, "Configuración aplicada.\nEl nuevo límite de RAM se activará al reiniciar BitBridge.");

        }

        private void guardarPropiedadesInternas() {
            config.setProperty(ConfigKey.POOL_MSG_SIZE, (String) cbMsgSize.getSelectedItem());
            config.setProperty(ConfigKey.POOL_MSG_CAP, String.valueOf(sldMsgCap.getValue()));
            config.setProperty(ConfigKey.POOL_DIR_SIZE, (String) cbDirSize.getSelectedItem());
            config.setProperty(ConfigKey.POOL_DIR_CAP, String.valueOf(sldDirCap.getValue()));
            config.setProperty(ConfigKey.POOL_TRANS_SIZE, (String) cbTransSize.getSelectedItem());
            config.setProperty(ConfigKey.POOL_TRANS_CAP, String.valueOf(sldTransCap.getValue()));
            config.guardarEnArchivo();
            JOptionPane.showMessageDialog(this, "Configuración guardada. Reinicie para aplicar cambios.");
        }

        /**
         * Obtiene la ruta del archivo .vmoptions según el sistema operativo
         * Garantiza que el usuario tenga permisos de escritura.
         */
        private File getVmOptionsFile() {
            String os = System.getProperty("os.name").toLowerCase();
            String userHome = System.getProperty("user.home");
            File vmFile;

            if (os.contains("win")) {
                // Windows: %APPDATA%/bitbridge/bitbridge64.vmoptions
                String appData = System.getenv("APPDATA");
                vmFile = new File(appData + File.separator + "bitbridge" + File.separator + "bitbridge64.vmoptions");
            } else {
                // Linux/Unix: ~/.config/bitbridge/bitbridge64.vmoptions
                vmFile = new File(userHome + "/.config/bitbridge/bitbridge64.vmoptions");
            }

            // Crear carpetas si no existen
            if (!vmFile.getParentFile().exists()) {
                vmFile.getParentFile().mkdirs();
            }
            return vmFile;
        }

        private void actualizarVmOptions(long totalMBRequerido) {
            // Margen: RAM de Pools + 512MB para el Heap base de Spring/JavaFX
            long nuevaMaxRAM = totalMBRequerido + 512;
            // La DirectMemorySize debe cubrir los Pools NIO
            long nuevaDirectMemory = totalMBRequerido + 128;

            File vmFile = getVmOptionsFile();

            try (PrintWriter out = new PrintWriter(new FileWriter(vmFile))) {
                out.println("# Perfil de alto rendimiento generado por BitBridge GUI");
                out.println("# Fecha: " + new java.util.Date());
                out.println("-Xms32m");
                out.println("-Xmx" + nuevaMaxRAM + "m");
                out.println("-XX:MaxDirectMemorySize=" + nuevaDirectMemory + "m");
                out.println("-XX:+UseZGC");
                out.println("-Dfile.encoding=UTF-8");

                Logger.logInfo("Configuración de inicio personalizada guardada en: " + vmFile.getAbsolutePath());

                JOptionPane.showMessageDialog(this,
                        "Configuración guardada exitosamente.\n\n" +
                                "Se ha creado un perfil de memoria en:\n" + vmFile.getAbsolutePath() + "\n\n" +
                                "Por favor, reinicie la aplicación para aplicar los cambios.");

            } catch (IOException e) {
                Logger.logError("Error crítico al escribir vmoptions: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Error al guardar el archivo de inicio: " + e.getMessage(),
                        "Error de Permisos", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class StorageTab extends BaseConfigPanel {
        private JTextField txtDownloadPath; // Renombrado para claridad
        private JTextField txtSharedPath;   // NUEVO: Ruta para compartir
        private JTextField txtMaxActive;
        private JCheckBox chkResume, chkOverwrite;

        public StorageTab() {
            // --- SECCIÓN 1: DESCARGAS ---
            add(createSectionTitle("DIRECTORIO DE ENTRADA (DOWNLOADS)"));
            add(createFieldLabel("Donde se guardarán los archivos recibidos:"));
            txtDownloadPath = createStyledField(config.obtener(ConfigKey.DOWNLOAD_DIR, ""));
            txtDownloadPath.setEditable(false);
            add(createPathPickerRow(txtDownloadPath));

            add(Box.createVerticalStrut(20));

            // --- SECCIÓN 2: CARPETA COMPARTIDA (NUEVO) ---
            add(createSectionTitle("DIRECTORIO DE SALIDA (ASSETS COMPARTIDOS)"));
            add(createFieldLabel("Carpeta raíz que otros nodos verán al conectarse:"));

            // Obtenemos el valor de SHARED_DIR del config
            txtSharedPath = createStyledField(config.obtener(ConfigKey.SHARED_DIR, ""));
            txtSharedPath.setEditable(false);
            add(createPathPickerRow(txtSharedPath));

            add(Box.createVerticalStrut(20));

            // --- SECCIÓN 3: POLÍTICAS ---
            add(createSectionTitle("POLÍTICAS DE TRANSFERENCIA"));
            add(createFieldLabel("Descargas simultáneas máximas:"));
            txtMaxActive = createStyledField(config.obtener(ConfigKey.TRANSFER_MAX_ACTIVE, "3"));
            txtMaxActive.setMaximumSize(new Dimension(100, 32));
            add(txtMaxActive);

            add(Box.createVerticalStrut(10));
            chkResume = new JCheckBox("Reanudar transferencias automáticamente", true);
            styleCheckBox(chkResume);
            add(chkResume);

            add(Box.createVerticalGlue());
        }

        // Helper para no repetir código de la fila con botón 📂
        private JPanel createPathPickerRow(JTextField field) {
            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            JButton btnBrowse = new JButton("📂");
            btnBrowse.setFocusPainted(false);
            btnBrowse.setBackground(new Color(60, 63, 65));
            btnBrowse.setForeground(Color.WHITE);

            Dimension btnDim = new Dimension(45, 32);
            btnBrowse.setPreferredSize(btnDim);
            btnBrowse.setMinimumSize(btnDim);
            btnBrowse.setMaximumSize(btnDim);

            btnBrowse.addActionListener(e -> selectFolder(field));

            row.add(field);
            row.add(Box.createHorizontalStrut(5));
            row.add(btnBrowse);
            return row;
        }

        @Override
        public void save() {
            // Guardamos ambas rutas en el archivo de configuración
            config.setProperty(ConfigKey.DOWNLOAD_DIR, txtDownloadPath.getText());
            config.setProperty(ConfigKey.SHARED_DIR, txtSharedPath.getText());
            config.setProperty(ConfigKey.TRANSFER_MAX_ACTIVE, txtMaxActive.getText());
        }
    }

    private class SecurityTab extends BaseConfigPanel {
        private JPasswordField txtKey;
        private JComboBox<String> comboEnc;
        private JCheckBox chkAuthRequired;
        private JButton btnToggle;
        private boolean isPasswordVisible = false;

        public SecurityTab() {
            add(createSectionTitle("CONTROL DE ACCESO & SEGURIDAD"));

            // --- CHECKBOX DE HABILITACIÓN ---
            chkAuthRequired = new JCheckBox("Requerir autenticación para conexiones entrantes");
            chkAuthRequired.setSelected(Boolean.parseBoolean(config.obtener(ConfigKey.AUTH_REQUIRED, "true")));
            styleCheckBox(chkAuthRequired);
            // Lógica para habilitar/deshabilitar el campo de texto al hacer click
            chkAuthRequired.addActionListener(e -> toggleAuthFields(chkAuthRequired.isSelected()));
            add(chkAuthRequired);

            add(Box.createVerticalStrut(10));
            add(createFieldLabel("Llave de Autenticación (Pre-Shared Key):"));

            // --- PANEL DE PASSWORD ---
            JPanel passPanel = new JPanel();
            passPanel.setLayout(new BoxLayout(passPanel, BoxLayout.X_AXIS));
            passPanel.setOpaque(false);
            passPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            passPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            txtKey = new JPasswordField(config.obtener(ConfigKey.AUTH_KEY, ""));
            stylePasswordField(txtKey);

            btnToggle = new JButton("👁️");
            btnToggle.setPreferredSize(new Dimension(45, 32));
            btnToggle.setMinimumSize(new Dimension(45, 32));
            btnToggle.setMaximumSize(new Dimension(45, 32));
            btnToggle.setFocusPainted(false);
            btnToggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnToggle.addActionListener(e -> togglePasswordVisibility());

            passPanel.add(txtKey);
            passPanel.add(Box.createHorizontalStrut(8));
            passPanel.add(btnToggle);
            add(passPanel);

            // Inicializar el estado de los campos según el config
            toggleAuthFields(chkAuthRequired.isSelected());

            add(Box.createVerticalStrut(20));

            // --- SECCIÓN DE CIFRADO ---
            add(createSectionTitle("CRIPTOGRAFÍA DE RED"));
            add(createFieldLabel("Algoritmo de cifrado de túnel:"));

            comboEnc = new JComboBox<>(new String[]{"AES-256-GCM (Seguro)", "ChaCha20 (Rápido)", "None (Solo LAN)"});
            comboEnc.setSelectedItem(config.obtener(ConfigKey.NET_ENCRYPTION, "AES-256-GCM (Seguro)"));
            comboEnc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            comboEnc.setBackground(new Color(45, 48, 54));
            comboEnc.setForeground(Color.WHITE);
            comboEnc.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(comboEnc);

            add(Box.createVerticalStrut(20));

            // --- OPCIONES ADICIONALES ---
            JCheckBox chkIpBlock = new JCheckBox("Bloquear IPs tras 3 intentos fallidos", true);
            styleCheckBox(chkIpBlock);
            add(chkIpBlock);

            add(Box.createVerticalGlue());
        }

        private void toggleAuthFields(boolean enabled) {
            txtKey.setEnabled(enabled);
            btnToggle.setEnabled(enabled);
            // Opcional: Cambiar el fondo para dar feedback visual de bloqueo
            txtKey.setBackground(enabled ? new Color(45, 48, 54) : new Color(35, 38, 41));
        }

        private void togglePasswordVisibility() {
            if (isPasswordVisible) {
                txtKey.setEchoChar('•');
            } else {
                txtKey.setEchoChar((char) 0);
            }
            isPasswordVisible = !isPasswordVisible;
        }

        private void stylePasswordField(JPasswordField f) {
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            f.setBackground(new Color(45, 48, 54));
            f.setForeground(Color.WHITE);
            f.setCaretColor(Color.WHITE);
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(70, 70, 70)),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)
            ));
        }

        @Override
        public void save() {
            config.setProperty(ConfigKey.AUTH_REQUIRED, chkAuthRequired.isSelected());
            config.setProperty(ConfigKey.AUTH_KEY, new String(txtKey.getPassword()));

            // Casting limpio del JComboBox
            config.setProperty(ConfigKey.NET_ENCRYPTION, (String) comboEnc.getSelectedItem());
        }
    }

    // --- CLASE BASE PARA PANELES (ESTILO COMÚN) ---

    private abstract class BaseConfigPanel extends JPanel {

        public BaseConfigPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            //setBorder(new EmptyBorder(25, 30, 25, 30));
            setBorder(new EmptyBorder(20, 25, 20, 25));
        }

        protected abstract void save();

        protected JLabel createSectionTitle(String text) {
            JLabel l = new JLabel(text);
            l.setForeground(COLOR_PRIMARIO);
            l.setFont(new Font("SansSerif", Font.BOLD, 11));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setBorder(new EmptyBorder(10, 0, 8, 0));
            return l;
        }

        protected JLabel createFieldLabel(String text) {
            JLabel l = new JLabel("<html><font color='#adb5bd'>" + text + "</font></html>");
            l.setFont(new Font("SansSerif", Font.PLAIN, 11));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            return l;
        }

        protected JTextField createStyledField(String text) {
            JTextField f = new JTextField(text);
            // Limitamos la altura máxima para que no se estire infinitamente
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            f.setPreferredSize(new Dimension(Integer.MAX_VALUE, 32));
            f.setBackground(new Color(45, 48, 54));
            f.setForeground(Color.WHITE);
            f.setCaretColor(Color.WHITE);
            f.setAlignmentX(Component.LEFT_ALIGNMENT);
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(70, 70, 70)),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)
            ));
            return f;
        }

        protected void styleSlider(JSlider s) {
            s.setOpaque(false);
            s.setMajorTickSpacing(512);
            s.setPaintTicks(true);
            s.setPaintLabels(true);
            s.setForeground(Color.GRAY);
        }

        protected void styleCheckBox(JCheckBox c) {
            c.setOpaque(false);
            c.setForeground(Color.LIGHT_GRAY);
        }
    }

    // --- ACCIONES GLOBALES ---

    private JPanel createFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        footer.setOpaque(false);
        JButton btnSave = new JButton("APLICAR CONFIGURACIÓN");
        btnSave.setBackground(COLOR_PRIMARIO);
        btnSave.addActionListener(e -> {
            networkTab.save();
            performanceTab.save();
            storageTab.save();
            securityTab.save();
            config.guardarEnArchivo();
            JOptionPane.showMessageDialog(this, "Ajustes guardados. Reinicie el motor.");
            dispose();
        });
        footer.add(btnSave);
        return footer;
    }

    private void unlockPort(JTextField f) { f.setEnabled(true); }

    private void selectFolder(JTextField f) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            f.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }
}
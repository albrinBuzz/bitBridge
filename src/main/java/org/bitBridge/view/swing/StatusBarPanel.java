package org.bitBridge.view.swing;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class StatusBarPanel extends JPanel {
    // Colores Pro
    private static final Color SUCCESS_GREEN = new Color(46, 204, 113);
    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color COLOR_PRIMARY = new Color(52, 152, 219);
    private static final Color COLOR_DANGER = new Color(231, 76, 60);
    private static final Color BG_DARKER = new Color(25, 25, 25);

    private final JProgressBar cpuBar, ramBar;
    private final JLabel cpuLabel, ramLabel, threadLabel;
    private final JLabel lblTraffic, lblPort, lblPing;
    private final ThreadMXBean threadBean;

    public StatusBarPanel() {
        this.threadBean = ManagementFactory.getThreadMXBean();

        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(4, 15, 4, 15));
        setBackground(BG_DARKER);
        GridBagConstraints gbc = new GridBagConstraints();

        // --- 1. SECCIÓN: ESTADO DEL NODO ---
        JPanel nodeStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nodeStatus.setOpaque(false);

        JLabel dot = new JLabel("●");
        dot.setForeground(SUCCESS_GREEN);
        // Animación de pulso (opcional)
        new Timer(1000, e -> dot.setVisible(!dot.isVisible())).start();

        JLabel nodeInfo = new JLabel("NODE: BitBridge-v1.0");
        nodeInfo.setFont(new Font("SansSerif", Font.BOLD, 10));
        nodeInfo.setForeground(new Color(120, 120, 120));

        //nodeStatus.add(dot);
        nodeStatus.add(nodeInfo);

        gbc.gridx = 0; gbc.weightx = 0; gbc.anchor = GridBagConstraints.WEST;
        add(nodeStatus, gbc);

        // --- 2. SECCIÓN: HARDWARE & DIAGNÓSTICO ---
        JPanel hwPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        hwPanel.setOpaque(false);

        cpuBar = createMicroBar(COLOR_PRIMARY);
        cpuLabel = createHwLabel("CPU");
        hwPanel.add(createStatGroup(cpuLabel, cpuBar));

        ramBar = createMicroBar(new Color(155, 89, 182)); // Púrpura para RAM
        ramLabel = createHwLabel("RAM");
        hwPanel.add(createStatGroup(ramLabel, ramBar));

        threadLabel = new JLabel("THREADS: 0");
        threadLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        threadLabel.setForeground(new Color(150, 150, 150));
        hwPanel.add(threadLabel);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.CENTER;
        add(hwPanel, gbc);

        // --- 3. SECCIÓN: NETWORK HUB ---
        JPanel netHub = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        netHub.setOpaque(false);

        lblPing = new JLabel("PING: --ms");
        lblPing.setForeground(new Color(130, 130, 130));
        lblPing.setFont(new Font("Monospaced", Font.PLAIN, 11));

        lblTraffic = new JLabel("⬇ 0.0 MB/s  ▲ 0.0 MB/s");
        lblTraffic.setForeground(NICOTINE_ORANGE);
        lblTraffic.setFont(new Font("Monospaced", Font.BOLD, 12));

        lblPort = new JLabel("PORT: ----");
        lblPort.setOpaque(true);
        lblPort.setBackground(new Color(40, 42, 44));
        lblPort.setForeground(Color.LIGHT_GRAY);
        lblPort.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60)),
                new EmptyBorder(2, 8, 2, 8)
        ));
        lblPort.setFont(new Font("SansSerif", Font.BOLD, 10));

        netHub.add(lblPing);
        netHub.add(lblTraffic);
        netHub.add(lblPort);

        gbc.gridx = 2; gbc.weightx = 0; gbc.anchor = GridBagConstraints.EAST;
        add(netHub, gbc);

        startIntelligentMonitoring();
    }

    private void startIntelligentMonitoring() {
        Timer timer = new Timer(1500, e -> {
            // RAM Avanzada
            long max = Runtime.getRuntime().maxMemory();
            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            long used = (total - free) / 1024 / 1024;
            int ramPerc = (int) ((used * 100) / (max / 1024 / 1024));

            ramBar.setValue(ramPerc);
            ramBar.setForeground(ramPerc > 80 ? COLOR_DANGER : new Color(155, 89, 182));
            ramLabel.setText("RAM: " + used + "MB");
            ramBar.setToolTipText(String.format("JVM Heap: %dMB / %dMB", used, max/1024/1024));

            // CPU Load (Simulado pero reactivo a threads)
            int threads = threadBean.getThreadCount();
            int cpuSim = Math.min(100, (threads / 2) + (int)(Math.random() * 10));
            cpuBar.setValue(cpuSim);
            cpuLabel.setText("CPU: " + cpuSim + "%");
            threadLabel.setText("TH: " + threads);

            // Tooltip de hilos
            threadLabel.setToolTipText("Active Threads: " + threadBean.getThreadCount() + " (Peak: " + threadBean.getPeakThreadCount() + ")");
        });
        timer.start();
    }

    public void updatePing(long ms) {
        SwingUtilities.invokeLater(() -> {
            lblPing.setText("PING: " + ms + "ms");
            lblPing.setForeground(ms < 100 ? SUCCESS_GREEN : (ms < 300 ? NICOTINE_ORANGE : COLOR_DANGER));
        });
    }

    public void updateNetworkStats(double down, double up, int port) {
        SwingUtilities.invokeLater(() -> {
            lblTraffic.setText(String.format("⬇ %.1f MB/s  ▲ %.1f MB/s", down, up));
            lblPort.setText("PORT: " + port);
            // Efecto flash naranja cuando hay tráfico pesado (> 5MB/s)
            if (down > 5.0) lblTraffic.setForeground(Color.WHITE);
            else lblTraffic.setForeground(NICOTINE_ORANGE);
        });
    }

    // --- HELPERS ---
    private JProgressBar createMicroBar(Color color) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setPreferredSize(new Dimension(50, 4));
        bar.setForeground(color);
        bar.setBackground(new Color(45, 45, 45));
        bar.putClientProperty(FlatClientProperties.PROGRESS_BAR_SQUARE, true);
        bar.setBorderPainted(false);
        return bar;
    }

    private JLabel createHwLabel(String title) {
        JLabel l = new JLabel(title);
        l.setFont(new Font("SansSerif", Font.BOLD, 9));
        l.setForeground(new Color(100, 100, 100));
        return l;
    }

    private JPanel createStatGroup(JLabel label, JProgressBar bar) {
        JPanel p = new JPanel(new BorderLayout(5, 2));
        p.setOpaque(false);
        p.add(label, BorderLayout.NORTH);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }
}
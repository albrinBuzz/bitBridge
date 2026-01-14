package org.bitBridge.view.swing.components.transfers;




import org.bitBridge.Client.TransferManager;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class TransferenceControlPanelSwing extends JPanel {

    private JProgressBar progressBar;
    private JLabel progressPercentageLabel;
    private JLabel detailLabel;
    private JButton pauseButton, resumeButton, cancelButton;

    private final TransferManager transferManager;
    private final String mode;

    private int lastPercentage = -1;
    private long lastUIUpdateTime = 0;
    private static final int UI_UPDATE_INTERVAL = 150;

    // Colores Dark UI
    private final Color COLOR_TARJETA = new Color(45, 52, 54);
    private final Color COLOR_ACENTO = new Color(0, 191, 255);
    private final Color COLOR_TEXTO_SEC = new Color(170, 170, 170);

    public TransferenceControlPanelSwing(String mode, Transferencia transferencia, TransferManager transferManager) {
        this.mode = mode;
        this.transferManager = transferManager;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(COLOR_TARJETA);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 63, 65), 1),
                new EmptyBorder(12, 15, 12, 15)
        ));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        initUI(transferencia);
    }

    private void initUI(Transferencia transferencia) {
        // --- FILA 1: Header (Icono + Nombre + Badge) ---
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        String icon = mode.equals("SENDING") ? "📤 " : "📥 ";
        JLabel title = new JLabel(icon + transferencia.getFileName());
        title.setForeground(COLOR_ACENTO);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));

        JLabel badge = new JLabel(mode.equals("SENDING") ? " ENVIANDO " : " RECIBIENDO ");
        badge.setOpaque(true);
        badge.setBackground(new Color(60, 63, 65));
        badge.setForeground(COLOR_TEXTO_SEC);
        badge.setFont(new Font("SansSerif", Font.BOLD, 9));

        header.add(title, BorderLayout.WEST);
        header.add(badge, BorderLayout.EAST);

        // --- FILA 2: Barra de Progreso ---
        JPanel progressRow = new JPanel(new BorderLayout(10, 0));
        progressRow.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(0, 12));
        progressBar.setForeground(COLOR_ACENTO);
        progressBar.setBackground(new Color(30, 30, 30));
        progressBar.setBorderPainted(false);

        progressPercentageLabel = new JLabel("0%");
        progressPercentageLabel.setForeground(Color.WHITE);
        progressPercentageLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        progressRow.add(progressBar, BorderLayout.CENTER);
        progressRow.add(progressPercentageLabel, BorderLayout.EAST);

        // --- FILA 3: Footer (Detalles + Controles) ---
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);

        detailLabel = new JLabel("Calculando... 0 KB/s");
        detailLabel.setForeground(COLOR_TEXTO_SEC);
        detailLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controls.setOpaque(false);

        pauseButton = createSmallButton("⏸", new Color(217, 119, 6));
        resumeButton = createSmallButton("▶", new Color(22, 163, 74));
        cancelButton = createSmallButton("✕", new Color(239, 68, 68));

        resumeButton.setEnabled(false);

        // Listeners
        pauseButton.addActionListener(e -> {
            transferManager.pause();
            pauseButton.setEnabled(false);
            resumeButton.setEnabled(true);
        });

        resumeButton.addActionListener(e -> {
            transferManager.resume();
            resumeButton.setEnabled(false);
            pauseButton.setEnabled(true);
        });

        cancelButton.addActionListener(e -> transferManager.cancel());

        controls.add(pauseButton);
        controls.add(resumeButton);
        controls.add(cancelButton);

        footer.add(detailLabel, BorderLayout.WEST);
        footer.add(controls, BorderLayout.EAST);

        // Ensamblar
        add(header);
        add(Box.createVerticalStrut(8));
        add(progressRow);
        add(Box.createVerticalStrut(8));
        add(footer);
    }

    private JButton createSmallButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(35, 25));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public void updateProgressBar(int progress) {
        // OPTIMIZACIÓN 1: Solo actualizar si el porcentaje entero cambió
        // Esto evita miles de llamadas inútiles si el archivo es grande
        if (progress == lastPercentage) return;
        this.lastPercentage = progress;

        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(progress);
            progressPercentageLabel.setText(progress + "%");

            if (progress >= 100) {
                setCompletado();
            }
        });
    }

    public void updateMetrics(double speedMBs, String eta) {
        long now = System.currentTimeMillis();

        // OPTIMIZACIÓN 2: Throttling de métricas
        // El ojo humano no puede leer cambios de texto cada 5ms.
        // Actualizar cada 150ms es fluido y libera al procesador.
        if (now - lastUIUpdateTime < UI_UPDATE_INTERVAL && speedMBs > 0) {
            return;
        }
        lastUIUpdateTime = now;

        SwingUtilities.invokeLater(() -> {
            String stats = formatStats(speedMBs, eta);

            // OPTIMIZACIÓN 3: Evitar el re-layout si el texto es idéntico
            if (!detailLabel.getText().equals(stats)) {
                detailLabel.setText(stats);
                detailLabel.setForeground(speedMBs <= 0 ? new Color(239, 68, 68) : COLOR_TEXTO_SEC);
            }
        });
    }

    private String formatStats(double speedMBs, String eta) {
        if (speedMBs >= 1.0) {
            return String.format("%.2f MB/s — Quedan: %s", speedMBs, eta);
        } else {
            return String.format("%.1f KB/s — Quedan: %s", speedMBs * 1024, eta);
        }
    }

    private void setCompletado() {
        progressBar.setForeground(new Color(46, 204, 113));
        detailLabel.setText("✅ Transferencia completada");
        detailLabel.setForeground(new Color(46, 204, 113));
        pauseButton.setEnabled(false);
        resumeButton.setEnabled(false);
    }
}
package org.bitBridge.view.swing.components.transfers;




import org.bitBridge.Client.TransferManager;
import org.bitBridge.models.Transferencia;

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
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(progress);
            progressPercentageLabel.setText(progress + "%");

            if (progress >= 100) {
                progressBar.setForeground(new Color(46, 204, 113)); // Verde éxito
                detailLabel.setText("✅ Transferencia completada");
                detailLabel.setForeground(new Color(46, 204, 113));
                pauseButton.setEnabled(false);
                resumeButton.setEnabled(false);
            }
        });
    }

    public void updateMetrics(double speedMBs, String eta) {
        SwingUtilities.invokeLater(() -> {
            // Formateamos la velocidad para que no tenga demasiados decimales
            String stats;
            if (speedMBs >= 1.0) {
                stats = String.format("%.2f MB/s — Quedan: %s", speedMBs, eta);
            } else {
                // Si es menos de 1 MB, mostramos KB para que sea más legible
                stats = String.format("%.1f KB/s — Quedan: %s", speedMBs * 1024, eta);
            }

            detailLabel.setText(stats);

            // Efecto visual: si la velocidad es 0 (pausado o lento), cambiamos el color
            if (speedMBs == 0) {
                detailLabel.setForeground(new Color(239, 68, 68)); // Rojo suave
            } else {
                detailLabel.setForeground(COLOR_TEXTO_SEC);
            }
        });
    }
}
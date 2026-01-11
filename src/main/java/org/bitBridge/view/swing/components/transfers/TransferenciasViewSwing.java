package org.bitBridge.view.swing.components.transfers;



import org.bitBridge.Client.TransferManager;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.models.TransferProgress;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.FileTransferState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;

public class TransferenciasViewSwing extends JPanel implements TransferencesObserver {

    private final HashMap<String, TransferenceControlPanelSwing> transferMap = new HashMap<>();
    private final JPanel transferBox;
    private final JTabbedPane mainTabPane;

    public TransferenciasViewSwing() {
        setLayout(new BorderLayout(0, 15));
        setBackground(new Color(30, 39, 46));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // 1. Título y Toolbar
        add(createHeader(), BorderLayout.NORTH);

        // 2. Panel de transferencias (VBox en FX)
        transferBox = new JPanel();
        transferBox.setLayout(new BoxLayout(transferBox, BoxLayout.Y_AXIS));
        transferBox.setBackground(new Color(30, 39, 46));

        JScrollPane scroll = new JScrollPane(transferBox);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        // 3. TabPane
        mainTabPane = new JTabbedPane();
        mainTabPane.addTab("🔄 En Curso", scroll);
        mainTabPane.addTab("🔔 Notificaciones", createPlaceholderPanel("Historial de notificaciones"));
        mainTabPane.addTab("📊 Estadísticas", createPlaceholderPanel("Resumen de actividad"));

        add(mainTabPane, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 10));
        header.setOpaque(false);

        JLabel titulo = new JLabel("📁 Panel de Control de Archivos");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 20));
        titulo.setForeground(Color.WHITE);

        // ToolBar (Filtros y buscador)
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolBar.setOpaque(false);
        toolBar.add(new JLabel("🔍") {{ setForeground(Color.WHITE); }});
        toolBar.add(new JTextField(15));
        toolBar.add(new JComboBox<>(new String[]{"Todos", "Enviando", "Recibiendo"}));

        header.add(titulo);
        header.add(toolBar);
        return header;
    }

    private JPanel createPlaceholderPanel(String text) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(35, 45, 50));
        p.add(new JLabel(text) {{ setForeground(Color.GRAY); }});
        return p;
    }

    @Override
    public void addTransference(String mode, Transferencia transferencia, TransferManager transferManager) {
        TransferenceControlPanelSwing panel = new TransferenceControlPanelSwing(mode, transferencia, transferManager);
        transferMap.put(transferencia.getId(), panel);

        SwingUtilities.invokeLater(() -> {
            transferBox.add(panel);
            transferBox.add(Box.createVerticalStrut(12));
            transferBox.revalidate();
            transferBox.repaint();
        });
    }

    @Override
    public void updateTransference(FileTransferState state, String id, int progress) {
        TransferenceControlPanelSwing panel = transferMap.get(id);
        if (panel != null) {
            panel.updateProgressBar(progress);
        }
    }

    @Override
    public void endTransference(String mode, String id) {
        SwingUtilities.invokeLater(() -> {
            TransferenceControlPanelSwing panel = transferMap.remove(id);
            if (panel != null) {
                transferBox.remove(panel);
                transferBox.revalidate();
                transferBox.repaint();
            }
        });
    }

    @Override
    public void notifyException(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE)
        );
    }

    @Override
    public void updateTransferenceFull(TransferProgress progress) {
        // 1. Buscamos el panel específico usando el ID de la transferencia
        TransferenceControlPanelSwing panel = transferMap.get(progress.id());

        if (panel != null) {
            // 2. Ejecutamos la actualización en el Event Dispatch Thread (EDT) de Swing
            SwingUtilities.invokeLater(() -> {
                panel.updateProgressBar(progress.percentage());

                // 3. Pasamos las métricas nuevas al panel
                panel.updateMetrics(progress.speedMBs(), progress.eta());
            });
        }
    }
}
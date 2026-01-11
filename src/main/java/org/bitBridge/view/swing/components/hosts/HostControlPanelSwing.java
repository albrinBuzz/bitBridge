package org.bitBridge.view.swing.components.hosts;



import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.shared.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class HostControlPanelSwing extends JPanel {

    // Usamos tipos definidos para evitar "Strings mágicos"
    private enum TransferType { ARCHIVO, CARPETA }

    private final Client client;
    private final ClientInfo host;

    private JLabel hostNameLabel;
    private JComboBox<TransferType> selectionComboBox;
    private JButton sendFileButton;

    public HostControlPanelSwing(ClientInfo host, Client cliente) {
        // Esto inicializa el Toolkit de JavaFX sin mostrar nada
        //new javafx.embed.swing.JFXPanel();

        this.client = cliente;
        this.host = host;
        setupLayout();
        initGUI();
    }

    private void setupLayout() {
        setLayout(new BorderLayout());
        setBackground(new Color(45, 52, 54));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        setPreferredSize(new Dimension(0, 65));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 191, 255, 100), 1),
                new EmptyBorder(5, 15, 5, 15)
        ));
    }

    private void initGUI() {
        // --- IZQUIERDA: Info ---
        JPanel infoBox = new JPanel();
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        infoBox.setOpaque(false);

        hostNameLabel = new JLabel(host.getNick());
        hostNameLabel.setForeground(Color.WHITE);
        hostNameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        infoBox.add(Box.createVerticalGlue());
        infoBox.add(hostNameLabel);
        infoBox.add(createStatusPanel());
        infoBox.add(Box.createVerticalGlue());

        // --- DERECHA: Acciones ---
        JPanel actionsBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 15));
        actionsBox.setOpaque(false);

        // ComboBox agnóstico usando el Enum
        selectionComboBox = new JComboBox<>(TransferType.values());
        selectionComboBox.setPreferredSize(new Dimension(100, 25));

        sendFileButton = new JButton("📤 Enviar");
        sendFileButton.addActionListener(e -> handleAction());

        actionsBox.add(selectionComboBox);
        actionsBox.add(sendFileButton);

        add(infoBox, BorderLayout.WEST);
        add(actionsBox, BorderLayout.EAST);
    }

    private void handleAction() {
        TransferType type = (TransferType) selectionComboBox.getSelectedItem();

        // 1. Delegar la selección de archivo al sistema nativo
        File file = selectFileNative(type == TransferType.ARCHIVO);

        if (file != null) {
            // 2. Ejecutar a través de un SwingWorker (No congela UI)
            executeTransfer(file, type);
        }
    }

    private File selectFileNative(boolean isFile) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(isFile ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }
    /*private File selectFileNative(boolean isFile) {
        final File[] selectedFile = new File[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        javafx.application.Platform.runLater(() -> {
            try {
                if (isFile) {
                    javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                    fc.setTitle("Seleccionar Archivo");
                    selectedFile[0] = fc.showOpenDialog(null); // Usamos null aquí
                } else {
                    javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
                    dc.setTitle("Seleccionar Carpeta");
                    selectedFile[0] = dc.showDialog(null);
                }
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(); // Swing espera a que el usuario elija en la ventana de FX
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return selectedFile[0];
    }*/

    private void executeTransfer(File file, TransferType type) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // AGNOSTICISMO: El cliente debería tener métodos que acepten solo el File y el destino
                // sin que la UI sepa de IPs o puertos internos.
                if (type == TransferType.ARCHIVO) {
                    client.sendFileToHost(host, file);
                } else {
                    client.sendDirectoryToHost(host, file);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    Logger.logInfo("Transferencia exitosa a " + host.getNick());
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(HostControlPanelSwing.this,
                            "Error: " + e.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private JPanel createStatusPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.setOpaque(false);
        // ... (Tu lógica del círculo indicador)
        return p;
    }
}
package org.bitBridge.view.swing.components.transfers;



import org.bitBridge.Client.TransferManager;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.models.TransferProgress;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.*;

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

    /*@Override
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
        }else {
            Logger.logInfo("No se encontro la transferencia ");
        }
    }*/

    @Override
    public void updateTransferenceFull(TransferProgress progress) {
        TransferenceControlPanelSwing panel = transferMap.get(progress.id());
        if (panel != null) {
            // NO usar invokeLater aquí, deja que el Panel decida cuándo
            // hacerlo mediante su propia lógica de throttling explicada arriba.
            panel.updateProgressBar(progress.percentage());
            panel.updateMetrics(progress.speedMBs(), progress.eta());
        }
    }

    @Override
    public boolean notifyTranference(FileHandshakeCommunication handshakeCommunication) {
        // Usamos una variable atómica o un array para obtener el resultado desde el hilo de la GUI
        final boolean[] accepted = {false};
        var com=handshakeCommunication.getFileInfo();

        try {
            // Ejecutamos y esperamos la respuesta del usuario en el Event Dispatch Thread
            SwingUtilities.invokeAndWait(() -> {
                String mensaje = String.format(
                        "<html><body style='width: 250px; font-family: sans-serif;'>" +
                                "<h3 style='color: #00BFFF;'>📥 Solicitud de Archivo</h3>" +
                                "<p><b>Origen:</b> %s</p>" +
                                "<p><b>Archivo:</b> <font color='#F1C40F'>%s</font></p>" +
                                "<p><b>Tamaño:</b> %s</p>" +
                                "<hr><p>¿Deseas recibir este archivo?</p>" +
                                "</body></html>",
                        com.getRecipient(),
                        com.getName(),
                        formatSize(com.getSize())
                );

                int option = JOptionPane.showConfirmDialog(
                        null, // O el frame principal si lo tienes referenciado
                        mensaje,
                        "BitBridge | Transferencia Entrante",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                accepted[0] = (option == JOptionPane.YES_OPTION);
            });
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return accepted[0];
    }

    @Override
    public void notifyTranference(FileHandshakeAction action) {
        SwingUtilities.invokeLater(() -> {
            // Usamos el switch de Java 21 para manejar cada caso del Enum
            switch (action) {
                case SEND_REQUEST -> {
                    // Podrías mostrar un pequeño indicador de "Enviando solicitud..."
                    Logger.logInfo("Solicitud enviada al receptor.");
                }

                case DECLINE_REQUEST -> {
                    showAlert("Transferencia Rechazada", "El destinatario ha rechazado el archivo.");
                    // Si tienes un diálogo de progreso abierto, este es el momento de cerrarlo
                }

                case START_TRANSFER -> {
                    Logger.logInfo("¡Transferencia autorizada! Iniciando flujo de bytes.");
                }

                // --- MANEJO DE ERRORES CRÍTICOS ---
                case ERROR_DISCO_LLENO -> {
                    showAlert("Error de Almacenamiento", "No hay espacio suficiente en el disco del receptor.");
                }

                case ERROR_ARCHIVO_GRANDE -> {
                    showAlert("Límite Excedido", "El archivo es demasiado grande para ser procesado por el servidor.");
                }

                case ERROR_TIPO_PROHIBIDO -> {
                    showAlert("Seguridad", "El tipo de archivo seleccionado no está permitido por el servidor.");
                }

                case ERROR_TIMEOUT -> {
                    showAlert("Tiempo Agotado", "El receptor no respondió a tiempo. Inténtalo de nuevo.");
                }

                case SERVER_BUSY -> {
                    showAlert("Servidor Saturado", "El servidor está procesando demasiadas solicitudes. Espera un momento.");
                }

                case TRANSFER_DONE -> {
                    // Puedes mostrar un "Pop-up" de éxito o un sonido
                    Logger.logInfo("Proceso finalizado con éxito.");
                }

                default -> Logger.logWarn("Acción de transferencia no manejada en UI: " + action);
            }
        });
    }

    // Método auxiliar para alertas (estilo BitBridge)
    private void showAlert(String titulo, String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, titulo, JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Utilidad para formatear el tamaño de bytes a algo legible
     */
    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
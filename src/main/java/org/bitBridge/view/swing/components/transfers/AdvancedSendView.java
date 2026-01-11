package org.bitBridge.view.swing.components.transfers;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class AdvancedSendView extends JDialog {
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;
    private JComboBox<String> hostSelector;
    private JLabel totalSizeLabel;

    public AdvancedSendView(JFrame parent, List<String> onlineHosts) {
        super(parent, "🚀 Configurar Envío Masivo", true);
        setSize(600, 500);
        setLayout(new BorderLayout(15, 15));
        getContentPane().setBackground(new Color(30, 39, 46));

        // 1. Panel de Selección de Destinatario
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setOpaque(false);
        topPanel.setBorder(new EmptyBorder(15, 15, 0, 15));

        hostSelector = new JComboBox<>(onlineHosts.toArray(new String[0]));
        JLabel lbl = new JLabel("Enviar a:");
        lbl.setForeground(Color.WHITE);
        topPanel.add(lbl, BorderLayout.WEST);
        topPanel.add(hostSelector, BorderLayout.CENTER);

        // 2. Lista de Archivos (El Core)
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setBackground(new Color(45, 52, 54));
        fileList.setForeground(Color.WHITE);

        JScrollPane scroll = new JScrollPane(fileList);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Cola de Envío", 0, 0, null, Color.CYAN));

        // 3. Botones Laterales (Añadir/Quitar)
        JPanel sideButtons = new JPanel(new GridLayout(4, 1, 0, 10));
        sideButtons.setOpaque(false);
        JButton btnAddFile = new JButton("📄 + Archivo");
        JButton btnAddDir = new JButton("📁 + Carpeta");
        JButton btnRemove = new JButton("🗑 Quitar");
        sideButtons.add(btnAddFile);
        sideButtons.add(btnAddDir);
        sideButtons.add(btnRemove);

        // 4. Panel Inferior (Resumen y Acción)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(0, 15, 15, 15));

        totalSizeLabel = new JLabel("Total: 0 archivos seleccionados");
        totalSizeLabel.setForeground(Color.LIGHT_GRAY);

        JButton btnSendAll = new JButton("INICIAR TRANSFERENCIA");
        btnSendAll.setBackground(new Color(46, 204, 113));
        btnSendAll.setForeground(Color.WHITE);
        btnSendAll.setFont(new Font("SansSerif", Font.BOLD, 14));

        bottomPanel.add(totalSizeLabel, BorderLayout.WEST);
        bottomPanel.add(btnSendAll, BorderLayout.EAST);

        // Ensamblaje
        add(topPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(sideButtons, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(parent);
    }

}
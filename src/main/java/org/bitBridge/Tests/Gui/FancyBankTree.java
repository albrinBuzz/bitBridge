package org.bitBridge.Tests.Gui;

import com.formdev.flatlaf.FlatDarkLaf; // Opcional: Librería de Look & Feel
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class FancyBankTree extends JFrame {

    public FancyBankTree() {
        // 1. Configurar Look & Feel Moderno
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            System.out.println("FlatLaf no encontrado, usando estilo default.");
        }

        setTitle("Core Banking OS - v2.5");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Panel Principal con Gradiente
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 2. Construir Jerarquía de Datos
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("🏦 Sistema Central");

        DefaultMutableTreeNode nodeNodes = new DefaultMutableTreeNode("🖥️ Nodos de Cómputo");
        nodeNodes.add(new DefaultMutableTreeNode("Servidor Alpha (Online)"));
        nodeNodes.add(new DefaultMutableTreeNode("Servidor Beta (Stress Test)"));

        DefaultMutableTreeNode nodeCuentas = new DefaultMutableTreeNode("📁 Gestión de Activos");
        DefaultMutableTreeNode catVip = new DefaultMutableTreeNode("Clientes VIP");
        catVip.add(new DefaultMutableTreeNode("💼 Portafolio Inversiones"));
        catVip.add(new DefaultMutableTreeNode("💎 Fideicomisos"));
        nodeCuentas.add(catVip);
        nodeCuentas.add(new DefaultMutableTreeNode("📊 Reporte Trimestral.xlsx"));

        root.add(nodeNodes);
        root.add(nodeCuentas);

        // 3. Crear el JTree y personalizarlo
        JTree tree = new JTree(root);
        tree.setBackground(new Color(30, 30, 30)); // Fondo oscuro personalizado
        tree.setRowHeight(30); // Más espacio entre filas
        tree.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // 4. EL RENDERER (Lo que lo hace 'Fancy')
        tree.setCellRenderer(new FancyTreeRenderer());

        // 5. ScrollPane con bordes redondeados o limpios
        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));

        // Panel lateral de info (simulando dashboard)
        JPanel infoPanel = new JPanel();
        infoPanel.setPreferredSize(new Dimension(250, 0));
        infoPanel.setBackground(new Color(45, 45, 45));
        infoPanel.add(new JLabel("<html><body style='padding:20px; color:white;'>"
                + "<h2>Status</h2><hr>"
                + "CPU: 12%<br>RAM: 4.2GB<br>"
                + "Locks Activos: 0<br><br>"
                + "<b>Consola:</b><br>Esperando comando...</body></html>"));

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(infoPanel, BorderLayout.EAST);

        add(mainPanel);
    }

    // Clase interna para iconos y colores personalizados
    class FancyTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            String text = value.toString();

            // Personalizar iconos según el contenido del texto
            if (text.contains("Sistema")) setIcon(UIManager.getIcon("FileView.computerIcon"));
            else if (text.contains("Servidor")) setIcon(UIManager.getIcon("FileView.hardDriveIcon"));
            else if (text.contains("Reporte")) setIcon(UIManager.getIcon("FileView.fileIcon"));
            else if (leaf) setIcon(UIManager.getIcon("Tree.leafIcon"));

            // Colores de selección
            if (sel) {
                setForeground(new Color(82, 158, 202)); // Azul neón al seleccionar
                setBackgroundSelectionColor(new Color(50, 50, 50));
            } else {
                setForeground(Color.WHITE);
            }

            setBorderSelectionColor(null); // Quitar borde feo al seleccionar
            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FancyBankTree().setVisible(true));
    }
}
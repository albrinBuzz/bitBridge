package org.bitBridge.view.swing.components.explorer;

import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;
import org.bitBridge.shared.Logger;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Inspector Lateral de Atributos de archivos para bitBridge Pro.
 * Diseñado con enfoque en administración Linux y auditoría Rsync.
 */
public class FileInspectorPanel extends JPanel {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_COLOR = new Color(25, 25, 25);
    private static final Color TERMINAL_BG = new Color(40, 44, 52);

    private JLabel lblIcon, lblName;
    private JLabel lblTypeVal, lblSizeVal, lblOwnerVal, lblModVal, lblPermsVal, lblPathVal;
    private JButton btnPullAction, btnTerminal;
    private NodoDirectorio currentNodo;

    public FileInspectorPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(20, 20, 20, 20));
        setBackground(BG_COLOR);
        setMinimumSize(new Dimension(320, 0));
        setPreferredSize(new Dimension(350, 0));

        // --- ENCABEZADO ---
        lblIcon = new JLabel("🗄️");
        lblIcon.setFont(new Font("Serif", Font.PLAIN, 72));
        lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblName = new JLabel("Seleccione un archivo");
        lblName.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblName.setForeground(NICOTINE_ORANGE);
        lblName.setAlignmentX(Component.CENTER_ALIGNMENT);

        add(lblIcon);
        add(Box.createVerticalStrut(10));
        add(lblName);
        add(Box.createVerticalStrut(25));

        // --- METADATOS ---
        add(createSectionTitle("METADATOS DEL ASSET"));

        JPanel pnlProps = new JPanel();
        pnlProps.setLayout(new BoxLayout(pnlProps, BoxLayout.Y_AXIS));
        pnlProps.setOpaque(false);
        pnlProps.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblTypeVal = new JLabel("-");
        lblSizeVal = new JLabel("-");
        lblOwnerVal = new JLabel("-");
        lblModVal = new JLabel("-");
        lblPermsVal = new JLabel("-");
        lblPathVal = new JLabel("-");

        // Fila cruzada con la info real del sistema operativo
        pnlProps.add(createPropRow("📂 Tipo", lblTypeVal));
        pnlProps.add(createPropRow("⚖️ Tamaño", lblSizeVal));
        pnlProps.add(createPropRow("👤 Dueño", lblOwnerVal));
        pnlProps.add(createPropRow("🔒 Permisos", lblPermsVal));
        pnlProps.add(createPropRow("📅 Modificado", lblModVal));
        pnlProps.add(createPropRow("📍 Ubicación", lblPathVal));

        add(pnlProps);
        add(Box.createVerticalStrut(15));
        add(Box.createVerticalGlue());

        // --- ACCIONES CORE ---
        btnTerminal = new JButton("💻 Abrir en Terminal Remota");
        stylePrimaryButton(btnTerminal, TERMINAL_BG, Color.CYAN);
        btnTerminal.setFont(new Font("Monospaced", Font.BOLD, 12));
        btnTerminal.setEnabled(false);
        btnTerminal.addActionListener(e -> {
            if (currentNodo != null) abrirTerminalRemota(currentNodo.getRutaString());
        });
        add(btnTerminal);

        add(Box.createVerticalStrut(8));

        btnPullAction = new JButton("📥 INICIAR DOWNLOAD PULL");
        stylePrimaryButton(btnPullAction, NICOTINE_ORANGE, Color.BLACK);
        add(btnPullAction);

        add(Box.createVerticalStrut(10));
    }

    public void configurarModoBoton(boolean esLocal) {
        if (esLocal) {
            btnPullAction.setText("📤 INICIAR RESPALDO PUSH");
            btnPullAction.setBackground(new Color(40, 167, 69));
            btnPullAction.setForeground(Color.WHITE);
        } else {
            btnPullAction.setText("📥 INICIAR DOWNLOAD PULL");
            btnPullAction.setBackground(NICOTINE_ORANGE);
            btnPullAction.setForeground(Color.BLACK);
        }
    }

    public void updateInfo(NodoDirectorio nodo) {
        this.currentNodo = nodo;
        if (nodo == null) return;
        Logger.logInfo(nodo.toString());

        // Limitar longitud del nombre para que no rompa el Layout lateral
        String truncName = nodo.getNombre();
        if (truncName.length() > 28) truncName = truncName.substring(0, 25) + "...";
        lblName.setText(truncName);
        lblName.setToolTipText(nodo.getNombre());

        lblIcon.setText(nodo.esDirectorio() ? "📁" : "📄");
        lblSizeVal.setText(nodo.getTamañoFormateado());
        lblTypeVal.setText(nodo.esDirectorio() ? "Directorio" : (nodo.getExtension().isEmpty() ? "Archivo" : nodo.getExtension()));

        // Inyección de variables dinámicas reales desde el objeto serializado
        lblOwnerVal.setText(nodo.getPropietarioString());
        lblPermsVal.setText(nodo.getPermisosPosix());
        lblModVal.setText(nodo.getFechaModificacion());

        // Manejo elegante de strings de ruta largos
        String shortPath = nodo.getRutaString();
        if (shortPath.length() > 30) shortPath = "..." + shortPath.substring(shortPath.length() - 27);
        lblPathVal.setText(shortPath);
        lblPathVal.setToolTipText(nodo.getRutaString());

        btnTerminal.setEnabled(true);
        refreshUI();
    }

    public void updateInfo(List<NodoDirectorio> seleccion) {
        if (seleccion == null || seleccion.isEmpty()) {
            resetInfo();
            return;
        }
        if (seleccion.size() == 1) {
            updateInfo(seleccion.get(0));
            return;
        }

        this.currentNodo = seleccion.get(0);
        lblIcon.setText("📚");
        lblName.setText(seleccion.size() + " elementos seleccionados");
        lblTypeVal.setText("Mix / Selección");
        lblSizeVal.setText("Variado");
        lblOwnerVal.setText("-");
        lblPermsVal.setText("-");
        lblModVal.setText("-");
        lblPathVal.setText("Múltiples rutas de red");

        btnPullAction.setEnabled(true);
        btnTerminal.setEnabled(false);

        refreshUI();
    }

    public void clear() { resetInfo(); }

    private void resetInfo() {
        lblName.setText("Seleccione un archivo");
        lblName.setToolTipText(null);
        lblIcon.setText("🗄️");
        lblSizeVal.setText("-");
        lblTypeVal.setText("-");
        lblOwnerVal.setText("-");
        lblPermsVal.setText("-");
        lblModVal.setText("-");
        lblPathVal.setText("-");
        lblPathVal.setToolTipText(null);
        btnTerminal.setEnabled(false);
        refreshUI();
    }

    private void refreshUI() {
        revalidate();
        repaint();
    }

    private void abrirTerminalRemota(String ruta) {
        Logger.logInfo("Solicitando apertura de terminal en: " + ruta);
        JOptionPane.showMessageDialog(this,
                "Iniciando sesión de consola interactiva en:\n" + ruta,
                "BitBridge SSH Core Terminal",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void stylePrimaryButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(bg.darker()));
    }

    private JPanel createSectionTitle(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel t = new JLabel(text);
        t.setFont(new Font("SansSerif", Font.BOLD, 11));
        t.setForeground(new Color(114, 118, 125)); // Gris suave FlatLaf
        p.add(t, BorderLayout.NORTH);
        p.add(new JSeparator(), BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(0, 0, 15, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JPanel createPropRow(String key, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel kl = new JLabel(key);
        kl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        kl.setForeground(new Color(160, 160, 160));
        valueLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        valueLabel.setForeground(Color.WHITE);
        row.add(kl, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        row.setBorder(new EmptyBorder(0, 0, 6, 0));
        return row;
    }

    public JButton getBtnPullAction() { return this.btnPullAction; }
}
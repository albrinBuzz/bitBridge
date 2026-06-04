package org.bitBridge.view.swing.components.explorer;

import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;
import org.bitBridge.shared.Logger;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Inspector Lateral de Atributos de archivos para bitBridge Pro.
 * Expone de manera limpia su botón operativo principal para la sincronización cruzada.
 */
public class FileInspectorPanel extends JPanel {

    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);
    private static final Color BG_COLOR = new Color(25, 25, 25);
    private static final Color TERMINAL_BG = new Color(40, 44, 52);

    private JLabel lblIcon, lblName, lblTypeVal, lblSizeVal, lblOwnerVal, lblHashVal;
    private JButton btnPullAction, btnTerminal;
    private NodoDirectorio currentNodo;

    public FileInspectorPanel() {
        initComponents();
        setupActions();
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(20, 20, 20, 20));
        setBackground(BG_COLOR);
        setMinimumSize(new Dimension(320, 0));
        setPreferredSize(new Dimension(350, 0));

        // --- ENCABEZADO ---
        lblIcon = new JLabel("🗄️");
        lblIcon.setFont(new Font("Serif", Font.PLAIN, 80));
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
        lblHashVal = new JLabel("-");

        pnlProps.add(createPropRow("📂 Tipo", lblTypeVal));
        pnlProps.add(createPropRow("⚖️ Tamaño", lblSizeVal));
        pnlProps.add(createPropRow("👤 Dueño", lblOwnerVal));
        pnlProps.add(createPropRow("🔑 SHA-1", lblHashVal));

        add(pnlProps);
        add(Box.createVerticalStrut(20));

        // --- ACCIONES SECUNDARIAS ---
        JButton btnCopyHash = new JButton("📋 Copiar Hash");
        btnCopyHash.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(btnCopyHash);

        add(Box.createVerticalStrut(10));
        add(Box.createVerticalGlue());

        // --- BOTÓN PRINCIPAL DE ACCIÓN (MUTABLE) ---
        btnPullAction = new JButton("📥 INICIAR DOWNLOAD PULL");
        stylePrimaryButton(btnPullAction, NICOTINE_ORANGE, Color.BLACK);
        add(btnPullAction);

        add(Box.createVerticalStrut(10));
    }

    private void setupActions() {
        btnTerminal = new JButton("💻 Abrir en Terminal Remota");
        stylePrimaryButton(btnTerminal, TERMINAL_BG, Color.CYAN);
        btnTerminal.setFont(new Font("Monospaced", Font.BOLD, 12));
        btnTerminal.setToolTipText("Abrir consola SSH en esta ubicación");

        btnTerminal.addActionListener(e -> {
            if (currentNodo != null) {
                abrirTerminalRemota(currentNodo.getRutaString());
            }
        });
        add(btnTerminal);
    }

    public void configurarModoBoton(boolean esLocal) {
        if (esLocal) {
            btnPullAction.setText("📤 INICIAR RESPALDO PUSH");
            btnPullAction.setBackground(new Color(40, 167, 69)); // Verde Éxito
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

        lblName.setText(nodo.getNombre());
        lblIcon.setText(nodo.esDirectorio() ? "📁" : "📄");
        lblSizeVal.setText(nodo.getTamañoFormateado());
        lblTypeVal.setText(nodo.esDirectorio() ? "Directorio" : (nodo.getExtension().isEmpty() ? "Archivo" : nodo.getExtension()));
        lblOwnerVal.setText("Remote Node");
        lblHashVal.setText("N/A");
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
        lblTypeVal.setText("Mix");
        lblSizeVal.setText("Variado");
        btnPullAction.setEnabled(true);
        btnPullAction.setText("📦 DOWNLOAD SELECCIÓN (" + seleccion.size() + ")");

        refreshUI();
    }

    public void clear() {
        resetInfo();
    }

    private void resetInfo() {
        lblName.setText("Seleccione un archivo");
        lblIcon.setText("🗄️");
        lblSizeVal.setText("-");
        lblTypeVal.setText("-");
        lblOwnerVal.setText("-");
        lblHashVal.setText("-");
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
                "Iniciando sesión de consola en:\n" + ruta,
                "BitBridge Terminal",
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
        t.setFont(new Font("SansSerif", Font.BOLD, 10));
        t.setForeground(Color.DARK_GRAY);
        p.add(t, BorderLayout.NORTH);
        p.add(new JSeparator(), BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(0, 0, 10, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JPanel createPropRow(String key, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel kl = new JLabel(key);
        kl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        kl.setForeground(new Color(150, 150, 150));
        valueLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        valueLabel.setForeground(Color.WHITE);
        row.add(kl, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        row.setBorder(new EmptyBorder(0, 0, 5, 0));
        return row;
    }

    public JButton getBtnPullAction() { return this.btnPullAction; }
}
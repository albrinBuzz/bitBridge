package org.bitBridge.view.swing.components.explorer;

import org.bitBridge.shared.core.comunication.NodoDirectorio;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class BreadcrumbBar extends JPanel {
    private final Consumer<NodoDirectorio> onNavigate;
    private static final Color ORANGE = new Color(255, 165, 0);

    public BreadcrumbBar(Consumer<NodoDirectorio> onNavigate) {
        this.onNavigate = onNavigate;
        setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        setBackground(new Color(30, 30, 30));
    }

    public void updatePath(NodoDirectorio raiz, NodoDirectorio actual) {
        removeAll();
        if (raiz == null || actual == null) return;

        // Botón Root
        add(createCrumb("🏠 Root", raiz, actual.getRutaString().equals(raiz.getRutaString())));

        String rString = raiz.getRutaString();
        String aString = actual.getRutaString();

        if (aString.length() > rString.length()) {
            String relativa = aString.substring(rString.length());
            if (relativa.startsWith("/") || relativa.startsWith("\\")) relativa = relativa.substring(1);

            String[] partes = relativa.split("[/\\\\]");
            String acumulado = rString;

            for (String parte : partes) {
                if (parte.isEmpty()) continue;
                add(new JLabel(">"));
                acumulado += File.separator + parte;

                boolean esElUltimo = aString.equals(acumulado);
                add(createCrumb(parte, new NodoDirectorio(Paths.get(acumulado)), esElUltimo));
            }
        }
        revalidate();
        repaint();
    }

    private JButton createCrumb(String texto, NodoDirectorio destino, boolean resaltado) {
        JButton btn = new JButton(texto);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setForeground(resaltado ? ORANGE : Color.GRAY);
        btn.setFont(new Font("SansSerif", resaltado ? Font.BOLD : Font.PLAIN, 12));

        btn.addActionListener(e -> onNavigate.accept(destino));
        return btn;
    }
}
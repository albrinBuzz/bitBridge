package org.bitBridge.Tests.Gui.nicotine;



import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;
import java.nio.file.*;

/**
 * Vista autocontenida: Explorador de archivos con Carga Perezosa (NIO)
 */
public class ExploradorBitBridge extends JFrame {

    private JTree arbol;
    private DefaultTreeModel modelo;

    public ExploradorBitBridge(String rutaInicial) {
        FlatOneDarkIJTheme.setup();

        setTitle("BitBridge File Explorer - NIO Engine");
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 1. Inicializar Datos Raíz
        Path rutaRaiz = Paths.get(rutaInicial);
        NodoDirectorio raizDatos = new NodoDirectorio(rutaRaiz);

        // 2. Configurar JTree y Modelo
        DefaultMutableTreeNode nodoVisualRaiz = crearNodoVisual(raizDatos);
        modelo = new DefaultTreeModel(nodoVisualRaiz);
        arbol = new JTree(modelo);

        // --- AQUÍ ESTÁ EL CAMBIO: Forzar carga del primer nivel ---
        // Sin esto, verás la carpeta raíz pero tendrás que expandirla para ver el contenido.
        cargarHijosVisuales(nodoVisualRaiz);
        // Opcional: Expandir la raíz automáticamente para que se vea el contenido al abrir
        arbol.expandPath(new TreePath(nodoVisualRaiz.getPath()));

        // 3. Renderer y 4. Eventos (Igual que antes)
        configurarRenderizado();
        configurarEventosExpansion();

        add(new JScrollPane(arbol), BorderLayout.CENTER);

        JLabel lblStatus = new JLabel(" Directorio: " + rutaInicial);
        lblStatus.setBorder(BorderFactory.createEtchedBorder());
        add(lblStatus, BorderLayout.SOUTH);
    }

    private void cargarHijosVisuales(DefaultMutableTreeNode nodoVisual) {
        Object userObj = nodoVisual.getUserObject();
        if (userObj instanceof NodoDirectorio) {
            NodoDirectorio datos = (NodoDirectorio) userObj;

            // Si es directorio y tiene el "dummy" de carga, traemos la info real
            if (datos.esDirectorio() && (nodoVisual.getChildCount() == 0 ||
                    (nodoVisual.getChildCount() > 0 && nodoVisual.getChildAt(0).toString().equals("Cargando...")))) {

                nodoVisual.removeAllChildren(); // Quitamos el "Cargando..."
                for (NodoDirectorio hijo : datos.getHijos()) {
                    nodoVisual.add(crearNodoVisual(hijo));
                }
                modelo.nodeStructureChanged(nodoVisual);
            }
        }
    }
    private void configurarRenderizado() {
        arbol.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean exp, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, hasFocus);

                if (value instanceof DefaultMutableTreeNode) {
                    Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                    if (userObj instanceof NodoDirectorio) {
                        NodoDirectorio nodo = (NodoDirectorio) userObj;
                        setText(nodo.getNombre());
                        setIcon(nodo.esDirectorio() ?
                                UIManager.getIcon("FileView.directoryIcon") :
                                UIManager.getIcon("FileView.fileIcon"));
                    }
                }
                return this;
            }
        });
    }

    private void configurarEventosExpansion() {
        arbol.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode nodoVisual = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                Object userObj = nodoVisual.getUserObject();

                if (userObj instanceof NodoDirectorio) {
                    NodoDirectorio datos = (NodoDirectorio) userObj;

                    // Si tiene el nodo "falso", cargamos de verdad
                    if (datos.esDirectorio() && nodoVisual.getChildCount() > 0 &&
                            nodoVisual.getChildAt(0).toString().equals("Cargando...")) {

                        nodoVisual.removeAllChildren();
                        for (NodoDirectorio hijo : datos.getHijos()) {
                            nodoVisual.add(crearNodoVisual(hijo));
                        }
                        modelo.nodeStructureChanged(nodoVisual);
                    }
                }
            }
            @Override public void treeWillCollapse(TreeExpansionEvent event) {}
        });
    }

    private DefaultMutableTreeNode crearNodoVisual(NodoDirectorio datos) {
        DefaultMutableTreeNode visual = new DefaultMutableTreeNode(datos);
        if (datos.esDirectorio()) {
            visual.add(new DefaultMutableTreeNode("Cargando..."));
        }
        return visual;
    }



    // --- MÉTODO DE EJECUCIÓN ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {}

            // Puedes cambiar esta ruta por la de tu carpeta de descargas
            String rutaDefecto = System.getProperty("user.home");
            Path rutaBase=Path.of("/home/cris/Descargas");
            new ExploradorBitBridge("/home/cris/Descargas").setVisible(true);
        });
    }
}
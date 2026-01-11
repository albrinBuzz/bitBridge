package org.bitBridge;






import org.bitBridge.view.swing.MainViewSwing;

import javax.swing.*;

public class Launcher {
    public static void main(String[] args) {
        // Esto evita que Java verifique los módulos de JavaFX al arrancar

        //Main.main(args);

        //FileChannel.transferTo();

        SwingUtilities.invokeLater(() -> new MainViewSwing().setVisible(true));
    }
}
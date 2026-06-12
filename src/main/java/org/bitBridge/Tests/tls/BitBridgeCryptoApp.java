package org.bitBridge.Tests.tls;


import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import org.bitBridge.Tests.tls.SecurityGeneratorSwingView;
import org.bitBridge.shared.core.secure.CryptoGeneratorController;

import javax.swing.*;

public class BitBridgeCryptoApp {

    public static void main(String[] args) {
        // 1. Instanciar el controlador único compartido
        CryptoGeneratorController controller = new CryptoGeneratorController();

        // 2. Analizar parámetros de lanzamiento por consola
        boolean lanzarModoCli = false;

        /*for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg)) {
                lanzarModoCli = true;
                break;
            }
        }*/

        if (lanzarModoCli) {
            // Inicializar la interfaz por línea de comandos
            SecurityGeneratorCliView cliView = new SecurityGeneratorCliView(controller);
            cliView.iniciarModoInteractivo();
        } else {
            // Inicializar interfaz visual enriquecida moderna (FlatLaf)
            SwingUtilities.invokeLater(() -> {
                FlatOneDarkIJTheme.setup();
                SecurityGeneratorSwingView swingView = new SecurityGeneratorSwingView(controller);
                swingView.setVisible(true);
            });
        }
    }
}
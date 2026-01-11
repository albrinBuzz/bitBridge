package org.bitBridge.Client;

import java.awt.*;
import java.io.File;

public class UtilidadesCliente {



    public static boolean abrirDirectorioDescargas(String ruta) {
        File carpeta = new File(ruta);
        if (!carpeta.exists() || !carpeta.isDirectory()) return false;

        try {
            // Intento 1: Standard AWT Desktop
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(carpeta);
                return true;
            }

            // Intento 2: Comandos de Sistema (Fallback)
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("explorer.exe \"" + ruta + "\"");
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec("open \"" + ruta + "\"");
            } else {
                Runtime.getRuntime().exec("xdg-open \"" + ruta + "\"");
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}

package org.bitBridge.Tests.rsync;
//import com.github.perlundq.yajsync.ui.Main;

public class SincronizadorExcel {
    public static void main(String[] args) {
        // Apuntamos DIRECTAMENTE al archivo que queremos mover
        String archivoOrigen = "/home/cris/prueba_rsync/origen/horario1.xlsx";

        // El destino debe ser la CARPETA donde quieres que caiga, o la ruta completa
        String carpetaDestino = "/home/cris/prueba_rsync/destino/";

        String[] argumentos = {
                "client",
                "-v",      // Verbose (para ver el progreso)
                "-t",      // preserve times (importante para Excel)
                archivoOrigen,
                carpetaDestino
        };

        try {
            System.out.println("Sincronizando archivo específico: " + archivoOrigen);

            // Llamada al JAR (Caja Negra)
            Main.main(argumentos);

            System.out.println("--- Proceso completado ---");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
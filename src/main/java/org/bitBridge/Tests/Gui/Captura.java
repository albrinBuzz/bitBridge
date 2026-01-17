package org.bitBridge.Tests.Gui;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.services.ScreenCaptureService;
import org.bitBridge.server.core.Server;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class Captura {


    public static void main(String[] args) {
        String serverIp = "127.0.0.1";
        int port = 8080;

        try {
            // 1. Iniciar Servidor
            Server servidor = Server.getInstance();
            new Thread(() -> {
                try {
                    servidor.startServer();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            Thread.sleep(100);


            // 3. Emisor
            // IMPORTANTE: Si lanzas esto en la misma PC, el hostname será igual.
            // Tu lógica de servidor añade un "1" al final si el nick se repite.
            Client emisor = new Client();
            emisor.setConexion(serverIp, port);
            Thread.sleep(200);

            // 4. Crear el target apuntando al nick que el servidor asignó al RECEPTOR
            // Según tu log, el primero fue "fedora"
            ClientInfo target = new ClientInfo("127.0.0.1", "fedora", port);

            // 5. Carpeta de prueba
            File carpeta = new File("/home/cris/baseDatos/");
            //File carpetaArch = new File("/home/cris/baseDatos/oracle/ddlRHH.sql");
            //File carpetaArch = new File("/home/cris/ldr/el-senor-de-los-anillos-la-comunidad-del-anillo-edicion-extendida-1.0.mp4");
            File carpetaArch = new File("/home/cris/java/javafx/proyectos/FileTalk/target/FileTalk-Desktop.jar");
            //File carpetaArch = new File("/home/cris/baseDatos/guias.sql");

            if (!carpeta.exists()) {
                carpeta.mkdirs();
                new File(carpeta, "test.txt").createNewFile();
            }

            // 6. Ejecutar
            // En tu AutomatedTestRunner.java
            System.out.println("[TEST] Llamando a sendDirectoryToHost...");
            //emisor.sendDirectoryToHost(target, carpeta);
            //emisor.sendFileToHost(target, carpetaArch);
            emisor.sendScreenSnapshot(target);

// IMPORTANTE: No pongas System.exit(0) inmediatamente.
// Los hilos del ExecutorService son hilos "Daemon" o se cortan si el main muere.
            //Thread.sleep(20); // Dale 20 segundos para ver los logs

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

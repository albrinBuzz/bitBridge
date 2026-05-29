package org.bitBridge.Tests;



import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.server.core.Server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutomatedTestRunner {

    public static void main(String[] args) {
        String serverIp = "127.0.0.1";
        int port = 8080;

        try {
            // 1. Iniciar Servidor
            Server servidor = Server.getInstance();
            new Thread(servidor::startServer).start();
            //Thread.sleep(100);


            // 3. Emisor
            // IMPORTANTE: Si lanzas esto en la misma PC, el hostname será igual.
            // Tu lógica de servidor añade un "1" al final si el nick se repite.
            Client emisor = new Client();
            emisor.setConexion(serverIp, port);
            Thread.sleep(200);

            // 4. Crear el target apuntando al nick que el servidor asignó al RECEPTOR
            // Según tu log, el primero fue "fedora"
            /*ClientInfo target = new ClientInfo("127.0.0.1", "fedora", port);

            // 5. Carpeta de prueba
            //File carpeta = new File("/home/cris/baseDatos/");
            File carpeta = new File("/home/cris/recuerdos/");

            //File carpetaArch = new File("/home/cris/baseDatos/oracle/ddlRHH.sql");
            File carpetaArch = new File("/home/cris/ldr/el-senor-de-los-anillos-la-comunidad-del-anillo-edicion-extendida-1.0.mp4");
            //File carpetaArch = new File("/home/cris/memoria.c");
            //File carpetaArch = new File("/home/cris/baseDatos/guias.sql");

            if (!carpeta.exists()) {
                carpeta.mkdirs();
                new File(carpeta, "test.txt").createNewFile();
            }

            // 6. Ejecutar
            // En tu AutomatedTestRunner.java
            System.out.println("[TEST] Llamando a sendDirectoryToHost...");
            //emisor.sendDirectoryToHost(target, carpeta);
            // ... (paso 6)
            System.out.println("[TEST] Llamando a sendFileToHost...");
            //emisor.sendFileToHost(target, carpetaArch);
            emisor.sendDirectoryToHost(target,carpeta);

            // ESPERA CRÍTICA: Dale 1 o 2 segundos para que la red y el disco terminen
            System.out.println("[TEST] Esperando a que termine la transferencia...");
            Thread.sleep(2000);*/

            /*verificarIntegridad(Path.of("/home/cris/ldr/el-senor-de-los-anillos-la-comunidad-del-anillo-edicion-extendida-1.0.mp4"),
                    Path.of("/home/cris/Filetalk/el-senor-de-los-anillos-la-comunidad-del-anillo-edicion-extendida-1.0.mp4"));*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void verificarIntegridad(Path original, Path recibido) throws IOException, InterruptedException {
        int intentos = 0;
        while (!Files.exists(recibido) && intentos < 5) {
            Thread.sleep(1000); // Esperar un segundo si el archivo aún no aparece
            intentos++;
        }

        if (!Files.exists(recibido)) {
            System.err.println("❌ ERROR: El archivo nunca llegó a su destino.");
            return;
        }

        /*byte[] f1 = Files.readAllBytes(original);
        byte[] f2 = Files.readAllBytes(recibido);

        if (java.util.Arrays.equals(f1, f2)) {
            System.out.println("✅ PRUEBA SUPERADA: Los archivos son idénticos.");
        } else {
            System.err.println("❌ PRUEBA FALLIDA: Diferencias encontradas.");
        }*/
    }
}
package org.bitBridge.Tests.rsync.rsyncRed;

public class DemoRsyncCarpeta {
    public static void main(String[] args) {
        // 1. Inicializar el Servidor en un hilo independiente apuntando a su repositorio de destino
        String rutaDestinoServidor = "/home/cris/BitBridge_Shared/";
        new Thread(() -> {
            RsyncServerPush servidor = new RsyncServerPush();
            servidor.iniciar(rutaDestinoServidor);
        }).start();

        // Dar un segundo para que el ServerSocket levante de forma segura
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        // 2. Ejecutar el Cliente pasándole la carpeta origen (con todas sus subcarpetas y fotos)
        /*String rutaCarpetaCliente = "/home/cris/BitBridge_Shared"; // Tu carpeta local con subcarpetas como "Upload"

        RsyncClienteRecursivo cliente = new RsyncClienteRecursivo("192.168.100.192", 9999);
        cliente.sincronizarCarpetaCompleta(rutaCarpetaCliente);*/

        // Código asociado al botón "Sincronizar hacia el Servidor (PUSH)"
        //aqui se simula
        new Thread(() -> {
            String miCarpetaLocal = "/home/cris/BitBridge_Shared/";
            RsyncClientePush clientePush = new RsyncClientePush("192.168.100.192", 9999);

            System.out.println("--- Inicio de sincronización ---");
            long inicio = System.currentTimeMillis();

            clientePush.enviarCarpetaRecursiva(miCarpetaLocal);

            long fin = System.currentTimeMillis();
            double segundos = (fin - inicio) / 1000.0;

            System.out.println("--- Sincronización completada ---");
            System.out.printf("Tiempo total de ejecución: %.3f segundos%n", segundos);

        }).start();
    }
}
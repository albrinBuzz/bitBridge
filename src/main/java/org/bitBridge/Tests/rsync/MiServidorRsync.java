package org.bitBridge.Tests.rsync;


import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MiServidorRsync {

    public void arrancarSinCLI() throws Exception {
        // 1. Configuración de Red Manual
        int puerto = 14415;
        InetAddress direccion = InetAddress.getByName("127.0.0.1");
        int timeout = 0; // Sin timeout

        // 2. Preparar el motor de ejecución (Thread Pool)
        int hilos = Runtime.getRuntime().availableProcessors() * 4;
        ExecutorService executor = Executors.newFixedThreadPool(hilos);

        // 3. Configurar el Motor Rsync (RsyncServer)
        // Usamos el Builder que viste en el código de YajsyncServer
        /*RsyncServer server = new RsyncServer.Builder()
                .isDeferWrite(true) // Configuración manual sin flags de texto
                .build(executor);

        // 4. Configurar el "Manejador de Carpetas" (ModuleProvider)
        // Aquí es donde se definen qué carpetas se comparten
        ModuleProvider moduleProvider = ModuleProvider.getDefault();

        // 5. Abrir el Socket manualmente usando la Factoría
        StandardServerChannelFactory factory = new StandardServerChannelFactory();
        factory.setReuseAddress(true);

        System.out.println("Servidor iniciado manualmente en " + direccion + ":" + puerto);

        try (ServerChannel listenSock = factory.open(direccion, puerto, timeout)) {
            while (true) {
                // Aceptamos la conexión del cliente
                DuplexByteChannel clientSock = listenSock.accept();

                // 6. Lanzar la tarea de servicio (Serve) en el executor
                executor.submit(() -> {
                    try {
                        // Obtenemos los módulos (carpetas) para esta conexión
                        Modules modules = moduleProvider.newAnonymous(clientSock.peerAddress());

                        // El método serve es el que realmente ejecuta el protocolo rsync
                        server.serve(modules, clientSock, clientSock, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try { clientSock.close(); } catch (Exception ignored) {}
                    }
                });
            }
        }*/
    }
    public static void main(String[] args) throws Exception {
        new MiServidorRsync().arrancarSinCLI();
    }

}
package org.bitBridge.Tests.rsync;

//import com.github.perlundq.yajsync.RsyncClient;
//import com.github.perlundq.yajsync.RsyncException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class ManipuladorRsync {

    /*public void sincronizarExcel() {
        // 1. Instanciamos el cliente principal (necesario para acceder a .new Local())

        RsyncClient.Local motorLocal = new RsyncClient.Builder()
                .isPreserveTimes(true)  // t
                .verbosity(1)           // v
                .buildLocal();          // Esto prepara el executor interno que viste en el código



        // 2. Accedemos a la clase interna Local que me mostraste
        //RsyncClient.Local motorLocal = rsync.new Local();

        // 3. Definimos rutas
        Path origen = Paths.get("/home/cris/prueba_rsync/origen/horario1.xlsx");
        Path destino = Paths.get("/home/cris/prueba_rsync/destino/");

        try {
            System.out.println("Ejecutando copia de bajo nivel...");

            // Aquí usamos el encadenamiento que viste en el código: copy().to()
            // copy(Iterable<Path> paths) -> devuelve una instancia de la clase interna Copy
            // to(Path dstPath) -> ejecuta localTransfer()
            RsyncClient.Result resultado = motorLocal.copy(Collections.singletonList(origen)).to(destino);

            if (resultado.isOK()) {
                System.out.println("Sincronización exitosa.");
                System.out.println("Bytes transferidos: " + resultado.statistics().totalBytesRead());
            }

        } catch (RsyncException | InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Error en la ejecución del motor: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new ManipuladorRsync().sincronizarExcel();
    }*/
}
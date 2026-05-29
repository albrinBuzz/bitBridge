package org.bitBridge.Tests.rsync;


/*import com.github.perlundq.yajsync.FileSelection;
import com.github.perlundq.yajsync.RsyncClient;
import com.github.perlundq.yajsync.net.StandardChannelFactory;
import com.github.perlundq.yajsync.net.DuplexByteChannel;
import com.github.perlundq.yajsync.net.StandardSocketChannel;*/

import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.InetAddress;
import java.util.Collections;

public class SincronizadorRemotoPro {

    public void ejecutarSincronizacionRemota() {
        // 1. Configurar los parámetros de conexión


        String host = "localhost";
        int puerto = 14415;
        String modulo = "respaldos";
        int conTimeout = 5000; // Tiempo para establecer conexión
        int ioTimeout = 0;    // 0 significa sin timeout para la transferencia

        // 2. Definir el archivo local
        //Path excelLocal = Paths.get("/home/cris/prueba_rsync/origen/horario1.xlsx");
        Path excelLocal = Paths.get("/home/cris/java/javafx/proyectos/bitBrige/bitBrige/target/BitBridge-Desktop.jar");



        // 3. Establecer la conexión de red (Capa de Sockets)
        /*StandardChannelFactory factory = new StandardChannelFactory();

        // Usamos un bloque try-with-resources para asegurar que el socket se cierre
        try (StandardSocketChannel canal = StandardSocketChannel.open(host, puerto, conTimeout, ioTimeout)) {

            // 4. Instanciar el cliente con el Builder (configurando Flags remotos)
            RsyncClient rsync = new RsyncClient.Builder()
                    .isPreserveTimes(true)
                    .verbosity(2)
                    .buildRemote();

            RsyncClient.Remote motorRemoto = new RsyncClient.Builder()
                    .verbosity(1)                   // -v (Verbose)
                    .fileSelection(FileSelection.RECURSE) // -r (Recursive, parte de -a)
                    .isPreserveLinks(true)          // -l (Links, parte de -a)
                    .isPreservePermissions(true)    // -p (Perms, parte de -a)
                    .isPreserveTimes(true)          // -t (Times, parte de -a)
                    .isPreserveGroup(true)          // -g (Group, parte de -a)
                    .isPreserveUser(true)           // -o (Owner, parte de -a)
                    .isPreserveDevices(true)        // -D (Devices, parte de -a)
                    .isPreserveSpecials(true)       // -D (Specials, parte de -a)
                    .buildRemote(canal,canal,false);          // Esto prepara el executor interno que viste en el código



            // 5. Acceder a la clase interna Remote que viste en el código fuente
            // El tercer parámetro 'false' indica si es interrumpible
            //RsyncClient.Remote motorRemoto = rsync.new Remote(canal, C, false);

            System.out.println("Conectado a " + host + ". Iniciando envío remoto...");

            // 6. Ejecutar el envío (Send)
            // .send() devuelve un objeto de la clase interna Send
            // .to() realiza el handshake y comienza la transferencia de deltas
            RsyncClient.Result resultado = motorRemoto
                    .send(Collections.singletonList(excelLocal))
                    .to(modulo, "/");

            if (resultado.isOK()) {
                var s = resultado.statistics();

                System.out.println("\n========== REPORTE DE SINCRONIZACIÓN ==========");
                System.out.println("Archivos procesados:    " + s.numFiles());
                System.out.println("Archivos transferidos: " + s.numTransferredFiles());

                System.out.println("\n--- Análisis de Datos ---");
                System.out.println("Tamaño en Disco:       " + formatBytes(s.totalFileSize()));
                System.out.println("Datos Reutilizados:    " + formatBytes(s.totalMatchedSize()));
                System.out.println("Datos Literales (Nuevos): " + formatBytes(s.totalLiteralSize()));

                System.out.println("\n--- Rendimiento de Red ---");
                System.out.println("Escritura (TX):        " + formatBytes(s.totalBytesWritten()));
                System.out.println("Lectura (RX):          " + formatBytes(s.totalBytesRead()));

                // Cálculo de eficiencia
                double ahorro = (s.totalFileSize() > 0)
                        ? (double) s.totalMatchedSize() / s.totalFileSize() * 100
                        : 0;

                System.out.println("\nEficiencia del Algoritmo: " + String.format("%.2f", ahorro) + "% de datos ahorrados");
                System.out.println("Tiempo construyendo lista: " + s.fileListBuildTime() + " ms");
                System.out.println("===============================================\n");
            }

        } catch (Exception e) {
            System.err.println("Error en la transferencia remota: " + e.getMessage());
            e.printStackTrace();
        }*/
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public static void main(String[] args) {
        new SincronizadorRemotoPro().ejecutarSincronizacionRemota();
    }
}
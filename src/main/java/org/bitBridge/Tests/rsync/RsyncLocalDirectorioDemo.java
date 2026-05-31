package org.bitBridge.Tests.rsync;



import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RsyncLocalDirectorioDemo {

    private static final int TAMANO_BLOQUE = 1024;
    private static final int BASE = 31;

    // Métricas globales para el reporte de performance local
    private static long totalBytesTransmitidosNetos = 0;
    private static long totalBytesReutilizadosServidor = 0;
    private static long tamañoTotalArchivosOrigen = 0;

    public static class Firma {
        int index;
        long rollingHash;
        String fuerteHash;

        public Firma(int index, long rollingHash, String fuerteHash) {
            this.index = index;
            this.rollingHash = rollingHash;
            this.fuerteHash = fuerteHash;
        }
    }

    public static class InstruccionDelta {
        String tipo; // "BLOQUE" o "DATOS"
        int indexBloque;
        byte[] datosNuevos;

        public static InstruccionDelta bloque(int index) {
            InstruccionDelta id = new InstruccionDelta();
            id.tipo = "BLOQUE";
            id.indexBloque = index;
            return id;
        }

        public static InstruccionDelta datos(byte[] raw) {
            InstruccionDelta id = new InstruccionDelta();
            id.tipo = "DATOS";
            id.datosNuevos = raw;
            return id;
        }
    }

    // --- 1. MOTOR RECURSIVO DE DIRECTORIOS ---
    public static void sincronizarDirectorioLocal(File carpetaOrigen, File carpetaDestino) throws Exception {
        if (!carpetaOrigen.exists()) {
            System.err.println("La carpeta de origen no existe.");
            return;
        }
        if (!carpetaDestino.exists()) {
            carpetaDestino.mkdirs();
        }

        // Ejecutar el mapeo recursivo manteniendo la consistencia de rutas relativas
        recorrerYSincronizar(carpetaOrigen, carpetaOrigen, carpetaDestino);
    }

    private static void recorrerYSincronizar(File archivoActual, File raizOrigen, File raizDestino) throws Exception {
        // Calcular la ruta relativa respecto a la raíz para replicar la estructura exacta
        String rutaRelativa = raizOrigen.toPath().relativize(archivoActual.toPath()).toString();
        File espejoDestino = new File(raizDestino, rutaRelativa);

        if (archivoActual.isDirectory()) {
            if (!espejoDestino.exists()) {
                espejoDestino.mkdirs();
            }
            File[] hijos = archivoActual.listFiles();
            if (hijos != null) {
                for (File h : hijos) {
                    recorrerYSincronizar(h, raizOrigen, raizDestino);
                }
            }
        } else if (archivoActual.isFile()) {
            tamañoTotalArchivosOrigen += archivoActual.length();

            System.out.println("Analizando: " + rutaRelativa);

            // Paso A: Generar firmas del archivo que ya existe en el espejo destino
            List<Firma> firmasViejas = generarFirmasArchivoViejo(espejoDestino);

            // Paso B: Ejecutar la ventana deslizante sobre el archivo original (fuente de verdad)
            List<InstruccionDelta> deltas = calcularDeltas(archivoActual, firmasViejas);

            // Calcular estadísticas de este archivo específico
            long bytesNuevosDeEsteArchivo = 0;
            for (InstruccionDelta d : deltas) {
                if ("BLOQUE".equals(d.tipo)) totalBytesReutilizadosServidor += TAMANO_BLOQUE;
                if ("DATOS".equals(d.tipo)) bytesNuevosDeEsteArchivo += d.datosNuevos.length;
            }
            totalBytesTransmitidosNetos += bytesNuevosDeEsteArchivo;

            // Paso C: Reconstruir el archivo destino usando los deltas calculados
            reconstruirArchivo(espejoDestino, espejoDestino, deltas);
        }
    }

    // --- 2. GENERACIÓN DE FIRMAS (ALINEADO AL TAMAÑO DE BLOQUE) ---
    public static List<Firma> generarFirmasArchivoViejo(File archivoViejo) throws Exception {
        List<Firma> lista = new ArrayList<>();
        if (!archivoViejo.exists() || !archivoViejo.isFile()) return lista;

        try (RandomAccessFile raf = new RandomAccessFile(archivoViejo, "r");
             FileChannel channel = raf.getChannel()) {

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(TAMANO_BLOQUE);
            int index = 0;

            while (channel.read(buffer) > 0) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                long rolling = 0;
                for (byte b : bytes) rolling = (rolling * BASE) + (b & 0xFF);

                md.reset();
                md.update(bytes);
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b));

                lista.add(new Firma(index++, rolling, sb.toString()));
                buffer.clear();
            }
        }
        return lista;
    }

    // --- 3. VENTANA DESLIZANTE CON CORRECCIÓN MATEMÁTICA DE PUNTEROS ---
    public static List<InstruccionDelta> calcularDeltas(File archivoNuevo, List<Firma> firmasViejas) throws Exception {
        List<InstruccionDelta> deltas = new ArrayList<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        Map<Long, Firma> mapaFirmas = new HashMap<>();
        for (Firma f : firmasViejas) {
            mapaFirmas.put(f.rollingHash, f);
        }

        try (RandomAccessFile raf = new RandomAccessFile(archivoNuevo, "r");
             FileChannel channel = raf.getChannel()) {

            long tamanoTotal = channel.size();
            if (tamanoTotal == 0) return deltas;

            ByteBuffer fileBuffer = ByteBuffer.allocateDirect((int) tamanoTotal);
            channel.read(fileBuffer);
            fileBuffer.flip();

            int posicion = 0;
            int bytesAcumulados = 0;
            int posicionDatosNuevosInicio = 0;

            long multiplicadorPotencia = 1;
            for (int i = 0; i < TAMANO_BLOQUE - 1; i++) {
                multiplicadorPotencia *= BASE;
            }

            long rollingHash = 0;
            if (tamanoTotal >= TAMANO_BLOQUE) {
                for (int i = 0; i < TAMANO_BLOQUE; i++) {
                    rollingHash = (rollingHash * BASE) + (fileBuffer.get(i) & 0xFF);
                }
            }

            while (posicion <= tamanoTotal - TAMANO_BLOQUE) {
                Firma f = mapaFirmas.get(rollingHash);

                if (f != null) {
                    byte[] ventanaBytes = new byte[TAMANO_BLOQUE];
                    fileBuffer.position(posicion);
                    fileBuffer.get(ventanaBytes);

                    md.reset();
                    md.update(ventanaBytes);
                    byte[] digest = md.digest();
                    StringBuilder sb = new StringBuilder();
                    for (byte b : digest) sb.append(String.format("%02x", b));

                    if (f.fuerteHash.equalsIgnoreCase(sb.toString())) {
                        if (bytesAcumulados > 0) {
                            byte[] raw = new byte[bytesAcumulados];
                            fileBuffer.position(posicionDatosNuevosInicio);
                            fileBuffer.get(raw);
                            deltas.add(InstruccionDelta.datos(raw));
                            bytesAcumulados = 0;
                        }

                        deltas.add(InstruccionDelta.bloque(f.index));
                        posicion += TAMANO_BLOQUE;

                        if (posicion <= tamanoTotal - TAMANO_BLOQUE) {
                            rollingHash = 0;
                            for (int i = 0; i < TAMANO_BLOQUE; i++) {
                                rollingHash = (rollingHash * BASE) + (fileBuffer.get(posicion + i) & 0xFF);
                            }
                        }
                        continue;
                    }
                }

                if (bytesAcumulados == 0) {
                    posicionDatosNuevosInicio = posicion;
                }
                bytesAcumulados++;

                if (posicion + TAMANO_BLOQUE < tamanoTotal) {
                    int byteSaliente = fileBuffer.get(posicion) & 0xFF;
                    int byteEntrante = fileBuffer.get(posicion + TAMANO_BLOQUE) & 0xFF;
                    rollingHash = (rollingHash - byteSaliente * multiplicadorPotencia) * BASE + byteEntrante;
                }
                posicion++;
            }

            if (posicion < tamanoTotal) {
                if (bytesAcumulados == 0) posicionDatosNuevosInicio = posicion;
                bytesAcumulados += (tamanoTotal - posicion);
            }

            if (bytesAcumulados > 0) {
                byte[] raw = new byte[bytesAcumulados];
                fileBuffer.position(posicionDatosNuevosInicio);
                fileBuffer.get(raw);
                deltas.add(InstruccionDelta.datos(raw));
            }
        }
        return deltas;
    }

    // --- 4. ENSAMBLADO SEGURO ---
    public static void reconstruirArchivo(File archivoViejo, File destinoFinal, List<InstruccionDelta> deltas) throws IOException {
        // Si el archivo no existía previamente (archivo nuevo en el directorio), se crea directamente desde DATOS
        File temporal = new File(destinoFinal.getAbsolutePath() + ".tmp");
        if (temporal.getParentFile() != null) temporal.getParentFile().mkdirs();

        try (RandomAccessFile rafViejo = (archivoViejo.exists() && archivoViejo.isFile()) ? new RandomAccessFile(archivoViejo, "r") : null;
             FileChannel channelViejo = rafViejo != null ? rafViejo.getChannel() : null;
             RandomAccessFile rafNuevo = new RandomAccessFile(temporal, "rw");
             FileChannel channelNuevo = rafNuevo.getChannel()) {

            for (InstruccionDelta d : deltas) {
                if ("BLOQUE".equals(d.tipo)) {
                    long posOriginal = (long) d.indexBloque * TAMANO_BLOQUE;
                    long longitudCopia = Math.min(TAMANO_BLOQUE, channelViejo.size() - posOriginal);
                    channelNuevo.transferFrom(channelViejo, channelNuevo.size(), longitudCopia);
                } else if ("DATOS".equals(d.tipo)) {
                    channelNuevo.write(ByteBuffer.wrap(d.datosNuevos));
                }
            }
        }

        // Reemplazo atómico local seguro
        if (destinoFinal.exists()) destinoFinal.delete();
        temporal.renameTo(destinoFinal);
    }

    // --- 5. PUNTO DE EJECUCIÓN (TUS RUTAS REALES) ---
    public static void main(String[] args) {
        try {
            // Configura las carpetas contenedoras basándote en tus scripts de logs
            File carpetaOrigen = new File("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/scripts/origen/");
            File carpetaDestino = new File("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/scripts/destino/");

            System.out.println("=== INICIANDO SINCRONIZACIÓN LOCAL RECURSIVA ===");
            long inicio = System.currentTimeMillis();

            // Ejecuta el proceso completo
            sincronizarDirectorioLocal(carpetaOrigen, carpetaDestino);

            long fin = System.currentTimeMillis();
            double segundos = (fin - inicio) / 1000.0;

            // Reporte de rendimiento estilo Rsync
            double speedup = totalBytesTransmitidosNetos == 0 ? 0 : (double) tamañoTotalArchivosOrigen / totalBytesTransmitidosNetos;
            System.out.println("\n==============================================");
            System.out.println("===       REPORTE DE SINCRONIZACIÓN        ===");
            System.out.println("==============================================");
            System.out.printf("Tiempo de ejecución: %.3f segundos%n", segundos);
            System.out.printf("Tamaño total analizado: %,d bytes%n", tamañoTotalArchivosOrigen);
            System.out.printf("Bytes reales modificados (transmitidos): %,d bytes%n", totalBytesTransmitidosNetos);
            System.out.printf("Bytes clonados del archivo anterior: %,d bytes%n", totalBytesReutilizadosServidor);
            System.out.printf("Factor de aceleración (Speedup): %.2fx%n", speedup);
            System.out.println("==============================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
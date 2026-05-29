package org.bitBridge.Tests.rsync.rsyncRed;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class RsyncClienteRecursivo {
    private final String host;
    private final int puerto;
    private static final int TAMANO_BLOQUE = 1024;

    public RsyncClienteRecursivo(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    /**
     * Punto de entrada: Sincroniza recursivamente todo el directorio origen hacia el servidor
     */
    public void sincronizarCarpetaCompleta(String rutaCarpetaLocal) {
        File carpetaRaiz = new File(rutaCarpetaLocal);
        if (!carpetaRaiz.exists() || !carpetaRaiz.isDirectory()) {
            System.err.println("[CLIENTE] La ruta especificada no es una carpeta válida.");
            return;
        }

        System.out.println("[CLIENTE] Iniciando escaneo recursivo en: " + carpetaRaiz.getName());
        recorrerYSincronizar(carpetaRaiz, carpetaRaiz);
        System.out.println("[CLIENTE] ¡Sincronización de carpeta completada con éxito!");
    }

    private void recorrerYSincronizar(File archivoActual, File carpetaRaiz) {
        // Calcular la ruta relativa para mantener la consistencia en el servidor
        String rutaRelativa = carpetaRaiz.toPath().relativize(archivoActual.toPath()).toString();

        if (archivoActual.isDirectory()) {
            // Notificar al servidor que asegure la existencia de la subcarpeta
            notificarCreacionCarpeta(rutaRelativa);

            File[] hijos = archivoActual.listFiles();
            if (hijos != null) {
                for (File hijo : hijos) {
                    recorrerYSincronizar(hijo, carpetaRaiz); // Llamada recursiva (Tree Walking)
                }
            }
        } else if (archivoActual.isFile()) {
            // Es un archivo regular: disparamos el rsync delta quirúrgico
            sincronizarArchivoIndividual(rutaRelativa, archivoActual);
        }
    }

    private void notificarCreacionCarpeta(String rutaRelativa) {
        if (rutaRelativa.isEmpty()) return; // Ignorar la raíz misma

        try (Socket socket = new Socket(host, puerto);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            oos.writeUTF("CARPETA");
            oos.writeUTF(rutaRelativa);
            oos.flush();

            dis.readUTF(); // Esperamos el "OK" de confirmación del servidor
        } catch (IOException e) {
            System.err.println("Error creando estructura de directorios: " + e.getMessage());
        }
    }

    private void sincronizarArchivoIndividual(String rutaRelativa, File archivoLocal) {
        List<FirmaBloque> firmas = generarFirmasLocales(archivoLocal);

        try (Socket socket = new Socket(host, puerto);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            oos.writeUTF("ARCHIVO");
            oos.writeUTF(rutaRelativa);
            oos.writeObject(firmas);
            oos.flush();

            if (!"OK".equals(dis.readUTF())) return;

            File archivoTemporal = new File(archivoLocal.getAbsolutePath() + ".tmp");
            ensamblarDeltas(archivoLocal, archivoTemporal, dis);

            // Reemplazo local atómico
            if (archivoLocal.exists()) archivoLocal.delete();
            archivoTemporal.renameTo(archivoLocal);

        } catch (Exception e) {
            System.err.println("Error sincronizando archivo " + archivoLocal.getName() + ": " + e.getMessage());
        }
    }

    private List<FirmaBloque> generarFirmasLocales(File archivo) {
        List<FirmaBloque> lista = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(archivo, "r");
             FileChannel channel = raf.getChannel()) {

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(TAMANO_BLOQUE);
            int index = 0;

            while (channel.read(buffer) > 0) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                long rolling = 0;
                for (byte b : bytes) rolling = (rolling * 31) + b;

                md.update(bytes);
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b));

                lista.add(new FirmaBloque(index++, rolling, sb.toString()));
                buffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    private void ensamblarDeltas(File archivoViejo, File archivoTemporal, DataInputStream dis) throws IOException {
        try (RandomAccessFile rafViejo = archivoViejo.exists() ? new RandomAccessFile(archivoViejo, "r") : null;
             FileChannel channelViejo = rafViejo != null ? rafViejo.getChannel() : null;
             RandomAccessFile rafNuevo = new RandomAccessFile(archivoTemporal, "rw");
             FileChannel channelNuevo = rafNuevo.getChannel()) {

            while (true) {
                String comando = dis.readUTF();
                if ("FIN".equals(comando)) break;

                if ("BLOQUE_EXISTENTE".equals(comando)) {
                    int index = dis.readInt();
                    if (channelViejo != null) {
                        long posOriginal = (long) index * TAMANO_BLOQUE;
                        long longitudCopia = Math.min(TAMANO_BLOQUE, channelViejo.size() - posOriginal);
                        channelNuevo.transferFrom(channelViejo, channelNuevo.size(), longitudCopia);
                    }
                } else if ("DATOS".equals(comando)) {
                    int lon = dis.readInt();
                    byte[] rawBytes = new byte[lon];
                    dis.readFully(rawBytes);
                    channelNuevo.write(ByteBuffer.wrap(rawBytes));
                }
            }
        }
    }
}
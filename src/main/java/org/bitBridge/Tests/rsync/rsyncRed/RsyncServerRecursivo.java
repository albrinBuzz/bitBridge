package org.bitBridge.Tests.rsync.rsyncRed;



import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.List;

public class RsyncServerRecursivo {
    private static final int PUERTO = 9999;
    private static final int TAMANO_BLOQUE = 1024;

    public void iniciar(String carpetaRaizServidor) {
        File raiz = new File(carpetaRaizServidor);
        if (!raiz.exists()) raiz.mkdirs();

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("[SERVIDOR] Rsync listo. Almacenando en: " + raiz.getAbsolutePath());

            while (true) {
                try (Socket socket = serverSocket.accept();
                     ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                     DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                    // 1. Leer la acción requerida por el cliente
                    String accion = ois.readUTF(); // "CARPETA" o "ARCHIVO"
                    String rutaRelativa = ois.readUTF();
                    File destinoFinal = new File(raiz, rutaRelativa);

                    if ("CARPETA".equals(accion)) {
                        destinoFinal.mkdirs();
                        dos.writeUTF("OK");
                        System.out.println("[SERVIDOR] Carpeta asegurada: " + rutaRelativa);
                        continue;
                    }

                    // Si es un archivo, el cliente nos manda su mapa de firmas actuales
                    @SuppressWarnings("unchecked")
                    List<FirmaBloque> firmasCliente = (List<FirmaBloque>) ois.readObject();
                    dos.writeUTF("OK");

                    System.out.println("[SERVIDOR] Sincronizando delta para: " + rutaRelativa);
                    procesarDeltas(destinoFinal, firmasCliente, dos);

                } catch (Exception e) {
                    System.err.println("[SERVIDOR] Error procesando flujo: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void procesarDeltas(File archivo, List<FirmaBloque> firmasCliente, DataOutputStream dos) throws Exception {
        // Asegurar que las carpetas contenedoras existan antes de escribir el archivo
        if (archivo.getParentFile() != null) archivo.getParentFile().mkdirs();

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        // Si el archivo no existe en el servidor, tamano es 0
        long tamanoTotal = archivo.exists() ? archivo.length() : 0;

        try (RandomAccessFile raf = archivo.exists() ? new RandomAccessFile(archivo, "r") : null;
             FileChannel channel = raf != null ? raf.getChannel() : null) {

            long posicion = 0;
            ByteArrayOutputStream bufferDatosNuevos = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(TAMANO_BLOQUE);

            while (posicion < tamanoTotal) {
                long restantes = tamanoTotal - posicion;
                int aLeer = (int) Math.min(TAMANO_BLOQUE, restantes);

                buffer.clear();
                buffer.limit(aLeer);
                channel.read(buffer, posicion);
                buffer.flip();

                byte[] bytesBloque = new byte[aLeer];
                buffer.get(bytesBloque);

                long rolling = 0;
                for (byte b : bytesBloque) rolling = (rolling * 31) + b;

                md.update(bytesBloque);
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b));
                String fuerte = sb.toString();

                FirmaBloque coincidencia = null;
                for (FirmaBloque f : firmasCliente) {
                    if (f.getRollingHash() == rolling && f.getFuerteHash().equalsIgnoreCase(fuerte)) {
                        coincidencia = f;
                        break;
                    }
                }

                if (coincidencia != null) {
                    if (bufferDatosNuevos.size() > 0) {
                        dos.writeUTF("DATOS");
                        dos.writeInt(bufferDatosNuevos.size());
                        dos.write(bufferDatosNuevos.toByteArray());
                        bufferDatosNuevos.reset();
                    }
                    dos.writeUTF("BLOQUE_EXISTENTE");
                    dos.writeInt(coincidencia.getIndex());
                    posicion += aLeer;
                } else {
                    bufferDatosNuevos.write(bytesBloque[0]);
                    posicion += 1;
                }
            }

            if (bufferDatosNuevos.size() > 0) {
                dos.writeUTF("DATOS");
                dos.writeInt(bufferDatosNuevos.size());
                dos.write(bufferDatosNuevos.toByteArray());
            }
            dos.writeUTF("FIN");
        }
    }
}
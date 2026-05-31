package org.bitBridge.Tests.rsync.rsyncRed;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;

public class GeneradorPruebasDelta {

    public static void main(String[] args) throws IOException {
        crearLogGrande(new File("/home/cris/BitBridge_Shared/logTexto.txt"),40);
        //antes hay que hacer esto?
        //ahora
        //mutarArchivo(new File("/home/cris/BitBridge_Shared/logTexto.txt"),new File("/home/cris/BitBridge_Shared/logTexto.txt"),"como estas",3);
    }

    public static void crearLogGrande(File archivo, int lineas) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(archivo))) {
            for (int i = 1; i <= lineas; i++) {
                bw.write("LOG ENTRY [" + i + "] - INFO - Procesando tarea de sincronizacion en BitBridge.\n");
            }
        }
    }

    public static void mutarArchivo(File origen, File destino, String textoNuevo, long posicion) throws IOException {
        // Copiar archivo original
        Files.copy(origen.toPath(), destino.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Inyectar mutacion en una posicion exacta
        try (RandomAccessFile raf = new RandomAccessFile(destino, "rw")) {
            raf.seek(posicion);
            raf.write(textoNuevo.getBytes());
        }
    }

    public static String calcularSHA256(File archivo) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(archivo.toPath())) {
            byte[] buffer = new byte[8192];
            int leidos;
            while ((leidos = is.read(buffer)) != -1) {
                md.update(buffer, 0, leidos);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
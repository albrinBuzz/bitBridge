package org.bitBridge.utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class HashCalculator {

    // Método para calcular el hash de un archivo
    public static String calculateFileHash(File file) throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(file);
            byte[] byteArray = new byte[1024];
            int bytesRead;

            while ((bytesRead = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesRead);
            }

            byte[] bytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    // Método recursivo para calcular el hash de un directorio
    public static String calculateDirectoryHash(File directory) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<File> files = new ArrayList<>();

        // Recolecta todos los archivos en el directorio y sus subdirectorios
        collectFiles(directory, files);

        // Para cada archivo, calcula su hash y actualiza el hash del directorio
        for (File file : files) {
            if (file.isFile()) {
                String fileHash = calculateFileHash(file);
                digest.update(fileHash.getBytes());  // Actualiza el digest con el hash del archivo
            }
        }

        byte[] bytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }

        return hexString.toString();
    }

    // Método recursivo para recolectar todos los archivos dentro del directorio y subdirectorios
    private static void collectFiles(File directory, List<File> files) {
        File[] fileList = directory.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    collectFiles(file, files);  // Llamada recursiva para subdirectorios
                } else {
                    files.add(file);  // Añadir archivo a la lista
                }
            }
        }
    }

    // Método principal para probar el cálculo del hash de un archivo o directorio
    public static void main(String[] args) {
        try {
            // Ruta del archivo o directorio
            File file = new File("/home/cris/recuerdos/corrida/dorsal.jpg"); // Reemplaza con la ruta de tu archivo
            String fileHash = calculateFileHash(file); // Calcula el hash SHA-256 de un archivo
            System.out.println("Hash del archivo: " + fileHash);

            // Ruta del directorio
            File directory = new File("/home/cris/recuerdos"); // Reemplaza con la ruta de tu directorio
            String directoryHash = calculateDirectoryHash(directory); // Calcula el hash SHA-256 del directorio
            System.out.println("Hash del directorio: " + directoryHash);

        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}

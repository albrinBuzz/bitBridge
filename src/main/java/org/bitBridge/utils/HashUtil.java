package org.bitBridge.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    // Aumentamos el buffer a 8KB o 16KB para mejorar el rendimiento de lectura en disco (I/O)
    private static final int BUFFER_SIZE = 8192;

    public static String getFileChecksum(File file) throws Exception {
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("El archivo no existe o no es válido");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return bytesToHex(digest.digest());
    }

    // Separamos la conversión a Hex y usamos un método más eficiente que String.format
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
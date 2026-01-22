package org.bitBridge.Tests;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.Random;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;

public class BigDataGenerator {

    public static void main(String[] args) {
        Path rootPath = Paths.get("BitBridge_StressTest");

        try {
            Files.createDirectories(rootPath);

            // 1. Archivo de 4 Gigabytes
            // 1024L * 1024L * 1024L = 1 GB
            long size4GB = 1024L * 1024L * 1024L * 4L;

            System.out.println("⏳ Generando ISO de 4GB (Sparse File)...");
            createBigFile(rootPath.resolve("heavy_game_disk.iso"), size4GB);

            // 2. Estructura profunda adicional
            generateDeepStructure(rootPath.resolve("deep_folders"), 5);

            System.out.println("\n✅ Entorno de prueba listo.");
            System.out.println("Ubicación: " + rootPath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private static void createBigFile(Path path, long sizeBytes) throws IOException {
        // RandomAccessFile permite saltar al final del archivo instantáneamente
        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw")) {
            f.setLength(sizeBytes);
            System.out.println("💎 Archivo Gigante creado: " + path.getFileName() + " [" + (sizeBytes / (1024*1024*1024)) + " GB]");
        }
    }

    private static void generateDeepStructure(Path current, int level) throws IOException {
        if (level == 0) return;
        Files.createDirectories(current);
        // Creamos un archivo pequeño en cada nivel para probar latencia
        Files.write(current.resolve("marker.txt"), ("Nivel: " + level).getBytes());
        generateDeepStructure(current.resolve("subdir_" + level), level - 1);
    }
}

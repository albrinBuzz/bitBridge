package org.bitBridge.Tests;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.Random;

public class DummyDataGenerator {
    static long size4GB = 1024L * 1024L * 1024L * 16L;
    static long size400MB = 1024 * 1024 * 500;
    public static void main(String[] args) {
        // Carpeta raíz de la prueba
        Path pathHeavy = Paths.get(System.getProperty("user.home"), "heavy_game_disk.iso");
        //Path rootPath = Paths.get("BitBridge_TestData");
        Path rootPath = Paths.get(System.getProperty("user.home"),"BitBridge_TestData");

        try {
            //Files.deleteIfExists(pathHeavy);

            System.out.println("🚀 Iniciando generación de datos de prueba...");
            generateStructure(rootPath, 3, 4); // 3 niveles de profundidad, 4 carpetas por nivel
            //createBigFile(pathHeavy, size4GB);

            System.out.println("\n✅ Estructura creada con éxito en: " + rootPath.toAbsolutePath());
            System.out.println("\n✅ ArchivoPesado creado en : " + pathHeavy.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ Error creando archivos: " + e.getMessage());
        }
    }

    /**
     * @param currentPath Ruta actual
     * @param depth Niveles de carpetas restantes
     * @param width Cantidad de subcarpetas por nivel
     */
    private static void generateStructure(Path currentPath, int depth, int width) throws IOException {
        if (depth < 0) return;

        // Crear el directorio actual
        Files.createDirectories(currentPath);

        // Crear un par de archivos dummy en este nivel
        createDummyFile(currentPath.resolve("file_small_" + depth + ".bin"), 1024 * 500); // 50 KB
        createDummyFile(currentPath.resolve("file_medium_" + depth + ".dat"), 1024 * 1024 * 9); // 5 MB

        // Si es el nivel raíz, creamos uno pesado para probar velocidad (WAN/LAN)
        if (depth == 3) {
            createDummyFile(currentPath.resolve("heavy_load.iso"), 1024 * 1024 * 70); // 50 MB
            //createBigFile(rootPath.resolve("heavy_game_disk.iso"), size4GB);
        }

        // Generar subcarpetas de forma recursiva
        for (int i = 1; i <= width; i++) {
            Path nextPath = currentPath.resolve("Folder_Level_" + depth + "_Node_" + i);
            generateStructure(nextPath, depth - 1, width);
        }
    }

    /**
     * Crea un archivo de tamaño exacto sin llenar la RAM (Uso de RandomAccessFile)
     */
    private static void createDummyFile(Path path, long sizeBytes) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw")) {
            f.setLength(sizeBytes); // Reserva el espacio instantáneamente en disco
            System.out.println("📄 Creado: " + path.getFileName() + " (" + (sizeBytes / 1024) + " KB)");
        }
    }
    private static void createBigFile(Path path, long sizeBytes) throws IOException {
        // RandomAccessFile permite saltar al final del archivo instantáneamente
        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw")) {
            f.setLength(sizeBytes);
            System.out.println("💎 Archivo Gigante creado: " + path.getFileName() + " [" + (sizeBytes / (1024*1024*1024)) + " GB]");
        }
    }
}

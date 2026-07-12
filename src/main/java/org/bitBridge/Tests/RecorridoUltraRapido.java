package org.bitBridge.Tests;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class RecorridoUltraRapido {

    // Contadores para verificar que ambos métodos procesen la misma cantidad de elementos
    private static long contadorNio = 0;
    private static long contadorClasico = 0;

    public static void main(String[] args) {
        String rutaStr = "/home/cris/BitBridge/Shared";
        Path rutaInicialNio = Paths.get(rutaStr);
        File carpetaInicialClasica = new File(rutaStr);

        System.out.println("=== INICIANDO PRUEBA DE RENDIMIENTO ===\n");

        // -----------------------------------------------------------------
        // PRUEBA 1: Files.walkFileTree (NIO)
        // -----------------------------------------------------------------
        long inicioNio = System.nanoTime();
        try {
            Files.walkFileTree(rutaInicialNio, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path archivo, BasicFileAttributes atributos) {
                    contadorNio++;
                    // System.out.println(archivo); // Comentado para no falsear el tiempo real
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    contadorNio++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path archivo, IOException excepcion) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        long finNio = System.nanoTime();
        double tiempoNioMs = (finNio - inicioNio) / 1_000_000.0;

        // -----------------------------------------------------------------
        // PRUEBA 2: Método Recursivo Clásico (java.io.File)
        // -----------------------------------------------------------------
        long inicioClasico = System.nanoTime();
        if (carpetaInicialClasica.exists() && carpetaInicialClasica.isDirectory()) {
            listarArchivosRecursivamente(carpetaInicialClasica);
        }
        long finClasico = System.nanoTime();
        double tiempoClasicoMs = (finClasico - inicioClasico) / 1_000_000.0;

        // -----------------------------------------------------------------
        // REPORTE DE RESULTADOS
        // -----------------------------------------------------------------
        System.out.println("Resultados para: " + rutaStr);
        System.out.println("--------------------------------------------------");
        System.out.printf("1. Files.walkFileTree (NIO)  | Elementos: %d | Tiempo: %.3f ms%n", contadorNio, tiempoNioMs);
        System.out.printf("2. Recursión Clásica (File)  | Elementos: %d | Tiempo: %.3f ms%n", contadorClasico, tiempoClasicoMs);
        System.out.println("--------------------------------------------------");

        if (tiempoNioMs < tiempoClasicoMs) {
            double diferencia = (tiempoClasicoMs / tiempoNioMs);
            System.out.printf("¡NIO fue aproximadamente %.2fx más rápido!%n", diferencia);
        } else {
            double diferencia = (tiempoNioMs / tiempoClasicoMs);
            System.out.printf("¡El método clásico fue aproximadamente %.2fx más rápido!%n", diferencia);
        }
    }

    public static void listarArchivosRecursivamente(File carpeta) {
        contadorClasico++; // Cuenta la carpeta actual
        File[] archivos = carpeta.listFiles();

        if (archivos != null) {
            for (File archivo : archivos) {
                if (archivo.isDirectory()) {
                    // System.out.println("[Directorio] " + archivo.getAbsolutePath());
                    listarArchivosRecursivamente(archivo);
                } else {
                    contadorClasico++; // Cuenta el archivo
                    // System.out.println("[Archivo] " + archivo.getAbsolutePath());
                }
            }
        }
    }
}

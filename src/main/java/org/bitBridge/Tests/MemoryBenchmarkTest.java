package org.bitBridge.Tests;


import java.io.Serializable;
import java.util.UUID;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
public class MemoryBenchmarkTest {

    public static void main(String[] args) {
        int totalArchivosSimulados = 100_000; // Un proyecto o directorio gigante
        String sessionId = UUID.randomUUID().toString();

        System.out.println("====== bitBridge Memory Benchmark ======");
        System.out.println("Simulando carga de: " + totalArchivosSimulados + " archivos...");

        // 1. Limpiar memoria antes de la prueba
        prepararGarbageCollector();
        long memoriaAntes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // 2. Instanciar y llenar el manifiesto masivo
        DirectoryManifestCommunication manifiesto = new DirectoryManifestCommunication(sessionId);
        for (int i = 0; i < totalArchivosSimulados; i++) {
            // Simulamos rutas promedio reales de un proyecto
            String nombre = "ArchivoClaseTest_" + i + ".java";
            String rutaRelativa = "src/main/java/org/bitbridge/core/engine/handlers/processors/" + nombre;

            manifiesto.addEntry(
                    nombre,
                    1024L * (i % 50), // Tamaños variables
                    false,
                    rutaRelativa,
                    System.currentTimeMillis()
            );
        }

        // 3. Medir consumo final
        prepararGarbageCollector();
        long memoriaDespues = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long memoriaConsumidaBytes = memoriaDespues - memoriaAntes;
        double memoriaConsumidaMegas = memoriaConsumidaBytes / (1024.0 * 1024.0);

        System.out.println("\n====== RESULTADOS ======");
        System.out.printf("Total de elementos en memoria : %,d%n", manifiesto.getEntries().size());
        System.out.printf("Memoria Heap neta utilizada   : %,d bytes%n", memoriaConsumidaBytes);
        System.out.printf("Memoria Heap neta utilizada   : %.2f MB%n", memoriaConsumidaMegas);
        System.out.printf("Costo promedio por archivo     : %.1f bytes%n", (double) memoriaConsumidaBytes / totalArchivosSimulados);
        System.out.println("========================");
    }

    private static void prepararGarbageCollector() {
        try {
            for (int i = 0; i < 4; i++) {
                System.gc();
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    static class DirectoryManifestCommunication implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String sessionId;
        private final List<FileEntry> entries = new ArrayList<>();

        public DirectoryManifestCommunication(String sessionId) {
            this.sessionId = sessionId;
        }

        public void addEntry(String name, long size, boolean isDir, String relativePath, long lastModified) {
            entries.add(new FileEntry(name, size, isDir, relativePath, lastModified));
        }

        public List<FileEntry> getEntries() { return entries; }
        public String getSessionId() { return sessionId; }

        // Usamos un Record por rendimiento y bajo 'overhead' de memoria
        public static record FileEntry(
                String name,
                long size,
                boolean isDir,
                String relativePath,
                long lastModified
        ) implements Serializable {}
    }

    class DirectoryDecisionCommunication implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String sessionId;
        private final List<FileDecision> decisions = new ArrayList<>();

        public DirectoryDecisionCommunication(String sessionId) {
            this.sessionId = sessionId;
        }

        public void addDecision(String relativePath, String action) {
            decisions.add(new FileDecision(relativePath, action));
        }

        public List<FileDecision> getDecisions() { return decisions; }
        public String getSessionId() { return sessionId; }

        public static record FileDecision(String relativePath, String action) implements Serializable {}
    }
}
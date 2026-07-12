package org.bitBridge.shared.core.comunication.model.basic;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Modelo anatómico de archivo/directorio optimizado para sincronización rápida estilo Rsync.
 * * Copyright 2026 Cristobal Roman Zamora
 */
public class NodoDirectorio implements Serializable {
    private static final long serialVersionUID = 20260603L;

    private static final Set<String> SYS_EXCLUSIONS = Set.of(
            "/proc", "/sys", "/dev", "/run", "/snap"
    );

    private final String nombre;
    private String rutaString;
    private transient Path rutaCompleta;
    private final boolean esDirectorio;
    private long tamaño;
    private final List<NodoDirectorio> hijos;
    private boolean cargado;
    private long fechaModificacionMillis;

    // ⚡ NUEVOS METADATOS PARA EL INSPECTOR ADVANCED
    private String permisosPosix = "----------";
    private String propietarioString = "Unknown";

    public NodoDirectorio(Path ruta) {
        this.nombre = ruta.getFileName() != null ? ruta.getFileName().toString() : ruta.toString();
        this.rutaCompleta = ruta;
        this.rutaString = ruta.toAbsolutePath().toString();
        this.esDirectorio = Files.isDirectory(ruta);
        this.hijos = new ArrayList<>();
        this.cargado = !esDirectorio;

        try {
            //this.tamaño = esDirectorio ? 0 : Files.size(ruta);
            this.tamaño=obtenerTamañoHibridoNIO(ruta);
            this.fechaModificacionMillis = Files.getLastModifiedTime(ruta).toMillis();

            // Extracción nativa de Atributos POSIX (Seguro para Linux Fedora/Rocky)
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                this.permisosPosix = PosixFilePermissions.toString(Files.getPosixFilePermissions(ruta));
                this.propietarioString = Files.getOwner(ruta).getName();
            } else {
                // Fallback básico para entornos de desarrollo en Windows
                this.permisosPosix = esDirectorio ? "drwxr-xr-x" : "-rw-r--r--";
                this.propietarioString = Files.getOwner(ruta).getName();
                this.propietarioString = System.getProperty("user.name");
            }
        } catch (IOException e) {
            this.tamaño = 0;
            this.fechaModificacionMillis = 0;
        }
    }


    public static long obtenerTamañoHibridoNIO(Path ruta) {
        if (!Files.isDirectory(ruta)) {
            try {
                return Files.size(ruta);
            } catch (IOException e) { return 0; }
        }

        // Transforma el árbol de archivos en un flujo plano y suma en paralelo
        try (Stream<Path> stream = Files.walk(ruta)) {
            return stream
                    .parallel() // ⚡ Divide el trabajo entre los núcleos de tu CPU
                    .filter(p -> !Files.isDirectory(p)) // Solo sumamos el peso de los archivos reales
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }


    public synchronized void cargarContenido() {
        if (cargado || !esDirectorio || rutaCompleta == null) return;
        if (SYS_EXCLUSIONS.contains(rutaString)) {
            this.cargado = true;
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rutaCompleta)) {
            hijos.clear();
            long sumaTamaño = 0;
            long ultimaModificacionHijos = this.fechaModificacionMillis;

            for (Path entrada : stream) {
                if (!Files.isReadable(entrada)) continue;

                NodoDirectorio hijo = new NodoDirectorio(entrada);
                sumaTamaño += hijo.getTamaño();

                if (hijo.getFechaModificacionMillis() > ultimaModificacionHijos) {
                    ultimaModificacionHijos = hijo.getFechaModificacionMillis();
                }

                hijos.add(hijo);
            }

            // Asignación de metadatos calculados al directorio padre
            this.tamaño = sumaTamaño;
            this.fechaModificacionMillis = ultimaModificacionHijos;

            cargado = true;
        } catch (IOException e) {
            org.bitBridge.shared.Logger.logError("Error cargando nivel: " + rutaString);
        }
    }

    // --- GETTERS COMPATIBLES Y NUEVOS ---
    public String getNombre() { return nombre; }
    public String getRutaString() { return rutaString; }
    public boolean esDirectorio() { return esDirectorio; }
    public long getTamaño() { return tamaño; }
    public List<NodoDirectorio> getHijos() { return hijos; }
    public boolean isCargado() { return cargado; }
    public long getFechaModificacionMillis() { return fechaModificacionMillis; }
    public String getPermisosPosix() { return permisosPosix; }
    public String getPropietarioString() { return propietarioString; }

    public String getExtension() {
        if (esDirectorio || !nombre.contains(".")) return "Carpeta";
        return nombre.substring(nombre.lastIndexOf('.')).toUpperCase();
    }

    public String getTamañoFormateado() {
        if (esDirectorio) return "Directorio";
        if (tamaño < 1024) return tamaño + " B";
        int exp = (int) (Math.log(tamaño) / Math.log(1024));
        return String.format("%.2f %cB", tamaño / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }

    public String getFechaModificacion() {
        if (fechaModificacionMillis == 0) return "Desconocido";
        return java.time.Instant.ofEpochMilli(fechaModificacionMillis)
                .toString().substring(0, 19).replace("T", " ");
    }

    @Override
    public String toString() {
        return "NodoDirectorio{" +
                "nombre='" + nombre + '\'' +
                ", rutaString='" + rutaString + '\'' +
                ", rutaCompleta=" + rutaCompleta +
                ", esDirectorio=" + esDirectorio +
                ", tamaño=" + tamaño +
                ", hijos=" + hijos +
                ", cargado=" + cargado +
                ", fechaModificacionMillis=" + fechaModificacionMillis +
                ", permisosPosix='" + permisosPosix + '\'' +
                ", propietarioString='" + propietarioString + '\'' +
                '}';
    }
}
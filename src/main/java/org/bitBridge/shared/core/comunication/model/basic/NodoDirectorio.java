package org.bitBridge.shared.core.comunication.model.basic;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Modelo anatómico de archivo/directorio optimizado para sincronización rápida estilo Rsync.
 * Eliminado el cálculo de hashes síncronos para evitar cuellos de botella en I/O.
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
    private long fechaModificacionMillis; // ⚡ NUEVO: Timestamp exacto en ms para el Quick Check de Rsync

    public NodoDirectorio(Path ruta) {
        this.nombre = ruta.getFileName() != null ? ruta.getFileName().toString() : ruta.toString();
        this.rutaCompleta = ruta;
        this.rutaString = ruta.toAbsolutePath().toString();
        this.esDirectorio = Files.isDirectory(ruta);
        this.hijos = new ArrayList<>();
        this.cargado = !esDirectorio;

        try {
            this.tamaño = esDirectorio ? 0 : Files.size(ruta);
            // Capturamos los milisegundos del sistema de archivos de forma nativa
            this.fechaModificacionMillis = esDirectorio ? 0 : Files.getLastModifiedTime(ruta).toMillis();
        } catch (IOException e) {
            this.tamaño = 0;
            this.fechaModificacionMillis = 0;
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
            for (Path entrada : stream) {
                if (!Files.isReadable(entrada)) continue;
                if (Files.isSymbolicLink(entrada)) {
                    try {
                        Path destino = Files.readSymbolicLink(entrada);
                        if (rutaCompleta.startsWith(destino) || entrada.equals(destino)) continue;
                    } catch (IOException e) { continue; }
                }
                hijos.add(new NodoDirectorio(entrada));
            }
            cargado = true;
        } catch (IOException e) {
            org.bitBridge.shared.Logger.logError("Error cargando nivel: " + rutaString);
        }
    }

    // --- GETTERS LIGEROS ---
    public String getNombre() { return nombre; }
    public String getRutaString() { return rutaString; }
    public boolean esDirectorio() { return esDirectorio; }
    public long getTamaño() { return tamaño; }
    public List<NodoDirectorio> getHijos() { return hijos; }
    public boolean isCargado() { return cargado; }
    public long getFechaModificacionMillis() { return fechaModificacionMillis; }

    public String getExtension() {
        if (esDirectorio || !nombre.contains(".")) return "Carpeta";
        return nombre.substring(nombre.lastIndexOf('.')).toUpperCase();
    }

    public String getTamañoFormateado() {
        if (esDirectorio) return "--";
        if (tamaño < 1024) return tamaño + " B";
        int exp = (int) (Math.log(tamaño) / Math.log(1024));
        return String.format("%.2f %cB", tamaño / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }

    public String getFechaModificacion() {
        if (fechaModificacionMillis == 0) return "Desconocido";
        // Formateo rápido a partir del primitivo serializado
        return java.time.Instant.ofEpochMilli(fechaModificacionMillis)
                .toString().substring(0, 19).replace("T", " ");
    }
}
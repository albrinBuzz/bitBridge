package org.bitBridge.web;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

import org.bitBridge.server.ConfiguracionServidor;
import org.bitBridge.shared.Logger;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.primefaces.model.file.UploadedFiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Named("uploadBean")
@RequestScoped
public class FileUploadBean {

    private UploadedFile file;
    private UploadedFiles files;
    private Path directorioUpload; // Usamos Path en lugar de String para rutas

    @PostConstruct
    public void init() {
        ConfiguracionServidor config = ConfiguracionServidor.getInstancia();

        // Obtenemos el path base de la config y le añadimos "Upload" de forma agnóstica
        String baseDir = config.obtener("cliente.directorio_descargas");
        this.directorioUpload = Paths.get(baseDir, "Upload");

        try {
            if (!Files.exists(directorioUpload)) {
                Files.createDirectories(directorioUpload);
                Logger.logInfo("Directorio de subida creado en: " + directorioUpload.toAbsolutePath());
            }
        } catch (IOException e) {
            Logger.logError("No se pudo crear el directorio de subida: " + e.getMessage());
        }
    }

    /**
     * Método centralizado para guardar archivos.
     * Evita repetir la lógica de InputStream/OutputStream en cada evento.
     */
    private void guardarArchivo(InputStream input, String nombreArchivo) throws IOException {
        Path destino = directorioUpload.resolve(nombreArchivo);
        // StandardCopyOption.REPLACE_EXISTING para que no falle si el archivo ya existe
        Files.copy(input, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    public void upload() {
        if (file != null) {
            processUpload(file);
        }
    }

    public void handleFileUpload(FileUploadEvent event) {
        processUpload(event.getFile());
    }

    private void processUpload(UploadedFile f) {
        if (f == null) return;

        try (InputStream is = f.getInputStream()) {
            guardarArchivo(is, f.getFileName());

            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito",
                    "Archivo subido: " + f.getFileName());

            Logger.logInfo("Archivo guardado: " + f.getFileName() + " en " + directorioUpload);
        } catch (IOException e) {
            Logger.logError("Error al guardar archivo: " + e.getMessage());
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo guardar el archivo.");
        }
    }

    public void handleFilesUpload(org.primefaces.event.FilesUploadEvent event) {
        for (UploadedFile f : event.getFiles().getFiles()) {
            processUpload(f);
        }
    }

    private void mostrarMensaje(FacesMessage.Severity severidad, String resumen, String detalle) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, resumen, detalle));
    }

    // --- Getters y Setters ---
    public UploadedFile getFile() { return file; }
    public void setFile(UploadedFile file) { this.file = file; }
    public UploadedFiles getFiles() { return files; }
    public void setFiles(UploadedFiles files) { this.files = files; }
}
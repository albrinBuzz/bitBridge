package org.bitBridge.web;




import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.bitBridge.models.LogChat;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.web.repo.FileRepositoryBean;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import org.primefaces.model.file.UploadedFiles;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Named
@ViewScoped
public class ActivityBean implements Serializable {


    private String statusMessage = "Esperando interacción...";
    private UploadedFile file;
    private UploadedFiles files;
    private Path directorioUpload; // Usamos Path en lugar de String para rutas
    @Inject
    private FileRepositoryBean repository;

    @PostConstruct
    public void init() {

        ConfiguracionApp config = ConfiguracionApp.getInstancia();

        // Obtenemos el path base de la config y le añadimos "Upload" de forma agnóstica
        String baseDir = config.obtener(ConfigKey.DOWNLOAD_DIR);
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

    /*public void handleFileUpload(FileUploadEvent event) {
        String name = event.getFile().getFileName();
        String size = (event.getFile().getSize() / 1024) + " KB";

        // Añadimos el archivo subido a la lista de logs
        logs.add(new LogChat("Cristobal", name, size + " • MANUAL UPLOAD", "fa-file-upload", "SENT"));
        statusMessage = "Archivo " + name + " sincronizado.";
    }*/

    public StreamedContent downloadFile(LogChat entry) throws FileNotFoundException {
        // Mock de descarga: devuelve un archivo de texto vacío con el nombre del log
        File file= new File(directorioUpload+"/"+entry.getFileName());

        InputStream stream = new FileInputStream(file);

        String contentType = guessContentType(file);

        return DefaultStreamedContent.builder()
                .name(file.getName())
                .contentType(contentType)
                .stream(() -> stream)
                .build();

        /*return DefaultStreamedContent.builder()
                .name(entry.getFileName())
                .contentType("application/octet-stream")
                .stream(() -> new ByteArrayInputStream(new byte[0]))
                .build();*/
    }

    private String guessContentType(File file) {
        try {
            return Files.probeContentType(file.toPath());
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    // Getters y Setters
    //public List<LogChat> getLogs() { return logs; }
    public List<LogChat> getLogs() {
        return repository.getGlobalLogs();
    }

    public String getStatusMessage() { return statusMessage; }


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

            String name = f.getFileName();
            String size = (f.getSize() / 1024) + " KB";

            // Añadimos el archivo subido a la lista de logs
            //logs.add(new LogChat("Cristobal", name, size + " • MANUAL UPLOAD", "fa-file-upload", "SENT"));

            LogChat newFile = new LogChat(
                    "User_" + System.currentTimeMillis() % 1000,
                    name,
                    size,
                    "fa-file",
                    "SENT"
            );

            // Guardamos en el repositorio que todos comparten
            repository.addEntry(newFile);

            for (LogChat log : getLogs()) {
                Logger.logInfo(log.toString());
            }

            statusMessage = "Archivo " + name + " sincronizado.";
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
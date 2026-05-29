package org.bitBridge.web;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;


import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Named
@ViewScoped

public class ExplorerBean implements Serializable {

    private String currentPath;
    private List<FileItem> fileList;
    private List<String> systemLogs;

    @PostConstruct
    public void init() {
        currentPath = "/storage/node_01/workspace";
        systemLogs = new ArrayList<>();
        addLog("[SYS] BitBridge NIO Channel Activo.");
        addLog("[INFO] Conectado a base de datos multi-tenant.");

        // Simulación de datos cargados desde el sistema operativo
        fileList = new ArrayList<>();
        fileList.add(new FileItem("backup_kiosko_db", "Directorio", "2026-05-01", true));
        fileList.add(new FileItem("schema_sass.sql", "14.2 KB", "2026-05-02", false));
        fileList.add(new FileItem("NioServerHandler.java", "8.1 KB", "2026-04-28", false));
    }

    public void processAction(FileItem file) {
        if(file.isDirectory()) {
            currentPath += "/" + file.getName();
            addLog("[CMD] cd " + file.getName());
            // Lógica para cargar nuevos archivos de la carpeta...
        } else {
            addLog("[STREAM] Iniciando descarga de: " + file.getName());
            // Lógica para StreamedContent de Primefaces (Descarga)
        }
    }

    public void deleteFile(FileItem file) {
        fileList.remove(file);
        addLog("[WARN] Archivo eliminado: " + file.getName());
    }

    public void handleFileUpload(FileUploadEvent event) {
        UploadedFile file = event.getFile();
        if (file != null) {
            String logMsg = String.format("[UPLOAD] OK - %s (%.2f MB)",
                    file.getFileName(),
                    (file.getSize() / 1024.0 / 1024.0));
            addLog(logMsg);
            // Agregamos visualmente el archivo subido a la lista
            fileList.add(new FileItem(file.getFileName(), (file.getSize() / 1024) + " KB", "Ahora", false));
        }
    }

    private void addLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        systemLogs.add(String.format("> %s: %s", timestamp, message));
    }

    // Getters y Setters
    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String currentPath) { this.currentPath = currentPath; }
    public List<FileItem> getFileList() { return fileList; }
    public void setFileList(List<FileItem> fileList) { this.fileList = fileList; }
    public List<String> getSystemLogs() { return systemLogs; }
    public void setSystemLogs(List<String> systemLogs) { this.systemLogs = systemLogs; }

    // Clase interna para manejar los datos en la vista (DTO)
    public static class FileItem {
        private String name;
        private String sizeStr;
        private String dateStr;
        private boolean isDirectory;

        public FileItem(String name, String sizeStr, String dateStr, boolean isDirectory) {
            this.name = name;
            this.sizeStr = sizeStr;
            this.dateStr = dateStr;
            this.isDirectory = isDirectory;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSizeStr() {
            return sizeStr;
        }

        public void setSizeStr(String sizeStr) {
            this.sizeStr = sizeStr;
        }

        public String getDateStr() {
            return dateStr;
        }

        public void setDateStr(String dateStr) {
            this.dateStr = dateStr;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public void setDirectory(boolean directory) {
            isDirectory = directory;
        }
    }
}
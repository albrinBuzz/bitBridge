package org.bitBridge.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogChat {
    private String origin;
    private String fileName;
    private String fileMeta;
    private String fileIcon;
    private String type; // "SENT" o "RECEIVED"
    private LocalDateTime timestamp;

    public LogChat(String origin, String fileName, String fileMeta, String fileIcon, String type) {
        this.origin = origin;
        this.fileName = fileName;
        this.fileMeta = fileMeta;
        this.fileIcon = fileIcon;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public String getFormattedDate() { return timestamp.format(DateTimeFormatter.ofPattern("dd MMM yyyy")); }
    public String getTimestamp() { return timestamp.format(DateTimeFormatter.ofPattern("HH:mm")); }
    public LocalDateTime getFecha(){
        return timestamp;
    }

    // Getters estándar para JSF...
    public String getOrigin() { return origin; }
    public String getFileName() { return fileName; }
    public String getFileMeta() { return fileMeta; }
    public String getFileIcon() { return fileIcon; }
    public String getType() { return type; }

    @Override
    public String toString() {
        return "LogChat{" +
                "origin='" + origin + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileMeta='" + fileMeta + '\'' +
                ", fileIcon='" + fileIcon + '\'' +
                ", type='" + type + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}

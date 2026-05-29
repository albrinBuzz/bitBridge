package org.bitBridge.shared.core.comunication;

import java.io.Serializable;

public class FileDirectoryCommunication extends Communication implements Serializable {

    private String name;
    private long size;
    private boolean isDirectory;
    private int totalArchivos;
    private String recipient;
    private String relativePath; // <--- CRÍTICO: Para reconstruir la estructura en NIO

    // Metadatos adicionales
    private String senderNick;
    private String hash;
    private long lastModified;

    // Constructor para Carpeta Principal (Handshake Inicial)
    public FileDirectoryCommunication(String name, int totalArchivos, String recipient, long size) {
        super(CommunicationType.DIRECTORY);
        this.name = name;
        this.totalArchivos = totalArchivos;
        this.recipient = recipient;
        this.size = size;
        this.isDirectory = true;
    }

    // Constructor para Archivos/Subcarpetas durante el flujo NIO
    public FileDirectoryCommunication(String name, long size, boolean isDirectory, String relativePath) {
        super(isDirectory ? CommunicationType.DIRECTORY : CommunicationType.FILE);
        this.name = name;
        this.size = size;
        this.isDirectory = isDirectory;
        this.relativePath = relativePath;
    }

    // Constructor simplificado (para compatibilidad)
    public FileDirectoryCommunication(String name, long length) {
        super(CommunicationType.FILE);
        this.name = name;
        this.size = length;
        this.isDirectory = false;
    }

    // Constructor para archivo
    public FileDirectoryCommunication(String name, long size,String recipient,String senderNick) {
        super(CommunicationType.FILE);  // O puedes usar CommunicationType.DIRECTORY si es un directorio
        this.name = name;
        this.size = size;
        this.recipient=recipient;
        this.senderNick=senderNick;

        this.isDirectory = false;  // Es un archivo por defecto
    }

    public FileDirectoryCommunication(String hash,String name, long size,String recipient,String senderNick) {
        super(CommunicationType.FILE);  // O puedes usar CommunicationType.DIRECTORY si es un directorio
        this.name = name;
        this.size = size;
        this.recipient=recipient;
        this.senderNick=senderNick;
        this.hash=hash;
        this.isDirectory = false;  // Es un archivo por defecto
    }

    // Constructor para directorio
    public FileDirectoryCommunication(String name,int totalArchivos,String recipient) {
        super(CommunicationType.DIRECTORY);
        this.name = name;
        this.size = 0;             // Un directorio no tiene un tamaño específico
        this.isDirectory = true;
        this.totalArchivos=totalArchivos;
        this.recipient=recipient;
    }



    // GETTERS Y SETTERS NECESARIOS
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getName() { return name; }
    public long getSize() { return size; }
    public boolean isDirectory() { return isDirectory; }
    public int getTotalArchivos() { return totalArchivos; }
    public String getRecipient() { return recipient; }
    public String getSenderNick() { return senderNick; }
    public void setSenderNick(String senderNick) { this.senderNick = senderNick; }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public String toString() {
        return (isDirectory ? "[DIR] " : "[FILE] ") + name + " (" + size + " bytes) path: " + relativePath;
    }
}
package org.bitBridge.shared;

import java.io.Serializable;

public class FileDirectoryCommunication extends Communication implements Serializable {

    private String name;            // Nombre del archivo o directorio
    private long size;              // Tamaño del archivo/directorio en bytes
    private boolean isDirectory;    // Indicador de si es un archivo o un directorio
    private int  totalArchivos;
    private String recipient;
    private String hash;

    // Constructor para archivo
    public FileDirectoryCommunication(String name, long size,String recipient) {
        super(CommunicationType.FILE);  // O puedes usar CommunicationType.DIRECTORY si es un directorio
        this.name = name;
        this.size = size;
        this.recipient=recipient;

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

    public FileDirectoryCommunication(String name, long length) {
        super(CommunicationType.FILE);  // O puedes usar CommunicationType.DIRECTORY si es un directorio
        this.name = name;
        this.size = length;
        this.isDirectory = false;  // Es un archivo por defecto
    }

    // Getter para el nombre del archivo/directorio
    public String getName() {
        return name;
    }

    // Getter para el tamaño
    public long getSize() {
        return size;
    }


    // Verificar si es un directorio
    public boolean isDirectory() {
        return isDirectory;
    }

    public int getTotalArchivos() {
        return totalArchivos;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Representación en cadena (opcional)
    @Override
    public String toString() {
        return "FileDirectoryCommunication{" +
                "name='" + name + '\'' +
                ", size=" + size +
                ", isDirectory=" + isDirectory +
                '}';
    }
}

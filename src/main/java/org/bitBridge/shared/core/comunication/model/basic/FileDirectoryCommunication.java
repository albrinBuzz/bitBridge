package org.bitBridge.shared.core.comunication.model.basic;

import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

import java.io.Serializable;

@BufferPoolMapping(DirectBufferPool.BufferType.TRANSFER)
public class FileDirectoryCommunication extends Communication implements Serializable {
    private String name;
    private long size;
    private boolean isDirectory;
    private int totalArchivos;
    private String recipient;
    private String relativePath;
    private String senderNick;
    private String hash;
    private long lastModified;

    public FileDirectoryCommunication(String name, int totalArchivos, String recipient, long size) {
        this.name = name;
        this.totalArchivos = totalArchivos;
        this.recipient = recipient;
        this.size = size;
        this.isDirectory = true;
    }
    public FileDirectoryCommunication(String name, long size, boolean isDirectory, String relativePath) {
        this.name = name;
        this.size = size;
        this.isDirectory = isDirectory;
        this.relativePath = relativePath;
    }
    public FileDirectoryCommunication(String name, long length) {
        this.name = name;
        this.size = length;
        this.isDirectory = false;
    }
    public FileDirectoryCommunication(String name, long size, String recipient, String senderNick) {
        this.name = name;
        this.size = size;
        this.recipient = recipient;
        this.senderNick = senderNick;
        this.isDirectory = false;
    }
    public FileDirectoryCommunication(String hash, String name, long size, String recipient, String senderNick) {
        this.name = name;
        this.size = size;
        this.recipient = recipient;
        this.senderNick = senderNick;
        this.hash = hash;
        this.isDirectory = false;
    }
    public FileDirectoryCommunication(String name, int totalArchivos, String recipient) {
        this.name = name;
        this.size = 0;
        this.isDirectory = true;
        this.totalArchivos = totalArchivos;
        this.recipient = recipient;
    }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getName() { return name; }
    public long getSize() { return size; }
    public boolean isDirectory() { return isDirectory; }
    public int getTotalArchivos() { return totalArchivos; }
    public String getRecipient() { return recipient; }
    public String getSenderNick() { return senderNick; }
    public void setSenderNick(String senderNick) { this.senderNick = senderNick; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public long getLastModified() { return lastModified; }
    public void setName(String name) { this.name = name; }
    public void setSize(long size) { this.size = size; }
    public void setDirectory(boolean directory) { this.isDirectory = directory; }
    public void setTotalArchivos(int totalArchivos) { this.totalArchivos = totalArchivos; }

    @Override
    public String getSubAction() {
        return isDirectory ? "DIRECTORY" : "FILE";
    }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }

    @Override
    public String toString() {
        return (isDirectory ? "[DIR] " : "[FILE] ") + name + " (" + size + " bytes) path: " + relativePath;
    }
}
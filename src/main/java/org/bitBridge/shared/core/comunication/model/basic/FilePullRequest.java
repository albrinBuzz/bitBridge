package org.bitBridge.shared.core.comunication.model.basic;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

import java.util.UUID;

@BufferPoolMapping(DirectBufferPool.BufferType.TRANSFER)
public class FilePullRequest extends Communication {
    @JsonProperty("rutaRemota") private String rutaRemota;
    @JsonProperty("nombreArchivo") private String nombreArchivo;
    @JsonProperty("targetIp") private String targetIp;
    @JsonProperty("requesterNick") private String requesterNick;
    @JsonProperty("sessionId") private String sessionId;
    @JsonProperty("timestamp") private long timestamp;
    @JsonProperty("esDirectorio") private boolean esDirectorio;

    public FilePullRequest() {}
    public FilePullRequest(String rutaRemota, String nombreArchivo, String targetIp, String requesterNick, boolean esDirectorio) {
        this.rutaRemota = rutaRemota;
        this.nombreArchivo = nombreArchivo;
        this.targetIp = targetIp;
        this.requesterNick = requesterNick;
        this.esDirectorio = esDirectorio;
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.timestamp = System.currentTimeMillis();
    }

    public String getRutaRemota() { return rutaRemota; }
    public void setRutaRemota(String rutaRemota) { this.rutaRemota = rutaRemota; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }
    public String getRequesterNick() { return requesterNick; }
    public void setRequesterNick(String requesterNick) { this.requesterNick = requesterNick; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public long getTimestamp() { return timestamp; }
    public boolean isEsDirectorio() { return esDirectorio; }
    public void setEsDirectorio(boolean esDirectorio) { this.esDirectorio = esDirectorio; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }
}
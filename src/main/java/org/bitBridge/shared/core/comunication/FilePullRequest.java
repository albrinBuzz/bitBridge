package org.bitBridge.shared.core.comunication;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Representa una solicitud de descarga (PULL) de un archivo específico.
 * Incluye trazabilidad para que el servidor sepa a quién retornar los datos.
 */
public class FilePullRequest extends Communication {

    @JsonProperty("rutaRemota")
    private String rutaRemota;

    @JsonProperty("nombreArchivo")
    private String nombreArchivo;

    @JsonProperty("targetIp") // El nick/IP del nodo que TIENE el archivo
    private String targetIp;

    @JsonProperty("requesterNick") // El nick del usuario que PIDE el archivo
    private String requesterNick;

    @JsonProperty("sessionId") // ID único para rastrear esta transferencia
    private String sessionId;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("esDirectorio")
    private boolean esDirectorio;

    // Constructor vacío para Jackson

    public FilePullRequest() { super(CommunicationType.FILE_PULL_REQUEST); }

    public FilePullRequest(String rutaRemota, String nombreArchivo, String targetIp, String requesterNick, boolean esDirectorio) {
        super(CommunicationType.FILE_PULL_REQUEST);
        this.rutaRemota = rutaRemota;
        this.nombreArchivo = nombreArchivo;
        this.targetIp = targetIp;
        this.requesterNick = requesterNick;
        this.esDirectorio = esDirectorio; // <--- Nuevo campo
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.timestamp = System.currentTimeMillis();
    }

    // --- Getters y Setters ---

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
}
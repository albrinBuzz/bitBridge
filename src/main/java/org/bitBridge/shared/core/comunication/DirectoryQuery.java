package org.bitBridge.shared.core.comunication;



import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa una solicitud de exploración remota.
 * Se usa para pedir el listado de archivos de una ruta específica.
 */
public class DirectoryQuery extends Communication {

    @JsonProperty("targetPath")
    private String targetPath;

    @JsonProperty("token")
    private String token; // Identificador para rastrear la respuesta
    private String targetIp;
    private String sourceIp;

    public DirectoryQuery() {
        super(CommunicationType.DIRECTORY_QUERY);
    }

    public DirectoryQuery(String targetIp,String sourceIp,String token) {
        super(CommunicationType.DIRECTORY_QUERY);
        this.targetIp = targetIp;
        this.sourceIp=sourceIp;
        this.token = token;
    }

    public DirectoryQuery(String targetIp, String sourceIp,String token, String targetPath) {
        super(CommunicationType.DIRECTORY_QUERY);
        this.sourceIp = sourceIp;
        this.targetIp = targetIp;
        this.token = token;
        this.targetPath = targetPath;
    }
    /*public DirectoryQuery(String targetPath, String token) {
        super(CommunicationType.DIRECTORY_QUERY);
        this.targetPath = targetPath;
        this.token = token;
    }*/

    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
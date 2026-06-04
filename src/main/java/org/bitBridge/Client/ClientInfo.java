package org.bitBridge.Client;

import java.io.Serializable;
import java.net.Socket;
import java.util.UUID;

public class ClientInfo implements Serializable {
    private static final long serialVersionUID = 1L; // Asegura la compatibilidad al serializar en red

    private String address;
    private long connectionTime;
    private String nick;
    private int port;

    // --- NUEVOS ATRIBUTOS PARA ENRIQUECER LA INFORMACIÓN OPERATIVA ---
    private String clientId;        // Identificador único (UUID) asignado a la sesión del cliente
    private String osName;          // Sistema operativo del cliente (para soporte/diagnóstico)
    private String clientVersion;   // Versión del software cliente (evita incompatibilidades de protocolo)
    private String status;          // Estado actual en el ciclo de vida (ej: "CONECTADO", "IDLE", "TRANSFRIENDO")

    private long lastHeartbeat;     // Marca de tiempo del último paquete de control recibido (Keep-Alive)
    private long totalBytesSent;    // Historial de bytes enviados por este cliente en la sesión
    private long totalBytesReceived;// Historial de bytes recibidos por este cliente en la sesión

    // Constructor para pruebas o cuando el socket no está disponible inmediatamente
    public ClientInfo(String address, String nick, int puerto) {
        // Validación preventiva antes de manipular la subcadena
        this.address = (address != null && address.startsWith("/")) ? address.substring(1) : address;
        this.nick = nick;
        this.port = puerto;
        this.connectionTime = System.currentTimeMillis();

        // Inicialización de la nueva información extendida
        this.clientId = UUID.randomUUID().toString();
        this.osName = System.getProperty("os.name", "Desconocido");
        this.clientVersion = "1.0.0";
        this.status = "CONECTADO";
        this.lastHeartbeat = this.connectionTime;
        this.totalBytesSent = 0;
        this.totalBytesReceived = 0;
    }

    public ClientInfo(String nick) {
        this.nick = nick;
        this.connectionTime = System.currentTimeMillis();
        this.clientId = UUID.randomUUID().toString();
        this.osName = System.getProperty("os.name", "Desconocido");
        this.clientVersion = "1.0.0";
        this.status = "CONECTADO";
        this.lastHeartbeat = this.connectionTime;
    }

    // --- MÉTODOS EXISTENTES PRESERVADOS ---

    public String getAddress() {
        return address;
    }

    public long getConnectionTime() {
        //return System.currentTimeMillis() - connectionTime;
        return connectionTime;
    }

    public String formatUptime() {
        long uptime = System.currentTimeMillis() - connectionTime;
        long seconds = (uptime / 1000) % 60;
        long minutes = (uptime / (1000 * 60)) % 60;
        long hours = (uptime / (1000 * 60 * 60)) % 24;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getNick() {
        return this.nick;
    }

    // --- NUEVOS MÉTODOS ACCESORES Y DE CONTROL ---

    public String getClientId() {
        return clientId;
    }

    public String getOsName() {
        return osName;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Actualiza la marca de tiempo para verificar que el cliente sigue vivo (Keep-Alive).
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Verifica si el cliente ha superado un tiempo límite de inactividad (Timeout).
     * @param timeoutMillis Tiempo máximo permitido en milisegundos sin recibir señales.
     */
    public boolean isTimedOut(long timeoutMillis) {
        return (System.currentTimeMillis() - lastHeartbeat) > timeoutMillis;
    }

    public long getTotalBytesSent() {
        return totalBytesSent;
    }

    public void addBytesSent(long bytes) {
        this.totalBytesSent += bytes;
    }

    public long getTotalBytesReceived() {
        return totalBytesReceived;
    }

    public void addBytesReceived(long bytes) {
        this.totalBytesReceived += bytes;
    }

    @Override
    public String toString() {
        return "ClientInfo{" +
                "clientId='" + clientId + '\'' +
                ", nick='" + nick + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", status='" + status + '\'' +
                ", osName='" + osName + '\'' +
                ", clientVersion='" + clientVersion + '\'' +
                ", connectionTime=" + connectionTime +
                ", uptime=" + formatUptime() +
                ", bytesSent=" + totalBytesSent +
                ", bytesReceived=" + totalBytesReceived +
                '}';
    }
}
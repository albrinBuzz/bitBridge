package org.bitBridge.shared.core.comunication.model.basic.telemetry;

import org.bitBridge.shared.core.comunication.Communication;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO Estático para el Apretón de Manos (Handshake) inicial de telemetría.
 * Contiene metadatos de entorno, hardware e índices fijos que no cambian en ejecución.
 */
public class TelemetryHandshakePacket extends Communication {

    private static final long serialVersionUID = 20260605L;

    // --- ENTORNO E INFRAESTRUCTURA INMUTABLE ---
    public String coreVersion = "v4.0.2-STABLE";
    public String osName = "UNKNOWN";
    public String osVersion = "UNKNOWN";
    public String osArch = "UNKNOWN";
    public String username = "UNKNOWN";
    public String javaVersion = "UNKNOWN";
    public String vmName = "UNKNOWN";

    // --- CAPACIDADES DEL HARDWARE ---
    public int cpuCores = 1;
    public long totalPhysicalMemory = 0L;
    public long maxMemory = 0L; // Límite -Xmx de la JVM
    public long maxFileDescriptors = 0L; // Límite de sockets/archivos permitidos por el Kernel Linux

    // --- CONFIGURACIÓN DE RED FIJA ---
    public int port = 0;
    public String currentPrimaryIp = "127.0.0.1";
    public String activeInterfaceName = "LOOPBACK";

    // --- TOPOLOGÍA DE RED BASE (Se extrae una única vez al conectar) ---
    public List<NetworkInterfaceStaticDTO> networkTopology = new ArrayList<>();

    // Record anidado ligero para la topología base
    public record NetworkInterfaceStaticDTO(
            String typeStr,
            String name,
            int mtu,
            String displayName,
            List<String> addresses
    ) implements Serializable {}
}
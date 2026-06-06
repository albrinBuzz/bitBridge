package org.bitBridge.shared.core.comunication.model.basic.telemetry;

import org.bitBridge.shared.core.comunication.Communication;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO que empaqueta las métricas de infraestructura avanzada del servidor.
 * Refactorizado: Valores por defecto asignados para evitar fugas de nulos en red.
 */
public class TelemetryPacket extends Communication {

    private static final long serialVersionUID = 20260605L;

    // --- METADATOS Y CONTROL GENERAL ---
    public String uptime = "00:00:00";
    public String coreVersion = "v4.0.2-STABLE";
    public String osVersion = "UNKNOWN";
    public String osName = "UNKNOWN";
    public String osArch = "UNKNOWN";
    public String username = "UNKNOWN";
    public String healthText = "ESTABLE";
    public String healthColor = "#55efc4";
    public String javaVersion = "UNKNOWN";
    public String vmName = "UNKNOWN";

    // --- ADVANCED HARDWARE & OPERATING SYSTEM ---
    public int cpuCores = 1;
    public double systemCpuLoad = 0.0;
    public double processCpuLoad = 0.0;
    public String loadColor = "#55efc4";
    public long totalPhysicalMemory = 0L;
    public long freePhysicalMemory = 0L;
    public long usedPhysicalMemory = 0L;
    public int systemRamPercent = 0;
    public long openFileDescriptors = 0L;
    public long maxFileDescriptors = 0L;

    // --- RUNTIME METRICS (JVM HEAP & BUFFER POOLS) ---
    public long heapUsed = 0L;
    public long heapCommitted = 0L;
    public long maxMemory = 0L;
    public long freeMemory = 0L;
    public long directMemoryUsed = 0L;
    public long directBufferCount = 0L;
    public long mappedMemoryUsed = 0L;
    public int ramPercent = 0;

    // --- SUBSISTEMAS JVM (GC & JIT COMPILER) ---
    public long gcCollectionCount = 0L;
    public long gcCollectionTimeMs = 0L;
    public String gcName = "GENERIC";
    public long jitCompileTimeMs = 0L;
    public long totalLoadedClassCount = 0L;

    // --- PROCESS & THREAD INSPECTOR ---
    public int bitBridgeWorkers = 0;
    public int totalThreadsCount = 0;
    public int daemonThreadsCount = 0;
    public int userThreadsCount = 0;
    public int peakThreadsCount = 0;
    public double threadLoad = 0.0;
    public List<ThreadDTO> threadDetails = new ArrayList<>();

    // --- METRICAS DE RED (FLOW & VOLUMEN) ---
    public String currentPrimaryIp = "127.0.0.1";
    public int port = 0;
    public String activeInterfaceName = "LOOPBACK";
    public long totalMessages = 0L;
    public long totalBytesTransferred = 0L;
    public double currentKbs = 0.0;
    public String trafficBar = "[............]";

    // --- LISTAS DINÁMICAS ---
    public List<String> shortLogHistory = new ArrayList<>();
    public List<ActiveNodeDTO> connectedNodes = new ArrayList<>();
    public List<NetworkInterfaceDTO> networkTopology = new ArrayList<>();

    // ==========================================
    // DATA TRANSFER OBJECTS (DTOs SERIALIZABLES)
    // ==========================================
    public record ThreadDTO(long id, String name, String state, int priority, String type) implements Serializable {}
    public record ActiveNodeDTO(String address, String session, String nick, String status) implements Serializable {}
    public record NetworkInterfaceDTO(String typeStr, String name, int mtu, String displayName, List<IpAddressDTO> addresses) implements Serializable {}
    public record IpAddressDTO(String ip, boolean isPrimary) implements Serializable {}
}
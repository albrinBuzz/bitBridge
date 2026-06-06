package org.bitBridge.shared.core.comunication.model.basic.telemetry;

import org.bitBridge.shared.core.comunication.Communication;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO Dinámico de alta frecuencia para Streaming de Telemetría.
 * Transporta exclusivamente variables que cambian milisegundo a milisegundo.
 */
public class TelemetryStreamPacket extends Communication {

    private static final long serialVersionUID = 20260606L;

    // --- CONTROL DE SALUD Y TIEMPO ---
    public String uptime = "00:00:00";
    public long timestamp = System.currentTimeMillis();
    public String healthText = "ESTABLE";
    public String healthColor = "#55efc4";

    // --- MÉTRICAS DE CARGA FLUTUANTE (OS & HARDWARE) ---
    public double systemCpuLoad = 0.0;
    public double processCpuLoad = 0.0;
    public String loadColor = "#55efc4";
    public long freePhysicalMemory = 0L;
    public long usedPhysicalMemory = 0L;
    public int systemRamPercent = 0;
    public long openFileDescriptors = 0L; // Sockets de red + archivos abiertos concurrentemente

    // --- DETALLES DE MEMORIA RUNTIME (JVM HEAP & DIRECT PACKETS) ---
    public long heapUsed = 0L;
    public long heapCommitted = 0L;
    public long freeMemory = 0L;
    public int ramPercent = 0;
    public long directMemoryUsed = 0L;  // Esencial para analizar buffers de transferencia Zero-Copy
    public long directBufferCount = 0L;
    public long mappedMemoryUsed = 0L;

    // --- ESTADO INTERNO DE LA JVM (GC & COMPILADOR EN TIEMPO REAL) ---
    public long gcCollectionCount = 0L;
    public long gcCollectionTimeMs = 0L;
    public String gcName = "GENERIC";
    public long jitCompileTimeMs = 0L;
    public long totalLoadedClassCount = 0L;

    // --- PROCESOS E INSPECCIÓN LIVE DE HILOS ---
    public int totalThreadsCount = 0;
    public int daemonThreadsCount = 0;
    public int userThreadsCount = 0;
    public int peakThreadsCount = 0;
    public int bitBridgeWorkers = 0;
    public double threadLoad = 0.0;
    public List<ThreadLiveDTO> threadDetails = new ArrayList<>();

    // --- TRAFICO DE RED E I/O DEL SISTEMA DE ARCHIVOS (MÉTRICAS DEL CORE) ---
    public long totalMessages = 0L;
    public long totalBytesTransferred = 0L;
    public double currentKbs = 0.0;
    public String trafficBar = "[............]";

    // --- NUEVAS MÉTRICAS OPERATIVAS DE TRANSFERENCIA ---
    public int activeTransfersCount = 0;      // Cuántos archivos se están transmitiendo en paralelo
    public double diskReadSpeedMbs = 0.0;     // Velocidad de lectura en el almacenamiento local
    public double diskWriteSpeedMbs = 0.0;    // Velocidad de escritura en disco

    // --- COPIAS DE LISTAS VOLÁTILES ---
    public List<String> shortLogHistory = new ArrayList<>();
    public List<ActiveNodeLiveDTO> connectedNodes = new ArrayList<>();

    // Records ligeros para el stream periódico
    public record ThreadLiveDTO(long id, String name, String state, int priority, String type) implements Serializable {}
    public record ActiveNodeLiveDTO(String address, String nick, String status) implements Serializable {}
}
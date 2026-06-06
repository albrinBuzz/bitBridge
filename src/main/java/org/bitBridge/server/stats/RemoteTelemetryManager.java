package org.bitBridge.server.stats;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.Server;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.shared.Logger;


import org.bitBridge.shared.core.comunication.model.basic.telemetry.TelemetryHandshakePacket;
import org.bitBridge.shared.core.comunication.model.basic.telemetry.TelemetryStreamPacket;
import org.bitBridge.shared.network.NetworkManager;

import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ThreadInfo;
import com.sun.management.OperatingSystemMXBean;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;

public class RemoteTelemetryManager {

    private final Server server;
    private final Set<BitBridgeClient> listeners = ConcurrentHashMap.newKeySet();

    // Planificador optimizado basado en hilos virtuales corriendo a intervalos saludables (1 segundo)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("Telemetry-Worker-", 1).factory()
    );

    private long lastTotalBytes = 0;
    private long lastTimestamp = System.currentTimeMillis();

    public RemoteTelemetryManager(Server server) {
        this.server = server;
        // Intervalo fijado a 1 segundo para preservar ciclos de CPU en el Core
        this.scheduler.scheduleAtFixedRate(this::broadcastTelemetry, 1, 1, TimeUnit.SECONDS);
    }

    public void registerListener(BitBridgeClient client) {
        if (client == null) return;
        listeners.add(client);

        // Despachar la foto fija de infraestructura de forma asíncrona e inmediata al conectar
        CompletableFuture.runAsync(() -> {
            try {
                TelemetryHandshakePacket handshake = compileStaticHandshake();
                client.sendComunicacion(handshake);
            } catch (Exception e) {
                Logger.logError("[TELEMETRY] Error enviando handshake inicial, desalojando: " + e.getMessage());
                listeners.remove(client);
            }
        });
    }

    public void unregisterListener(BitBridgeClient client) { if (client != null) listeners.remove(client); }

    private void broadcastTelemetry() {
        if (listeners.isEmpty()) return;
        try {
            TelemetryStreamPacket streamPacket = compileStreamTelemetry();
            //server.getStats().recordBytes(sp.getClass().size);
            for (BitBridgeClient client : listeners) {
                try {
                    client.sendComunicacion(streamPacket);
                } catch (Exception ex) {
                    Logger.logError("[TELEMETRY] Error de conexión persistente en stream, removiendo cliente.");
                    listeners.remove(client);
                }
            }
        } catch (Exception e) {
            Logger.logError("[TELEMETRY] Fallo crítico al procesar el broadcast de métricas dinámicas: " + e.getMessage());
        }
    }

    /**
     * Compila la información de infraestructura rígida. Solo se llama en el apretón de manos.
     */
    private TelemetryHandshakePacket compileStaticHandshake() {
        TelemetryHandshakePacket hp = new TelemetryHandshakePacket();
        var osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        hp.port = server.getPORT();
        hp.coreVersion = "v4.0.2-STABLE";
        hp.osName = System.getProperty("os.name", "Linux");
        hp.osVersion = System.getProperty("os.version", "Unknown");
        hp.osArch = System.getProperty("os.arch", "amd64");
        hp.username = System.getProperty("user.name", "root");
        hp.javaVersion = System.getProperty("java.version", "21");
        hp.vmName = System.getProperty("java.vm.name", "JVM");
        hp.cpuCores = osBean.getAvailableProcessors();
        hp.totalPhysicalMemory = osBean.getTotalMemorySize();
        hp.maxMemory = Runtime.getRuntime().maxMemory();
        //hp.maxFileDescriptors = osBean.getMaxFileDescriptorCount();

        try {
            List<InetAddress> allLocalIps = NetworkManager.getAllLocalIps();
            hp.currentPrimaryIp = (allLocalIps == null || allLocalIps.isEmpty()) ? "127.0.0.1" : allLocalIps.get(0).getHostAddress();
            hp.activeInterfaceName = getActiveInterfaceName(allLocalIps);
            hp.networkTopology = compileNetworkTopology(hp.currentPrimaryIp);
        } catch (Exception ignored) {}

        return hp;
    }

    /**
     * Compila estrictamente las variables volátiles y las métricas de rendimiento en tiempo real.
     */
    private TelemetryStreamPacket compileStreamTelemetry() {
        Runtime r = Runtime.getRuntime();
        ServerStats stats = server.getStats();
        TelemetryStreamPacket sp = new TelemetryStreamPacket();

        var osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        var threadBean = ManagementFactory.getThreadMXBean();

        // --- SALUD GENERAL Y TIEMPO ---
        sp.uptime = stats.getUptime() != null ? stats.getUptime() : "00:00:00";
        sp.totalMessages = stats.getTotalMessages();

        // --- HARDWARE DYNAMIC METRICS ---
        double sysCpu = osBean.getCpuLoad() * 100.0;
        sp.systemCpuLoad = sysCpu >= 0 ? sysCpu : 0.0;
        double procCpu = osBean.getProcessCpuLoad() * 100.0;
        sp.processCpuLoad = procCpu >= 0 ? procCpu : 0.0;
        sp.loadColor = (sp.systemCpuLoad > 80.0) ? "#e74c3c" : (sp.systemCpuLoad > 50.0 ? "#fdcb6e" : "#55efc4");

        sp.freePhysicalMemory = osBean.getFreeMemorySize();
        long totalPhysical = osBean.getTotalMemorySize();
        sp.usedPhysicalMemory = Math.max(0, totalPhysical - sp.freePhysicalMemory);
        if (totalPhysical > 0) {
            sp.systemRamPercent = (int) ((sp.usedPhysicalMemory * 100) / totalPhysical);
        }
        //sp.openFileDescriptors = osBean.getOpenFileDescriptorCount();

        // --- JVM HEAP & BUFFER POOLS ---
        sp.heapCommitted = r.totalMemory();
        sp.freeMemory = r.freeMemory();
        sp.heapUsed = sp.heapCommitted - sp.freeMemory;
        long maxMem = r.maxMemory();
        if (maxMem > 0) {
            sp.ramPercent = (int) ((sp.heapUsed * 100) / maxMem);
        }
        extractLiveBufferPoolMetrics(sp);

        // --- SUBSISTEMAS (GC & JIT) ---
        try {
            var classBean = ManagementFactory.getClassLoadingMXBean();
            sp.totalLoadedClassCount = classBean.getTotalLoadedClassCount();
            var compBean = ManagementFactory.getCompilationMXBean();
            if (compBean != null && compBean.isCompilationTimeMonitoringSupported()) {
                sp.jitCompileTimeMs = compBean.getTotalCompilationTime();
            }

            long totalCollections = 0;
            long totalGcTime = 0;
            StringBuilder gcNames = new StringBuilder();
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                long count = gc.getCollectionCount();
                if (count > -1) {
                    totalCollections += count;
                    totalGcTime += gc.getCollectionTime();
                    gcNames.append(gc.getName()).append(", ");
                }
            }
            sp.gcCollectionCount = totalCollections;
            sp.gcCollectionTimeMs = totalGcTime;
            sp.gcName = gcNames.isEmpty() ? "GENERIC" : gcNames.substring(0, gcNames.length() - 2);
        } catch (Exception ignored) {}

        // --- FLUJO DE RENDIMIENTO DE TRAFICO E I/O ---
        long currentBytes = stats.getTotalBytes();
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - lastTimestamp;
        if (timeDelta > 0) {
            double bytesPerSecond = (double) (currentBytes - lastTotalBytes) / (timeDelta / 1000.0);
            sp.currentKbs = Math.max(0.0, bytesPerSecond / 1024.0);
        }
        lastTotalBytes = currentBytes;
        lastTimestamp = currentTime;
        sp.totalBytesTransferred = currentBytes;
        sp.trafficBar = generateTrafficBar(sp.currentKbs);

        // Inyección de métricas específicas de transferencia mapeadas en el core de ServerStats
        /*sp.activeTransfersCount = stats.getActiveTransfersCount();
        sp.diskReadSpeedMbs = stats.getDiskReadSpeedMbs();
        sp.diskWriteSpeedMbs = stats.getDiskWriteSpeedMbs();*/

        // --- INSPECCIÓN INDUSTRIAL OPTIMIZADA DE HILOS (Evita Safepoints pesados) ---
        sp.peakThreadsCount = threadBean.getPeakThreadCount();
        sp.totalThreadsCount = threadBean.getThreadCount();
        sp.daemonThreadsCount = threadBean.getDaemonThreadCount();
        sp.userThreadsCount = Math.max(0, sp.totalThreadsCount - sp.daemonThreadsCount);

        // dumpAllThreads(false, false) no extrae stacktraces completos, solo identificadores y estados nativos
        ThreadInfo[] liveThreads = threadBean.dumpAllThreads(false, false);
        for (ThreadInfo info : liveThreads) {
            if (info == null) continue;
            String name = info.getThreadName();
            boolean isDaemon = info.isDaemon();

            if (name != null && name.matches(".*(Worker|BitBridge|FT-Pool|NIO).*")) sp.bitBridgeWorkers++;

            sp.threadDetails.add(new TelemetryStreamPacket.ThreadLiveDTO(
                    info.getThreadId(),
                    name != null ? name.toUpperCase() : "UNKNOWN-THREAD",
                    info.getThreadState().toString(),
                    info.getPriority(),
                    isDaemon ? "DAEMON" : "USER"
            ));
        }
        int maxExpectedThreads = Math.max(1, osBean.getAvailableProcessors() * 150);
        sp.threadLoad = (sp.totalThreadsCount * 100.0) / maxExpectedThreads;

        // --- LOGS Y NODOS ACTIVOS ---
        int activeClients = stats.getClientCount();
        sp.healthText = (sp.systemCpuLoad > 85.0 || sp.ramPercent > 90) ? "CRÍTICO" : (activeClients > sp.totalThreadsCount * 0.8) ? "ESTRESADO" : "ESTABLE";
        sp.healthColor = sp.healthText.equals("CRÍTICO") ? "#e74c3c" : sp.healthText.equals("ESTRESADO") ? "#fdcb6e" : "#55efc4";

        List<String> logs = stats.getMessageHistory();
        if (logs != null && !logs.isEmpty()) {
            sp.shortLogHistory = new ArrayList<>(logs.subList(Math.max(0, logs.size() - 5), logs.size()));
        }

        Collection<ClientInfo> connected = stats.getConnectedClients();
        if (connected != null) {
            for (ClientInfo c : connected) {
                if (c == null) continue;
                sp.connectedNodes.add(new TelemetryStreamPacket.ActiveNodeLiveDTO(
                        c.getAddress() != null ? c.getAddress() : "0.0.0.0",
                        c.getNick() != null ? c.getNick().toUpperCase() : "ANONYMOUS",
                        "ONLINE"
                ));
            }
        }


        return sp;
    }

    private void extractLiveBufferPoolMetrics(TelemetryStreamPacket sp) {
        try {
            for (var pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                String name = pool.getName();
                if ("direct".equals(name)) {
                    sp.directMemoryUsed = pool.getMemoryUsed();
                    sp.directBufferCount = pool.getCount();
                } else if ("mapped".equals(name)) {
                    sp.mappedMemoryUsed = pool.getMemoryUsed();
                }
            }
        } catch (Exception ignored) {}
    }

    private String getActiveInterfaceName(List<InetAddress> ips) {
        if (ips == null || ips.isEmpty()) return "LOOPBACK";
        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(ips.get(0));
            return (ni != null) ? ni.getName().toUpperCase() : "UNKNOWN";
        } catch (SocketException e) { return "ERR_NET"; }
    }

    private String generateTrafficBar(double kbs) {
        int segments = 12;
        int filled = (int) Math.min(segments, (kbs / 1024.0) * segments);
        return "[" + "■".repeat(filled) + ".".repeat(segments - filled) + "]";
    }

    private List<TelemetryHandshakePacket.NetworkInterfaceStaticDTO> compileNetworkTopology(String primaryIp) {
        List<TelemetryHandshakePacket.NetworkInterfaceStaticDTO> topology = new ArrayList<>();
        try {
            var nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nets)) {
                if (ni == null || !ni.isUp()) continue;
                String name = ni.getName().toUpperCase();
                boolean isVirtual = ni.isVirtual() || name.matches(".*(VBOX|DOCKER|VETH|VIRBR).*");
                String typeStr = name.startsWith("EN") || name.startsWith("ETH") ? "🔌 [ETHERNET]" :
                        name.startsWith("WL") ? "📶 [WI-FI]" : isVirtual ? "📦 [VIRTUAL]" :
                                ni.isLoopback() ? "🔄 [LOOPBACK]" : "🌐 [NETWORK]";

                List<String> addrList = new ArrayList<>();
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address)) continue;
                    addrList.add(addr.getHostAddress());
                }
                topology.add(new TelemetryHandshakePacket.NetworkInterfaceStaticDTO(typeStr, name, ni.getMTU(), ni.getDisplayName(), addrList));
            }
        } catch (Exception ignored) {}
        return topology;
    }

    public void shutdown() { scheduler.shutdown(); }
}
package org.bitBridge.server.stats;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.server.core.Server;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.TelemetryPacket;
import org.bitBridge.shared.network.NetworkManager;

import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.BufferPoolMXBean;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;

public class RemoteTelemetryManager {

    private final Server server;
    private final Set<BitBridgeClient> listeners = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("Telemetry-Hub-", 1).factory()
    );

    private long lastTotalBytes = 0;
    private long lastTimestamp = System.currentTimeMillis();

    public RemoteTelemetryManager(Server server) {
        this.server = server;
        this.scheduler.scheduleAtFixedRate(this::broadcastTelemetry, 1, 1, TimeUnit.SECONDS);
    }

    public void registerListener(BitBridgeClient client) { if (client != null) listeners.add(client); }
    public void unregisterListener(BitBridgeClient client) { if (client != null) listeners.remove(client); }

    private void broadcastTelemetry() {
        if (listeners.isEmpty()) return;
        try {
            TelemetryPacket packet = compileFullTelemetry();
            for (BitBridgeClient client : listeners) {
                try {
                    client.sendComunicacion(packet);
                } catch (Exception ex) {
                    Logger.logError("[TELEMETRY] Error enviando a cliente, removiendo... : " + ex.getMessage());
                    listeners.remove(client);
                }
            }
        } catch (Exception e) {
            Logger.logError("[TELEMETRY] Fallo crítico al compilar broadcast: " + e.getMessage());
        }
    }

    private TelemetryPacket compileFullTelemetry() {
        Runtime r = Runtime.getRuntime();
        ServerStats stats = server.getStats();
        TelemetryPacket p = new TelemetryPacket();

        // --- ENTORNO PROCESO ---
        try {
            p.uptime = stats.getUptime() != null ? stats.getUptime() : "00:00:00";
            p.osName = System.getProperty("os.name", "Linux");
            p.osVersion = System.getProperty("os.version", "Unknown");
            p.osArch = System.getProperty("os.arch", "amd64");
            p.username = System.getProperty("user.name", "root");
            p.javaVersion = System.getProperty("java.version", "21");
            p.vmName = System.getProperty("java.vm.name", "JVM");
            p.totalMessages = stats.getTotalMessages();
        } catch (Exception ignored) {}

        // --- MXBEANS Y HARDWARE NATIVO (PROTECCIÓN CONTRA VALORES MENORES A CERO) ---
        try {
            var osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            p.cpuCores = osBean.getAvailableProcessors();

            double sysCpu = osBean.getCpuLoad() * 100.0;
            p.systemCpuLoad = sysCpu >= 0 ? sysCpu : 0.0;

            double procCpu = osBean.getProcessCpuLoad() * 100.0;
            p.processCpuLoad = procCpu >= 0 ? procCpu : 0.0;

            p.totalPhysicalMemory = osBean.getTotalMemorySize();
            p.freePhysicalMemory = osBean.getFreeMemorySize();
            p.usedPhysicalMemory = Math.max(0, p.totalPhysicalMemory - p.freePhysicalMemory);

            if (p.totalPhysicalMemory > 0) {
                p.systemRamPercent = (int) ((p.usedPhysicalMemory * 100) / p.totalPhysicalMemory);
            }
        } catch (Exception e) {
            p.systemCpuLoad = 0.0; p.processCpuLoad = 0.0;
        }

        // --- CONTROL DE COLORES Y ALERTA TEMPRANA ---
        p.loadColor = (p.systemCpuLoad > 80.0) ? "#e74c3c" : (p.systemCpuLoad > 50.0 ? "#fdcb6e" : "#55efc4");

        // --- JVM HEAP INTERNAL METRICS ---
        p.heapCommitted = r.totalMemory();
        p.freeMemory = r.freeMemory();
        p.heapUsed = p.heapCommitted - p.freeMemory;
        p.maxMemory = r.maxMemory();
        if (p.maxMemory > 0) {
            p.ramPercent = (int) ((p.heapUsed * 100) / p.maxMemory);
        }

        // --- COMPILACIÓN, CLASES Y BUFFERS ---
        try {
            var classBean = ManagementFactory.getClassLoadingMXBean();
            p.totalLoadedClassCount = classBean.getTotalLoadedClassCount();

            var compBean = ManagementFactory.getCompilationMXBean();
            if (compBean != null && compBean.isCompilationTimeMonitoringSupported()) {
                p.jitCompileTimeMs = compBean.getTotalCompilationTime();
            }
            extractBufferPoolMetrics(p);
        } catch (Exception ignored) {}

        // --- GARBAGE COLLECTOR INSPECTOR ---
        try {
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
            p.gcCollectionCount = totalCollections;
            p.gcCollectionTimeMs = totalGcTime;
            p.gcName = gcNames.isEmpty() ? "GENERIC" : gcNames.substring(0, gcNames.length() - 2);
        } catch (Exception e) {
            p.gcName = "GENERIC_ERR";
        }

        // --- RED Y TRAFICO (EVITA INTERRUPCIONES SI RED FALLA) ---
        try {
            List<InetAddress> allLocalIps = NetworkManager.getAllLocalIps();
            p.currentPrimaryIp = (allLocalIps == null || allLocalIps.isEmpty()) ? "127.0.0.1" : allLocalIps.get(0).getHostAddress();
            p.port = server.getPORT();
            p.activeInterfaceName = getActiveInterfaceName(allLocalIps);

            long currentBytes = stats.getTotalBytes();
            long currentTime = System.currentTimeMillis();
            long timeDelta = currentTime - lastTimestamp;
            if (timeDelta > 0) {
                double bytesPerSecond = (double) (currentBytes - lastTotalBytes) / (timeDelta / 1000.0);
                p.currentKbs = Math.max(0.0, bytesPerSecond / 1024.0);
            }
            lastTotalBytes = currentBytes;
            lastTimestamp = currentTime;
            p.totalBytesTransferred = currentBytes;
            p.trafficBar = generateTrafficBar(p.currentKbs);
        } catch (Exception ignored) {}

        // --- INSPECCIÓN SEGURA DE HILOS (COPIA SNAPSHOT EVITA CONCURRENTMODIFICATION) ---
        try {
            var threadBean = ManagementFactory.getThreadMXBean();
            p.peakThreadsCount = threadBean.getPeakThreadCount();

            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
            p.totalThreadsCount = allThreads.size();
            p.threadDetails = new ArrayList<>();
            p.bitBridgeWorkers = 0;
            p.daemonThreadsCount = 0;

            for (Thread t : allThreads.keySet()) {
                if (t == null) continue;
                boolean isDaemon = t.isDaemon();
                String name = t.getName();

                if (isDaemon) p.daemonThreadsCount++;
                if (name != null && name.matches(".*(Worker|BitBridge|FT-Pool).*")) p.bitBridgeWorkers++;

                p.threadDetails.add(new TelemetryPacket.ThreadDTO(
                        t.getId(),
                        name != null ? name.toUpperCase() : "UNKNOWN-THREAD",
                        t.getState() != null ? t.getState().toString() : "UNKNOWN",
                        t.getPriority(),
                        isDaemon ? "DAEMON" : "USER"
                ));
            }
            p.userThreadsCount = Math.max(0, p.totalThreadsCount - p.daemonThreadsCount);

            int maxExpectedThreads = Math.max(1, p.cpuCores * 150);
            p.threadLoad = (p.totalThreadsCount * 100.0) / maxExpectedThreads;
        } catch (Exception ignored) {}

        // --- ESTADOS DINÁMICOS DE SALUD INFRAESTRUCTURA ---
        int activeClients = stats.getClientCount();
        p.healthText = (p.systemCpuLoad > 85.0 || p.ramPercent > 90) ? "CRÍTICO" : (activeClients > p.totalThreadsCount * 0.8) ? "ESTRESADO" : "ESTABLE";
        p.healthColor = p.healthText.equals("CRÍTICO") ? "#e74c3c" : p.healthText.equals("ESTRESADO") ? "#fdcb6e" : "#55efc4";

        // --- ENTRADAS DINÁMICAS (LOGS, NODOS Y TOPOLOGÍA) ---
        try {
            List<String> logs = stats.getMessageHistory();
            if (logs != null && !logs.isEmpty()) {
                p.shortLogHistory = new ArrayList<>(logs.subList(Math.max(0, logs.size() - 10), logs.size()));
            }

            p.connectedNodes = new ArrayList<>();
            Collection<ClientInfo> connected = stats.getConnectedClients();
            if (connected != null) {
                for (ClientInfo c : connected) {
                    if (c == null) continue;
                    p.connectedNodes.add(new TelemetryPacket.ActiveNodeDTO(
                            c.getAddress() != null ? c.getAddress() : "0.0.0.0",
                            "ACT",
                            c.getNick() != null ? c.getNick().toUpperCase() : "ANONYMOUS",
                            "ONLINE"
                    ));
                }
            }
            p.networkTopology = compileNetworkTopology(p.currentPrimaryIp);
        } catch (Exception ignored) {}

        return p;
    }

    private void extractBufferPoolMetrics(TelemetryPacket p) {
        try {
            for (var pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                String name = pool.getName();
                if ("direct".equals(name)) {
                    p.directMemoryUsed = pool.getMemoryUsed();
                    p.directBufferCount = pool.getCount();
                } else if ("mapped".equals(name)) {
                    p.mappedMemoryUsed = pool.getMemoryUsed();
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

    private List<TelemetryPacket.NetworkInterfaceDTO> compileNetworkTopology(String primaryIp) {
        List<TelemetryPacket.NetworkInterfaceDTO> topology = new ArrayList<>();
        try {
            var nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nets)) {
                if (ni == null || !ni.isUp()) continue;
                String name = ni.getName().toUpperCase();
                boolean isVirtual = ni.isVirtual() || name.matches(".*(VBOX|DOCKER|VETH|VIRBR).*");
                String typeStr = name.startsWith("EN") || name.startsWith("ETH") ? "🔌 [ETHERNET]" :
                        name.startsWith("WL") ? "📶 [WI-FI]" : isVirtual ? "📦 [VIRTUAL]" :
                                ni.isLoopback() ? "🔄 [LOOPBACK]" : "🌐 [NETWORK]";

                List<TelemetryPacket.IpAddressDTO> addrDTOs = new ArrayList<>();
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address)) continue;
                    String ip = addr.getHostAddress();
                    addrDTOs.add(new TelemetryPacket.IpAddressDTO(ip, ip.equals(primaryIp)));
                }
                topology.add(new TelemetryPacket.NetworkInterfaceDTO(typeStr, name, ni.getMTU(), ni.getDisplayName(), addrDTOs));
            }
        } catch (Exception ignored) {}
        return topology;
    }

    public void shutdown() { scheduler.shutdown(); }
}
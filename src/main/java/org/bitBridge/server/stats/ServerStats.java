package org.bitBridge.server.stats;


import org.bitBridge.Client.ClientInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ServerStats {
    private final long startTime;
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);

    // Listas concurrentes para evitar errores de modificación mientras se renderiza
    private final List<String> messageHistory = new CopyOnWriteArrayList<>();
    private final List<ClientInfo> connectedClients = new CopyOnWriteArrayList<>();

    public ServerStats() {
        this.startTime = System.currentTimeMillis();
    }

    // --- Métodos de Actualización (Usados por el Server) ---
    public void recordMessage(String message) {
        totalMessages.incrementAndGet();
        messageHistory.add(message);
        if (messageHistory.size() > 50) messageHistory.remove(0); // Mantener buffer manejable
    }

    public void recordBytes(long bytes) { totalBytes.addAndGet(bytes); }

    public void setClients(List<ClientInfo> clients) {
        connectedClients.clear();
        connectedClients.addAll(clients);
    }
    public void addClient(ClientInfo clientInfo){
        connectedClients.add(clientInfo);
    }

    // --- Métodos de Consulta (Usados por la Consola) ---
    public String getUptime() {
        long uptime = System.currentTimeMillis() - startTime;
        long seconds = (uptime / 1000) % 60;
        long minutes = (uptime / (1000 * 60)) % 60;
        long hours = (uptime / (1000 * 60 * 60)) % 24;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public String getMemoryUsageFormat() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long used = memoryBean.getHeapMemoryUsage().getUsed();
        long max = memoryBean.getHeapMemoryUsage().getMax();
        return formatBytes(used) + " / " + formatBytes(max);
    }

    public String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %cB", bytes / Math.pow(1024, exp), pre);
    }
    // Agrega esto a tu clase ServerStats
    public String getMaxMemoryFormat() {
        double maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0);
        return (maxMemoryMB >= 1024)
                ? String.format("%.2f GB", maxMemoryMB / 1024)
                : String.format("%.2f MB", maxMemoryMB);
    }

    public String formatUptime(long connectionTime) {
        long uptime = System.currentTimeMillis() - connectionTime;
        long seconds = (uptime / 1000) % 60;
        long minutes = (uptime / (1000 * 60)) % 60;
        long hours = (uptime / (1000 * 60 * 60)) % 24;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    public long getTotalMessages() { return totalMessages.get(); }
    public long getTotalBytes() { return totalBytes.get(); }
    public List<String> getMessageHistory() { return messageHistory; }
    public List<ClientInfo> getConnectedClients() { return connectedClients; }
    public int getClientCount() { return connectedClients.size(); }
}
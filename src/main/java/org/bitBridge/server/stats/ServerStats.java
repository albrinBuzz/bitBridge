package org.bitBridge.server.stats;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.utils.Color;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ServerStats {
    private final long startTime;
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final List<String> messageHistory = new CopyOnWriteArrayList<>();
    private final List<ClientInfo> connectedClients = new CopyOnWriteArrayList<>();

    public ServerStats() {
        this.startTime = System.currentTimeMillis();
    }

    // --- MÉTODOS DE COMPATIBILIDAD (Para evitar errores de compilación) ---
    public void recordMessage(String message) { addMessage(message); }
    public void setClients(List<ClientInfo> clients) {
        connectedClients.clear();
        connectedClients.addAll(clients);
    }
    public long getTotalMessages() { return totalMessages.get(); }

    // --- MÉTODOS NUEVOS ---
    public void addMessage(String message) {
        totalMessages.incrementAndGet();
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        messageHistory.add("[" + time + "] " + message);
        if (messageHistory.size() > 50) messageHistory.remove(0);
    }

    public void clearMessageHistory() {
        messageHistory.clear();
        addMessage(Color.CYAN + "Consola limpiada.");
    }

    public void recordBytes(long bytes) { totalBytes.addAndGet(bytes); }

    public void addClient(ClientInfo clientInfo) {
        if (!connectedClients.contains(clientInfo)) {
            connectedClients.add(clientInfo);
            addMessage("Nodo conectado: " + clientInfo.getNick());
        }
    }

    public double getMemoryUsagePercent() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        double used = (double) memoryBean.getHeapMemoryUsage().getUsed();
        double max = (double) memoryBean.getHeapMemoryUsage().getMax();
        return (used / max) * 100;
    }

    public String getUptime() {
        return formatDuration(System.currentTimeMillis() - startTime);
    }

    public String formatUptime(long connectionTime) {
        return formatDuration(System.currentTimeMillis() - connectionTime);
    }

    private String formatDuration(long durationMillis) {
        long seconds = (durationMillis / 1000) % 60;
        long minutes = (durationMillis / (1000 * 60)) % 60;
        long hours = (durationMillis / (1000 * 60 * 60));
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    public String getMemoryUsageFormat() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        return formatBytes(memoryBean.getHeapMemoryUsage().getUsed());
    }

    public String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }
    /**
     * Retorna el límite máximo de memoria que la JVM puede utilizar,
     * formateado para ser legible (MB o GB).
     */
    public String getMaxMemoryFormat() {
        // Runtime.getRuntime().maxMemory() devuelve el valor en bytes
        double maxMemoryBytes = Runtime.getRuntime().maxMemory();
        double maxMemoryMB = maxMemoryBytes / (1024.0 * 1024.0);

        if (maxMemoryMB >= 1024) {
            return String.format("%.2f GB", maxMemoryMB / 1024.0);
        } else {
            return String.format("%.2f MB", maxMemoryMB);
        }
    }

    public long getTotalBytes() { return totalBytes.get(); }
    public List<String> getMessageHistory() { return messageHistory; }
    public List<ClientInfo> getConnectedClients() { return connectedClients; }
    public int getClientCount() { return connectedClients.size(); }
}
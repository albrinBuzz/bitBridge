package org.bitBridge.utils;


import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class HardwareEngine {
    private final OperatingSystemMXBean osBean;

    public HardwareEngine() {
        // Usamos la versión de 'com.sun.management' para acceder a métricas de sistema real
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public double getSystemCpuLoad() {
        double load = osBean.getCpuLoad(); // Retorna 0.0 a 1.0
        return (load < 0) ? 0 : load * 100;
    }

    public long getFreePhysicalMemory() {
        return osBean.getFreeMemorySize() / 1024 / 1024; // MB
    }

    public long getTotalPhysicalMemory() {
        return osBean.getTotalMemorySize() / 1024 / 1024; // MB
    }

    public int getRamUsagePercentage() {
        double free = osBean.getFreeMemorySize();
        double total = osBean.getTotalMemorySize();
        return (int) (100 - ((free / total) * 100));
    }
}
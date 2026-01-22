package org.bitBridge.shared.network;

import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.io.IOException;

public class NetworkConfig {
    // Valores base para tu red WiFi actual
    public static final int DEFAULT_BUFFER_SIZE = 128 * 1024; // 128KB (Sweet spot detectado)
    public static final int MAX_CHUNK_SIZE = 4 * 1024 * 1024;  // 4MB para ráfagas en LAN

    /**
     * Aplica la configuración de alto rendimiento de forma uniforme a cualquier canal.
     */
    public static void optimizeSocket(SocketChannel channel) throws IOException {
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        // Usamos un buffer de socket intermedio para evitar saturar el router Huawei
        channel.setOption(StandardSocketOptions.SO_SNDBUF, 256 * 1024);
        channel.setOption(StandardSocketOptions.SO_RCVBUF, 256 * 1024);
        channel.configureBlocking(true);
    }

    /**
     * Calcula el buffer óptimo dinámicamente basado en el rendimiento actual.
     */
    public static long getDynamicChunkSize(long bytesProcessed, long startTimeNanos) {
        if (bytesProcessed < 1024 * 1024) return DEFAULT_BUFFER_SIZE; // Primer MB va con buffer seguro

        long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
        if (durationMs <= 0) return DEFAULT_BUFFER_SIZE;

        double mbps = (bytesProcessed * 8.0) / (durationMs * 1000.0);

        if (mbps < 40) return 64 * 1024;           // WiFi congestionado
        if (mbps < 150) return DEFAULT_BUFFER_SIZE; // Tu WiFi Huawei actual
        return 1024 * 1024;                        // Red Gigabit / LAN
    }
}
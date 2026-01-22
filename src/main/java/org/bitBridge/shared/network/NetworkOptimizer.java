package org.bitBridge.shared.network;

public class NetworkOptimizer {

    public enum NetworkProfile {
        WIFI_CONGESTED(64 * 1024),    // 64KB: Redes inestables/2.4GHz
        WIFI_CLEAN(128 * 1024),       // 128KB: Tu actual Huawei optimizado
        LAN_GIGABIT(1024 * 1024),     // 1MB: Cable Ethernet
        FIBER_EXTREME(4 * 1024 * 1024); // 4MB: Transferencia local SSD-to-SSD

        final int bufferSize;
        NetworkProfile(int size) { this.bufferSize = size; }
    }

    public static int getOptimalBufferSize(long startTime, long bytesTransferred) {
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        if (durationMs < 100) return NetworkProfile.WIFI_CLEAN.bufferSize; // Default inicial

        // Mbps = (Bytes * 8) / (ms * 1000)
        double mbps = (bytesTransferred * 8.0) / (durationMs * 1000.0);

        if (mbps < 50) return NetworkProfile.WIFI_CONGESTED.bufferSize;
        if (mbps < 200) return NetworkProfile.WIFI_CLEAN.bufferSize;
        if (mbps < 800) return NetworkProfile.LAN_GIGABIT.bufferSize;
        return NetworkProfile.FIBER_EXTREME.bufferSize;
    }
}

/*
private void transferDataNIO(File file, SocketChannel socketChannel, String idTrans) throws IOException {
    try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
        long size = fileChannel.size();
        long position = 0;
        long startNIO = System.nanoTime();

        // Empezamos con el "Sweet Spot" que encontramos para tu WiFi
        int dynamicBuffer = 128 * 1024;

        while (position < size && running) {
            // Cada 5MB transferidos, recalculamos el perfil de red
            if (position > 0 && position % (5 * 1024 * 1024) == 0) {
                dynamicBuffer = NetworkOptimizer.getOptimalBufferSize(startNIO, position);
            }

            long transferred = fileChannel.transferTo(position,
                                 Math.min(dynamicBuffer, size - position),
                                 socketChannel);

            if (transferred <= 0) {
                Thread.sleep(1); // Pequeño respiro si el buffer del SO está lleno
                continue;
            }

            position += transferred;
            transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTrans, position, size);
        }
    }
}




private void transferDataNIO(File file, SocketChannel socketChannel, String idTrans) throws IOException {
    try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
        long size = fileChannel.size();
        long position = 0;
        long startNIO = System.nanoTime();
        int currentBuffer = 128 * 1024; // Empezar con el valor que te dio estabilidad

        while (position < size && running) {
            // Recalibrar cada 5MB para no saturar la CPU con cálculos
            if (position > 0 && position % (5 * 1024 * 1024) == 0) {
                currentBuffer = NetworkOptimizer.calculateBufferSize(position, System.nanoTime() - startNIO);
            }

            long transferred = fileChannel.transferTo(position,
                                Math.min(currentBuffer, size - position),
                                socketChannel);

            if (transferred <= 0) {
                Thread.yield(); // Evitar consumo de CPU si el buffer de red está lleno
                continue;
            }

            position += transferred;
            transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTrans, position, size);
        }
    }
}

 */
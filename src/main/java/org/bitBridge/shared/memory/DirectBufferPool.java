package org.bitBridge.shared.memory;

import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DirectBufferPool {

    public enum BufferType {
        MESSAGE, DIRECTORY, TRANSFER
    }

    private static final Map<BufferType, ArrayBlockingQueue<ByteBuffer>> pools = new EnumMap<>(BufferType.class);
    private static final Map<BufferType, Integer> sizes = new EnumMap<>(BufferType.class);
    private static boolean initialized = false;

    // En DirectBufferPool.java
    public static int getCapacityForType(BufferType type) {
        return sizes.getOrDefault(type, 0);
    }

    /**
     * Carga dinámicamente los buffers según el archivo .properties
     */
    public static synchronized void initialize() {
        if (initialized) return;

        ConfiguracionApp config = ConfiguracionApp.getInstancia();

        // NOTA: El .properties ya da el tamaño en BYTES o KB según tu ConfigKey.
        // Vamos a asumir que los valores del .properties son finales.

        // 1. Pool de MENSAJES (4KB x 100 = 400KB total)
        setupPool(BufferType.MESSAGE,
                config.obtenerInt(ConfigKey.POOL_MSG_SIZE, 4096),
                config.obtenerInt(ConfigKey.POOL_MSG_CAP, 100));

        // 2. Pool de DIRECTORIOS (64KB x 20 = 1.2MB total)
        setupPool(BufferType.DIRECTORY,
                config.obtenerInt(ConfigKey.POOL_DIR_SIZE, 65536),
                config.obtenerInt(ConfigKey.POOL_DIR_CAP, 20));

        // 3. Pool de TRANSFERENCIAS (256KB x 10 = 2.5MB total)
        setupPool(BufferType.TRANSFER,
                config.obtenerInt(ConfigKey.POOL_TRANS_SIZE, 262144),
                config.obtenerInt(ConfigKey.POOL_TRANS_CAP, 10));

        initialized = true;
        Logger.logInfo("[DirectBufferPool] Memoria Off-Heap inicializada con límites seguros.");
    }

    private static void setupPool(BufferType type, int sizeInBytes, int capacity) {
        sizes.put(type, sizeInBytes);
        ArrayBlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<>(capacity);

        // Solo pre-llenamos si el tamaño es razonable
        for (int i = 0; i < capacity; i++) {
            try {
                queue.offer(ByteBuffer.allocateDirect(sizeInBytes));
            } catch (OutOfMemoryError e) {
                Logger.logError("No se pudo pre-asignar pool " + type + ". Reduciendo capacidad.");
                break;
            }
        }
        pools.put(type, queue);
    }

    /**
     * Ahora require el TIPO para saber de qué cola sacar
     */
    public static ByteBuffer acquire(BufferType type, long timeoutMs) {
        if (!initialized) initialize(); // Auto-init por seguridad

        try {
            return pools.get(type).poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public static void release(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) return;

        int cap = buffer.capacity();
        buffer.clear();

        // Buscamos a qué pool pertenece comparando el tamaño
        for (Map.Entry<BufferType, Integer> entry : sizes.entrySet()) {
            if (entry.getValue() == cap) {
                pools.get(entry.getKey()).offer(buffer);
                return;
            }
        }
    }
}
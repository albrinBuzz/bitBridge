package org.bitBridge.shared.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DirectBufferPool {
    //private static final int BUFFER_SIZE = 8192; // 8 KB
    //private static final int POOL_CAPACITY = 40_960; // ~128MB fijos


    private static final int BUFFER_SIZE = 16_384*2;

    // Escalamos a 100,000 buffers. Total memoria: ~1.56 GB direct memory.
    // Con esto, puedes tener 100k mensajes volando simultáneamente.
    private static final int POOL_CAPACITY = 200_000;

    // Usamos ArrayBlockingQueue para bloquear o descartar de forma segura
    private static final ArrayBlockingQueue<ByteBuffer> pool = new ArrayBlockingQueue<>(POOL_CAPACITY);

    static {
        for (int i = 0; i < POOL_CAPACITY; i++) {
            pool.offer(ByteBuffer.allocateDirect(BUFFER_SIZE));
        }
    }

    /**
     * Intenta obtener un buffer. Si no hay, devuelve null (para descartar)
     * o espera un tiempo máximo.
     */
    public static ByteBuffer acquire(long timeoutMs) {
        try {
            // Intentamos obtener memoria por un tiempo breve
            return pool.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public static void release(ByteBuffer buffer) {
        if (buffer != null && buffer.isDirect() && buffer.capacity() == BUFFER_SIZE) {
            buffer.clear();
            pool.offer(buffer); // Devuelve al pool
        }
    }

    public static int available() {
        return pool.size();
    }
}
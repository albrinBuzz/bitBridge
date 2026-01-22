package org.bitBridge.shared.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BufferPool {
    private static final int BUFFER_SIZE = 128 * 1024; // 64KB
    private static final int POOL_SIZE = 1000;    // Mantener 1000 buffers listos
    private static final BlockingQueue<ByteBuffer> pool = new ArrayBlockingQueue<>(POOL_SIZE);

    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            pool.add(ByteBuffer.allocateDirect(BUFFER_SIZE));
        }
    }

    public static ByteBuffer borrow() throws InterruptedException {
        ByteBuffer b = pool.take(); // Espera si no hay disponibles
        b.clear();
        return b;
    }

    public static void giveBack(ByteBuffer b) {
        pool.offer(b);
    }
}
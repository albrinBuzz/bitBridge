package org.bitBridge.shared.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedBufferWrapper {
    public final ByteBuffer buffer;
    public final AtomicInteger refCount;

    public SharedBufferWrapper(ByteBuffer buffer, AtomicInteger refCount) {
        // Usamos duplicate() para que cada cliente tenga su propio position y limit,
        // pero todos compartan la misma zona de memoria física.
        this.buffer = buffer.duplicate();
        this.refCount = refCount;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }
}
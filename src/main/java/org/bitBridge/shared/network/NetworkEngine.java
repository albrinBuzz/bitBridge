package org.bitBridge.shared.network;

import java.io.IOException;

// Interfaz base para cualquier motor de red
public interface NetworkEngine {
    void stop() throws IOException;
    boolean isActive();
}
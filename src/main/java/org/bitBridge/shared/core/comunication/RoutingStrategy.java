package org.bitBridge.shared.core.comunication;

public enum RoutingStrategy {
    VIRTUAL_THREAD,  // Mensajería, notificaciones, queries cortas
    BLOCKING_IO,     // Operaciones pesadas de disco/red (Files, Directories)
    INLINE           // Se ejecuta en el mismo hilo (Antiguo SYNC)
}
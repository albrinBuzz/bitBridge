package org.bitBridge.shared;

public enum ExecutionMode {
    ASYNC, // Va al Worker Pool (mensajes rápidos)
    SYNC   // Se queda en el hilo actual (flujos de datos/streams)
}

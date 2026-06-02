package org.bitBridge.shared.core.comunication;

public enum FileHandshakeAction {
    // --- Flujo Normal ---
    SEND_REQUEST,
    ACCEPT_REQUEST,
    DECLINE_REQUEST,
    START_TRANSFER,
    TRANSFER_INIT,
    TRANSFER_DONE,
    SKIP_FILE,      // 👈 Añadir: El receptor le dice al servidor/emisor que no gaste red
    PROCESS_DELTA,
    // --- Flujo de Integridad y Parcialidad (Nuevos) ---
    /**
     * El archivo se recibió, pero el Hash (SHA-256) no coincide.
     * Ideal para tu red cuando hay interferencia ráfaga.
     */
    ERROR_CHECKSUM_MISMATCH,

    /**
     * La conexión se cortó antes de leer el total de bytes esperados.
     * Permite a BitBridge intentar un "Resume" (reanudación) después.
     */
    TRANSFER_INCOMPLETE,

    // --- Flujo de Errores de Sistema ---
    ERROR_DISCO_LLENO,
    ERROR_ARCHIVO_GRANDE,
    ERROR_TIPO_PROHIBIDO,
    ERROR_TIMEOUT,
    SERVER_BUSY,

    PROCESS_DELTAS,
    /**
     * El archivo cambió en el origen mientras se enviaba.
     */
    ERROR_FILE_MODIFIED
}
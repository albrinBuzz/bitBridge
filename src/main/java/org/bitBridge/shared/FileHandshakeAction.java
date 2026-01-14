package org.bitBridge.shared;

public enum FileHandshakeAction {
    // Flujo normal
    SEND_REQUEST,     // Solicitud inicial
    ACCEPT_REQUEST,   // El receptor dijo que sí
    DECLINE_REQUEST,  // El receptor dijo que no
    START_TRANSFER,   // Autorización final del servidor
    TRANSFER_INIT,    // Apertura de canales de datos
    TRANSFER_DONE,    // Éxito total

    // Flujo de errores (Nuevos)
    ERROR_DISCO_LLENO,   // No hay espacio en el receptor
    ERROR_ARCHIVO_GRANDE, // Supera el límite permitido
    ERROR_TIPO_PROHIBIDO, // Ejemplo: no se permiten .exe o .bat
    ERROR_TIMEOUT,        // El receptor tardó mucho en responder
    SERVER_BUSY           // El servidor ya tiene muchas transferencias activas
}
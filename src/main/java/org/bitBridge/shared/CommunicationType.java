package org.bitBridge.shared;

public enum CommunicationType {
    MESSAGE,       // Mensaje general
    PRIVATE_MESSAGE, // Mensaje directo (privado)
    SYSTEM_MESSAGE,  // Mensaje del sistema
    ERROR_MESSAGE,
    FILE,           // Transferencia de archivos
    DIRECTORY,
    COMMAND,        // Comandos o peticiones del cliente
    NOTIFICATION,   // Notificaciones generales
    ALERT,   // Mensaje de error
    UPDATE,
    DISCONNECT,
    SCREEN_CAPTURE,
    AUDIO_STREAM

}

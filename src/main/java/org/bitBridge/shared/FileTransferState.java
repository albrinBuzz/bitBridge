package org.bitBridge.shared;

public enum FileTransferState {
    PENDING,               // Transferencia pendiente, esperando comenzar
    SENDING,               // El archivo está siendo enviado
    RECEIVING,             // El archivo está siendo recibido
    IN_PROGRESS,           // Transferencia en progreso
    COMPLETED,             // Transferencia completada exitosamente
    FAILED,                // La transferencia falló
    PAUSED,                // Transferencia pausada
    RESUMED,               // Transferencia reanudada después de una pausa
    CANCELLED,             // Transferencia cancelada por el usuario
    WAITING_FOR_APPROVAL,  // Transferencia esperando aprobación (por ejemplo, autenticación o confirmación del receptor)
    INTERRUPTED,           // Transferencia interrumpida por problemas de red u otros errores
    RESTARTING,            // La transferencia se está reiniciando (por ejemplo, después de un error o pérdida de conexión)
    WAITING_FOR_RESPONSE,  // Esperando respuesta del receptor, tal vez para confirmar la recepción del archivo
    SENT,                  // El archivo fue enviado correctamente
    RECEIVED               // El archivo fue recibido correctamente
}

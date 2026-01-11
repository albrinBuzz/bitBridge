package org.bitBridge.shared;

public enum ServerStatusConnection {
    DISCONNECTED,  // El cliente no está conectado al servidor
    CONNECTING,    // El cliente está intentando conectarse al servidor
    CONNECTED,     // El cliente está conectado al servidor y la comunicación está establecida
    RECONNECTING,  // El cliente está intentando reconectarse después de una desconexión
    DISCONNECTING, // El cliente está en proceso de desconectarse del servidor
    ERROR,         // Ha ocurrido un error en la conexión
    TIMEOUT        // La conexión ha excedido el tiempo de espera
}

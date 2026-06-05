package org.bitBridge.shared.core.comunication;

public enum SocketPurpose {
    CHAT_COMMAND,        // Cliente activo normal (comandos, chat)
    FILE_TRANSFER,       // Socket dedicado a la transferencia masiva de archivos
    AUDIO_STREAMING,     // Canal de audio en tiempo real bidireccional
    PASSIVE_LISTENER     // Socket pasivo que solo se conecta para recibir info / métricas del servidor
}
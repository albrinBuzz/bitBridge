package org.bitBridge.shared;

public enum FileHandshakeAction {
    SEND_REQUEST,     // Cliente A quiere enviar un archivo
    ACCEPT_REQUEST,   // Cliente B acepta el archivo
    DECLINE_REQUEST,  // Cliente B lo rechaza
    START_TRANSFER,   // El servidor autoriza la transferencia
    TRANSFER_INIT,    // Cliente abre socket de datos
    TRANSFER_DONE     // Archivo enviado/recibido completamente
}

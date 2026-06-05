package org.bitBridge.server.core.client;

public enum ThreadCarrier {
    VIRTUAL,   // Mensajes de control, chat, señalización (Hilos virtuales rápidos)
    PHYSICAL   // Transferencias de archivos, I/O disco, criptografía (Pool acotado)
}
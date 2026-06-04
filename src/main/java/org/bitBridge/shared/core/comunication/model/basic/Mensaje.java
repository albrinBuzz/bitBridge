package org.bitBridge.shared.core.comunication.model.basic;

import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

@BufferPoolMapping(DirectBufferPool.BufferType.MESSAGE)
public class Mensaje extends Communication {
    private String contenido;

    public Mensaje(String contenido) { this.contenido = contenido; }
    public Mensaje() {}

    public void setContenido(String contenido) { this.contenido = contenido; }
    public String getContenido() { return contenido; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }

    @Override
    public String toString() { return " Mensaje: " + contenido; }
}
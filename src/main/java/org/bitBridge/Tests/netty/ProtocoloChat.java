package org.bitBridge.Tests.netty;

import java.io.Serializable;

public class ProtocoloChat implements Serializable {
    public String id;
    public String contenido;
    public boolean esAck;

    public ProtocoloChat(String id, String contenido, boolean esAck) {
        this.id = id;
        this.contenido = contenido;
        this.esAck = esAck;
    }
}
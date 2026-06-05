package org.bitBridge.shared.core.comunication.model.basic;


import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.SocketPurpose;

public class HandshakeMessage extends Communication {
    private final String identity;         // Nickname o SessionID (ej: "Cris" o "REQ-9981")
    private final SocketPurpose purpose;   // Qué tipo de socket se está abriendo
    private final String metadata;         // Datos adicionales opcionales

    public HandshakeMessage(String identity, SocketPurpose purpose, String metadata) {
        this.identity = identity;
        this.purpose = purpose;
        this.metadata = metadata;
    }

    public String getIdentity() { return identity; }
    public SocketPurpose getPurpose() { return purpose; }
    public String getMetadata() { return metadata; }


}
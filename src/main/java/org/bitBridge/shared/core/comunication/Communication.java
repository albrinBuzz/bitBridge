package org.bitBridge.shared.core.comunication;

import java.io.Serializable;


public abstract class Communication implements Serializable {
    private final String communicationId;

    public Communication() {
        this.communicationId = this.getClass().getSimpleName();
    }

    public String getCommunicationId() {
        return communicationId;
    }
}
package org.bitBridge.shared;

import java.io.Serializable;

public abstract class Communication implements Serializable {

    private CommunicationType communicationType;

    public Communication(CommunicationType communicationType) {
        this.communicationType = communicationType;
    }

    public CommunicationType getType() {
        return this.communicationType;
    }
}

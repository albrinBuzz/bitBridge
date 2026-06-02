package org.bitBridge.shared.core.comunication.sync;

import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.CommunicationType;

import java.util.List;


public class RsyncSignatures extends Communication {
    private final List<BlockSignature> signatures;

    public RsyncSignatures(List<BlockSignature> signatures) {
        super(CommunicationType.RSYNC_SIGNATURES); // Inyección obligatoria de tipo
        this.signatures = signatures;
    }

    public List<BlockSignature> getSignatures() {
        return signatures;
    }
}
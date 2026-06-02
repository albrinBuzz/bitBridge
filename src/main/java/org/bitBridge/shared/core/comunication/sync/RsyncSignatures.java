package org.bitBridge.shared.core.comunication.sync;

import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.CommunicationType;

import java.util.List;


public class RsyncSignatures extends Communication {
    private final List<BlockSignature> signatures;
    private  int blockSize;

    public RsyncSignatures(List<BlockSignature> signatures) {
        super(CommunicationType.RSYNC_SIGNATURES); // Inyección obligatoria de tipo
        this.signatures = signatures;
    }

    public RsyncSignatures(List<BlockSignature> signatures, int blockSize) {
        super(CommunicationType.RSYNC_SIGNATURES);
        this.signatures = signatures;
        this.blockSize = blockSize;
    }
    public int getBlockSize() {
        return blockSize;
    }

    public List<BlockSignature> getSignatures() {
        return signatures;
    }
}
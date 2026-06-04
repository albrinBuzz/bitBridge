package org.bitBridge.shared.core.comunication.model.sync;

import org.bitBridge.shared.core.comunication.Communication;

import java.util.List;


public class RsyncSignatures extends Communication {
    private final List<BlockSignature> signatures;
    private  int blockSize;

    public RsyncSignatures(List<BlockSignature> signatures) {

        this.signatures = signatures;
    }

    public RsyncSignatures(List<BlockSignature> signatures, int blockSize) {

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
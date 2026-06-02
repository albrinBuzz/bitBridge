package org.bitBridge.shared.core.comunication.sync;

import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.CommunicationType;


import java.util.List;

public class RsyncDeltaPackage extends Communication {
    private final List<RsyncDeltaInstruction> instructions;
    private long totalFileSize;
    public RsyncDeltaPackage(List<RsyncDeltaInstruction> instructions) {
        super(CommunicationType.RSYNC_DELTAS); // Inyección obligatoria de tipo
        this.instructions = instructions;
    }
    public RsyncDeltaPackage(List<RsyncDeltaInstruction> instructions, long totalFileSize) {
        this(instructions);
        this.totalFileSize = totalFileSize;
    }

    public List<RsyncDeltaInstruction> getInstructions() {
        return instructions;
    }
    public long getTotalFileSize() {
        return totalFileSize;
    }
}
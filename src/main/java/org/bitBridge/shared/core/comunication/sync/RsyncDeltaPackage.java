package org.bitBridge.shared.core.comunication.sync;

import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.CommunicationType;


import java.util.List;

public class RsyncDeltaPackage extends Communication {
    private final List<RsyncDeltaInstruction> instructions;

    public RsyncDeltaPackage(List<RsyncDeltaInstruction> instructions) {
        super(CommunicationType.RSYNC_DELTAS); // Inyección obligatoria de tipo
        this.instructions = instructions;
    }

    public List<RsyncDeltaInstruction> getInstructions() {
        return instructions;
    }
}
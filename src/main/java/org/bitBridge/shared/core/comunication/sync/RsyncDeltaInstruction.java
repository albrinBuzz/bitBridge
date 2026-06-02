package org.bitBridge.shared.core.comunication.sync;

import java.io.Serializable;

public class RsyncDeltaInstruction implements Serializable {
    private int blockIndex = -1;
    private byte[] literalData = null;

    public RsyncDeltaInstruction(int blockIndex) { this.blockIndex = blockIndex; }
    public RsyncDeltaInstruction(byte[] literalData) { this.literalData = literalData; }

    public boolean isLiteral() { return literalData != null; }
    public int getBlockIndex() { return blockIndex; }
    public byte[] getLiteralData() { return literalData; }
}
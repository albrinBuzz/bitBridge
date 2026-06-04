package org.bitBridge.shared.core.comunication.model.sync;

import java.io.Serializable;

public class BlockSignature implements Serializable {
    private final int blockIndex;
    private final long adler32;
    private final byte[] md5;

    public BlockSignature(int blockIndex, long adler32, byte[] md5) {
        this.blockIndex = blockIndex;
        this.adler32 = adler32;
        this.md5 = md5;
    }
    public int getBlockIndex() { return blockIndex; }
    public long getAdler32() { return adler32; }
    public byte[] getMd5() { return md5; }
}
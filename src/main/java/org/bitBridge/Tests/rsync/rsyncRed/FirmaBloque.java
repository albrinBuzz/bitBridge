package org.bitBridge.Tests.rsync.rsyncRed;


import java.io.Serializable;

public class FirmaBloque implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int index;
    private final long rollingHash;
    private final String fuerteHash;

    public FirmaBloque(int index, long rollingHash, String fuerteHash) {
        this.index = index;
        this.rollingHash = rollingHash;
        this.fuerteHash = fuerteHash;
    }

    public int getIndex() { return index; }
    public long getRollingHash() { return rollingHash; }
    public String getFuerteHash() { return fuerteHash; }
}
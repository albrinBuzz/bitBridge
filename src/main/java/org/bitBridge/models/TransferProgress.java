package org.bitBridge.models;


import org.bitBridge.shared.FileTransferState;

public record TransferProgress(
        String id,
        int percentage,
        double speedMBs,
        String eta,
        FileTransferState state
) {
    @Override
    public String toString() {
        return "TransferProgress{" +
                "id='" + id + '\'' +
                ", percentage=" + percentage +
                ", speedMBs=" + speedMBs +
                ", eta='" + eta + '\'' +
                ", state=" + state +
                '}';
    }
}
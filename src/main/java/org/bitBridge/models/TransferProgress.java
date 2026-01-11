package org.bitBridge.models;


import org.bitBridge.shared.FileTransferState;

public record TransferProgress(
        String id,
        int percentage,
        double speedMBs,
        String eta,
        FileTransferState state
) {}
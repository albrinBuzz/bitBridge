package org.bitBridge.shared.core.comunication;

import org.bitBridge.shared.memory.DirectBufferPool;

@BufferPoolMapping(DirectBufferPool.BufferType.TRANSFER) // Capturas crudas de alta definición
public class ScreenCaptureMessage extends Communication {
    private final byte[] imageData;
    private final String senderNick;
    private final String targetNick;

    public ScreenCaptureMessage(byte[] imageData, String senderNick, String targetNick) {
        this.imageData = imageData;
        this.senderNick = senderNick;
        this.targetNick = targetNick;
    }

    public byte[] getImageData() { return imageData; }
    public String getSenderNick() { return senderNick; }
    public String getTargetNick() { return targetNick; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }
}
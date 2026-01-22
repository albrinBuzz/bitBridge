package org.bitBridge.shared;

public class ScreenCaptureMessage extends Communication {
    private final byte[] imageData;
    private final String senderNick;
    private final String targetNick;
    public ScreenCaptureMessage(byte[] imageData, String senderNick, String targetNick) {
        super(CommunicationType.SCREEN_CAPTURE); // Asegúrate de agregarlo a tu Enum
        this.imageData = imageData;
        this.senderNick = senderNick;
        this.targetNick = targetNick;
    }

    public byte[] getImageData() { return imageData; }
    public String getSenderNick() { return senderNick; }

    public String getTargetNick() {
        return targetNick;
    }
}
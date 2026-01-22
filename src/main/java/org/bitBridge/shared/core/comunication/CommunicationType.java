package org.bitBridge.shared.core.comunication;

public enum CommunicationType {
    MESSAGE((byte) 1),
    PRIVATE_MESSAGE((byte) 2),
    SYSTEM_MESSAGE((byte) 3),
    ERROR_MESSAGE((byte) 4),
    FILE((byte) 5),
    DIRECTORY((byte) 6),
    COMMAND((byte) 7),
    NOTIFICATION((byte) 8),
    ALERT((byte) 9),
    UPDATE((byte) 10),
    DISCONNECT((byte) 11),
    SCREEN_CAPTURE((byte) 12),
    AUDIO_STREAM((byte) 13),
    ACK((byte) 14);
    public final byte id;

    CommunicationType(byte id) {
        this.id = id;
    }

    public static CommunicationType fromId(byte id) {
        for (CommunicationType type : values()) {
            if (type.id == id) return type;
        }
        return MESSAGE;
    }
}

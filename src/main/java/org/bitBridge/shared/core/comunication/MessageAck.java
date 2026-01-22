package org.bitBridge.shared.core.comunication;


public class MessageAck extends Communication {
    private  String originalMessageId; // ID único del mensaje original
    private final long timestamp;
    private final Status status;

    public enum Status {
        SUCCESS, ERROR, PENDING, DENIED
    }


    public MessageAck( Status status) {
        super(CommunicationType.ACK);

        this.timestamp = System.currentTimeMillis();
        this.status = status;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Status getStatus() {
        return status;
    }

    // Getters...
}
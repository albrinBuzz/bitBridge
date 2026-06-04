package org.bitBridge.shared.core.comunication.model.basic;

import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

@BufferPoolMapping(DirectBufferPool.BufferType.MESSAGE)
public class MessageAck extends Communication {
    private String originalMessageId;
    private long timestamp;
    private Status status;

    public enum Status { SUCCESS, ERROR, PENDING, DENIED }

    public MessageAck() {}
    public MessageAck(Status status) {
        this.timestamp = System.currentTimeMillis();
        this.status = status;
    }

    public String getOriginalMessageId() { return originalMessageId; }
    public long getTimestamp() { return timestamp; }
    public Status getStatus() { return status; }
    public void setOriginalMessageId(String originalMessageId) { this.originalMessageId = originalMessageId; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }
}
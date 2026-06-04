package org.bitBridge.shared.core.comunication.model.basic;

import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

@BufferPoolMapping(DirectBufferPool.BufferType.MESSAGE)
public class ResponseCommunication extends Communication {
    public enum Status { SUCCESS, ERROR, PENDING, DENIED }

    private final Status status;
    private final String message;
    private Object payload;
    private String requestId;

    public ResponseCommunication(Status status, String message, String requestId) {
        this(status, message, requestId, null);
    }
    public ResponseCommunication(Status status, String message, String requestId, Object payload) {
        this.status = status;
        this.message = message;
        this.requestId = requestId;
        this.payload = payload;
    }
    public ResponseCommunication(String message, Status status) {
        this.message = message;
        this.status = status;
    }

    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public String getRequestId() { return requestId; }
    public Object getPayload() { return payload; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }
}
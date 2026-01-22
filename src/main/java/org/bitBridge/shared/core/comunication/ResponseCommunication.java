package org.bitBridge.shared.core.comunication;


/**
 * Representa una respuesta formal del servidor a una petición del cliente.
 */
public class ResponseCommunication extends Communication {

    public enum Status {
        SUCCESS, ERROR, PENDING, DENIED
    }

    private final Status status;
    private final String message;
    private Object payload; // Datos extra (opcional)
    private String requestId; // Para saber a qué petición responde

    public ResponseCommunication(Status status, String message, String requestId) {
        this(status, message, requestId, null);
    }

    public ResponseCommunication(Status status, String message, String requestId, Object payload) {
        super(status == Status.ERROR ? CommunicationType.ERROR_MESSAGE : CommunicationType.COMMAND);
        this.status = status;
        this.message = message;
        this.requestId = requestId;
        this.payload = payload;
    }

    public ResponseCommunication(CommunicationType communicationType, String message, Status status) {
        super(status == Status.ERROR ? CommunicationType.ERROR_MESSAGE : CommunicationType.COMMAND);
        this.message = message;
        this.status = status;
    }

    // Getters
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public String getRequestId() { return requestId; }
    public Object getPayload() { return payload; }
}

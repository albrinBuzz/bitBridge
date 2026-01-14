package org.bitBridge.shared;


public class FileHandshakeCommunication extends Communication {

    private FileHandshakeAction action;

    private String sessionId;
    private FileDirectoryCommunication fileInfo;

    public FileHandshakeCommunication(
            FileHandshakeAction action,
            String sessionId,
            FileDirectoryCommunication fileInfo
    ) {
        super(CommunicationType.NOTIFICATION);
        this.action = action;
        this.sessionId = sessionId;
        this.fileInfo = fileInfo;
    }

    public FileHandshakeCommunication(FileHandshakeAction fileHandshakeAction,String sessionId) {
        super(CommunicationType.NOTIFICATION);
        this.action = fileHandshakeAction;
        this.sessionId = sessionId;
    }

    public FileHandshakeCommunication(FileHandshakeAction fileHandshakeAction) {
        super(CommunicationType.NOTIFICATION);
        this.action = fileHandshakeAction;
    }


    public FileHandshakeAction getAction() {
        return action;
    }

    public String getSessionId() {
        return sessionId;
    }

    public FileDirectoryCommunication getFileInfo() {
        return fileInfo;
    }
}


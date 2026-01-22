package org.bitBridge.shared.core.comunication;

public class Alert extends Communication {


    public enum Severity {
        INFO, WARNING, ERROR, CRITICAL
    }


    public Alert(CommunicationType communicationType) {
        super(communicationType);
    }


}

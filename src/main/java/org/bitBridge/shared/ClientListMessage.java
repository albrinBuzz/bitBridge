package org.bitBridge.shared;


import org.bitBridge.Client.ClientInfo;

import java.util.List;



public class ClientListMessage extends Communication {

    private List<ClientInfo> clientNicks;  // Lista de nombres de clientes

    public ClientListMessage(CommunicationType communicationType, List<ClientInfo> clientNick) {
        super(communicationType);
        this.clientNicks = clientNick;

    }
    public List<ClientInfo>getClientNicks(){
        return this.clientNicks;
    }

}

package org.bitBridge.shared.core.comunication.model.basic;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

import java.util.List;

@BufferPoolMapping(DirectBufferPool.BufferType.MESSAGE)
public class ClientListMessage extends Communication {
    private List<ClientInfo> clientNicks;

    public ClientListMessage(List<ClientInfo> clientNick) { this.clientNicks = clientNick; }
    public List<ClientInfo> getClientNicks() { return this.clientNicks; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }
}
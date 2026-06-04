package org.bitBridge.Client.handlers;

import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.core.comunication.model.basic.MessageAck;

@ClientHandler(MessageAck.class)
public class MessageAckHandler implements ClientActionHandler<MessageAck> {
    @Override
    public void handle(MessageAck data, Client cl, ClientContext ctx) throws Exception {
        cl.getMessageTracker().markAsDelivered();
    }
}
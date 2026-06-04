package org.bitBridge.server.handlers;


import org.bitBridge.server.core.client.CommunicationExchange;
import org.bitBridge.server.core.client.CommunicationHandler;
import org.bitBridge.server.core.client.ServerHandler;
import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.core.comunication.Communication;

import org.bitBridge.shared.core.comunication.ScreenCaptureMessage;

@ServerHandler(value = ScreenCaptureMessage.class, mode = ExecutionMode.SYNC)
public class ScreenCaptureRelayHandler implements CommunicationHandler {
    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        ScreenCaptureMessage screenMsg = (ScreenCaptureMessage) message;
        String destinatario = screenMsg.getTargetNick();

        // El servidor actúa como puente usando tu método original
        exchange.sendTo(destinatario, screenMsg);
    }
}
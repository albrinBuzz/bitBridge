package org.bitBridge.server.handlers;

import org.bitBridge.server.core.client.CommunicationExchange;
import org.bitBridge.server.core.client.CommunicationHandler;
import org.bitBridge.server.core.client.ServerHandler;
import org.bitBridge.server.core.client.ThreadCarrier;
import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.core.comunication.Communication;

import org.bitBridge.shared.core.comunication.model.basic.FilePullRequest;

@ServerHandler(value = FilePullRequest.class, mode = ExecutionMode.ASYNC,carrier = ThreadCarrier.PHYSICAL)
public class ServerFilePullRequestHandler implements CommunicationHandler {
    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        FilePullRequest pullReq = (FilePullRequest) message;
        String targetNick = pullReq.getTargetIp(); // Usando targetIp como alias del destino en tu modelo

        // El servidor reenvía de forma transparente el objeto al nodo destino
        exchange.sendTo(targetNick, pullReq);
    }
}
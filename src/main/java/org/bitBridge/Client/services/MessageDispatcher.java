package org.bitBridge.Client.services;



import org.bitBridge.Client.DirectoryTransferManager;
import org.bitBridge.Client.FileTransferManager;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.ClientListMessage;
import org.bitBridge.shared.CommunicationType;
import org.bitBridge.shared.FileHandshakeCommunication;
import org.bitBridge.shared.Mensaje;

import java.io.IOException;

public class MessageDispatcher {
    private final Client client;
    private final ClientContext context;

    public MessageDispatcher(Client client, ClientContext context) {
        this.client = client;
        this.context = context;
    }

    public void dispatch(Object incoming) {
        if (incoming instanceof ClientListMessage list) {
            client.notifyHostobserves(list.getClientNicks());
        }
        else if (incoming instanceof FileHandshakeCommunication handshake) {
            handleTransfer(handshake);
        }
        else if (incoming instanceof Mensaje msg) {
            client.handleIncomingMessage(msg.getContenido());
        }
    }

    private void handleTransfer(FileHandshakeCommunication handshake) {
        context.executor().submit(() -> {
            var info = handshake.getFileInfo();
            if (info.getType() == CommunicationType.FILE) {
                new FileTransferManager(context.transferController())
                        .receiveFiles(context.serverAddress(), String.valueOf(context.serverPort()), handshake);
            } else {
                try {
                    new DirectoryTransferManager(context.transferController())
                            .reciveDirectory(context.serverAddress(), String.valueOf(context.serverPort()), handshake);
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}

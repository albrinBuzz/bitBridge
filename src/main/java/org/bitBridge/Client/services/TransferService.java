package org.bitBridge.Client.services;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.DirectoryTransferManager;
import org.bitBridge.Client.FileTransferManager;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.FileDirectoryCommunication;
import org.bitBridge.shared.Logger;

import java.io.File;

public class TransferService {
    private final ClientContext context;
    private final FileTransferManager fileManager;
    private final DirectoryTransferManager dirManager;

    public TransferService(ClientContext context) {
        this.context = context;
        // Reutilizamos los managers
        this.fileManager = new FileTransferManager(context.transferController());
        this.dirManager = new DirectoryTransferManager(context.transferController());
    }

    public void enqueueFileSend(ClientInfo recipient, File file,String sender) {
        var com = new FileDirectoryCommunication(file.getName(), file.length(), recipient.getNick(),sender);
        context.executor().submit(() -> {
            try {
                fileManager.sendFile(com, file, context.serverAddress(), context.serverPort());
            } catch (Exception e) {
                Logger.logError("[TRANSFER] Error enviando archivo: " + e.getMessage());
            }
        });
    }

    public void enqueueDirectorySend(ClientInfo recipient, File directory) {
        context.executor().submit(() -> {
            try {
                dirManager.sendDirectory(directory, context.serverAddress(), context.serverPort(), recipient.getNick());
            } catch (Exception e) {
                Logger.logError("[TRANSFER] Error enviando directorio: " + e.getMessage());
            }
        });
    }
}
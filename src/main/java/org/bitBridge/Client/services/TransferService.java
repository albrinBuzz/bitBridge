package org.bitBridge.Client.services;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.managers.FileTransferManager;
import org.bitBridge.Client.managers.NioDirectoryTransferManager;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.core.comunication.model.basic.FileDirectoryCommunication;
import org.bitBridge.shared.Logger;
import org.bitBridge.utils.HashUtil;

import java.io.File;

public class TransferService {
    private final ClientContext context;
    private final FileTransferManager fileManager;
    //private final DirectoryTransferManager dirManager;
    private final NioDirectoryTransferManager dirManager;
    public TransferService(ClientContext context) {
        this.context = context;
        // Reutilizamos los managers
        this.fileManager = new FileTransferManager(context.transferController());
        //this.dirManager = new DirectoryTransferManager(context.transferController());
        this.dirManager=new NioDirectoryTransferManager(context.transferController());

    }

    public void enqueueFileSend(ClientInfo recipient, File file,String sender) throws Exception {
        String hash= HashUtil.getFileChecksum(file);

        var com = new FileDirectoryCommunication(hash,file.getName(), file.length(), recipient.getNick(),sender);
        context.executor().submit(() -> {
            try {
                var localFileManger= new FileTransferManager(context.transferController());
                localFileManger.sendFile(com, file, context.serverAddress(), context.serverPort());
                //fileManager.sendFile(com, file, context.serverAddress(), context.serverPort());
            } catch (Exception e) {
                Logger.logError("[TRANSFER] Error enviando archivo: " + e.getMessage());
            }
        });
    }

    public void enqueueDirectorySend(ClientInfo recipient, File directory) {
        context.executor().submit(() -> {
            try {
                NioDirectoryTransferManager localDirManager = new NioDirectoryTransferManager(context.transferController());
                localDirManager.sendDirectory(directory, context.serverAddress(), context.serverPort(), recipient.getNick());
            } catch (Exception e) {
                Logger.logError("[TRANSFER] Error enviando directorio: " + e.getMessage());
            }
        });
    }

}
package org.bitBridge.Client.handlers;

import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.managers.FileTransferManager;
import org.bitBridge.Client.managers.NioDirectoryTransferManager;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;

@ClientHandler(FileHandshakeCommunication.class)
public class ClientFileHandshakeHandler implements ClientActionHandler<FileHandshakeCommunication> {
    @Override
    public void handle(FileHandshakeCommunication handshake, Client cl, ClientContext ctx) throws Exception {
        ctx.executor().submit(() -> {
            var info = handshake.getFileInfo();
            try {
                // Log de depuración para ver qué está llegando realmente desde el servidor

                Logger.logInfo("[DEBUG-RECEIVER] ¿Es directorio según paquete?: " + info.isDirectory() + " para: " + info.getName());

                // Si el nombre termina en una extensión común o info.isDirectory es falso de verdad
                if (!info.isDirectory()) {
                    Logger.logInfo("Iniciando transferencia de ARCHIVO ÚNICO para: " + info.getName());
                    new FileTransferManager(ctx.transferController())
                            .receiveFile(ctx.serverAddress(), ctx.serverPort(), handshake);
                } else {
                    Logger.logInfo("Iniciando transferencia de DIRECTORIO para: " + info.getName());
                    new NioDirectoryTransferManager(ctx.transferController())
                            .receiveDirectory(ctx.serverAddress(), ctx.serverPort(), handshake);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
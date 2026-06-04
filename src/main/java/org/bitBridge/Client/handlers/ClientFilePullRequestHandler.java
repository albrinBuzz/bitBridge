package org.bitBridge.Client.handlers;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.FilePullRequest;
import java.io.File;

@ClientHandler(FilePullRequest.class)
public class ClientFilePullRequestHandler implements ClientActionHandler<FilePullRequest> {
    @Override
    public void handle(FilePullRequest req, Client cl, ClientContext ctx) throws Exception {
        File targetFile = new File(req.getRutaRemota());

        if (!targetFile.exists() || !targetFile.canRead()) {
            Logger.logError("[PULL-FAIL] No accesible: " + req.getRutaRemota());
            return;
        }

        ClientInfo recipient = new ClientInfo(req.getRequesterNick());

        if (req.isEsDirectorio() && targetFile.isDirectory()) {
            Logger.logInfo("[PULL-DIR] Iniciando envío de carpeta: " + targetFile.getName());
            cl.sendDirectoryToHost(recipient, targetFile);
        } else {
            Logger.logInfo("[PULL-FILE] Iniciando envío de archivo: " + targetFile.getName());
            cl.sendFileToHost(recipient, targetFile);
        }
    }
}
package org.bitBridge.server.handlers;

import org.bitBridge.server.core.client.CommunicationExchange;
import org.bitBridge.server.core.client.CommunicationHandler;
import org.bitBridge.server.core.client.ServerHandler;
import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.model.basic.DirectoryQuery;


/**
 * Maneja las peticiones de exploración de directorios remotos en el Servidor.
 * Redirige la consulta al nodo objetivo (Target).
 */
@ServerHandler(value = DirectoryQuery.class, mode = ExecutionMode.ASYNC)
public class ServerDirectoryQueryHandler implements CommunicationHandler {

    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        if (!(message instanceof DirectoryQuery query)) {
            return;
        }else {
            Logger.logInfo("Procesando la query de "+query.getSourceIp());
        }

        try {
            // El servidor actúa como puente de relay hacia el nodo objetivo
            exchange.sendTo(query.getTargetIp(), query);
        } catch (Exception e) {
            Logger.logError("Error procesando DirectoryQuery en Servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
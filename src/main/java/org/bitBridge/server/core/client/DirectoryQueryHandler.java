package org.bitBridge.server.core.client;



import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.model.basic.DirectoryQuery;
import org.bitBridge.shared.Logger;

/**
 * Maneja las peticiones de exploración de directorios remotos.
 */
public class DirectoryQueryHandler implements CommunicationHandler {

    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        // 1. Validamos que el mensaje sea del tipo correcto
        if (!(message instanceof DirectoryQuery query)) {
            return;
        }

        try {

            exchange.sendTo(query.getTargetIp(), query);


        } catch (Exception e) {
            Logger.logError("Error procesando DirectoryQuery: " + e.getMessage());
            e.printStackTrace();
            // Opcional: Enviar un mensaje de error de vuelta al cliente
        }
    }
}
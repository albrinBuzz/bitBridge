package org.bitBridge.server.handlers;

import org.bitBridge.server.core.client.CommunicationExchange;
import org.bitBridge.server.core.client.CommunicationHandler;
import org.bitBridge.server.core.client.ServerHandler;
import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.model.basic.DirectoryQueryResponse;

import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;

import java.util.List;

/**
 * Maneja la LLEGADA de los datos de un directorio remoto al servidor.
 * Devuelve el árbol de archivos al nodo que originó la petición (Source).
 */
@ServerHandler(value = DirectoryQueryResponse.class, mode = ExecutionMode.ASYNC)
public class ServerDirectoryQueryResponseHandler implements CommunicationHandler {

    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        if (!(message instanceof DirectoryQueryResponse response)) {
            return;
        }

        NodoDirectorio nodoRaiz = response.getNodo();

        if (nodoRaiz == null) {
            Logger.logWarn("Se recibió una respuesta de directorio vacía o nula en el Servidor.");
            return;
        }

        try {
            // Extraemos los hijos de red serializados tal cual venían en tu lógica original
            List<NodoDirectorio> hijosRecibidos = nodoRaiz.getHijos();

            // Reconstruimos la respuesta limpia manteniendo el nodo raíz mapeado
            DirectoryQueryResponse queryResponse = new DirectoryQueryResponse(nodoRaiz);

            // Enviamos de vuelta al cliente solicitante original usando tu método exacto
            exchange.sendTo(response.getSourceIp(), queryResponse);

        } catch (Exception e) {
            Logger.logError("Error procesando DirectoryQueryResponse en Servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
package org.bitBridge.server.core.client;

//package org.bitBridge.Client.services.handlers;

import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.core.comunication.model.basic.DirectoryQueryResponse;
import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;

import java.util.List;

/**
 * Maneja la LLEGADA de los datos de un directorio remoto al cliente.
 */
public class DirectoryQueryResponseHandler implements CommunicationHandler {

    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        // 1. Validamos que sea la respuesta esperada
        if (!(message instanceof DirectoryQueryResponse response)) {
            return;
        }

        // 2. Extraemos el nodo raíz que contiene los archivos
        NodoDirectorio nodoRaiz = response.getNodo();



        if (nodoRaiz == null) {
            Logger.logWarn("Se recibió una respuesta de directorio vacía o nula.");
            return;
        }

        // Obtenemos los hijos (ya serializados desde el servidor)
        List<NodoDirectorio> hijosRecibidos = nodoRaiz.getHijos();




        // Obtenemos los hijos reales (esto dispara el DirectoryStream internamente)
        //List<NodoDirectorio> hijos = nodoRaiz.getHijos();

        // 3. Creamos la respuesta con la lista de objetos NodoDirectorio
        DirectoryQueryResponse queryResponse = new DirectoryQueryResponse(nodoRaiz);

        // 4. Enviamos de vuelta al cliente a través del exchange

        exchange.sendTo(response.getSourceIp(), queryResponse);

        // 3. Notificamos al Cliente para que este dispare el evento a la UI
        // El Dispatcher suele tener acceso al 'client' a través del contexto
        // cl.handleRemoteDirectoryUpdate(hijosRecibidos);
    }
}
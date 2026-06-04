package org.bitBridge.server.handlers;

import org.bitBridge.server.core.client.CommunicationExchange;
import org.bitBridge.server.core.client.CommunicationHandler;
import org.bitBridge.server.core.client.ServerHandler;
import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.core.comunication.Communication;

import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;

/**
 * Maneja el intercambio inicial (Handshake) para transferencias de archivos y directorios.
 * Se ejecuta en modo SYNC para registrar la sesión inmediatamente en el hilo actual.
 */
@ServerHandler(value = FileHandshakeCommunication.class, mode = ExecutionMode.SYNC)
public class FileHandshakeHandler implements CommunicationHandler {

    @Override
    public void handle(CommunicationExchange exchange, Communication message) throws Exception {
        // 1. Validamos de forma segura que el mensaje entrante sea el Handshake esperado
        if (!(message instanceof FileHandshakeCommunication handshake)) {
            return;
        }

        // 2. Obtenemos el contexto del servidor desde el exchange nativo
        var context = exchange.getContext();

        // 3. Desbloquea al emisor registrando la respuesta en el manager de forma síncrona
        context.transferManager().registerHandshake(
                handshake.getSessionId(),
                handshake
        );
    }
}
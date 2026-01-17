package org.bitBridge.server.client;

import org.bitBridge.server.core.ServerContext;
import org.bitBridge.server.transfer.FileTransferService;
import org.bitBridge.shared.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bitBridge.shared.Communication;
import org.bitBridge.shared.CommunicationType;
import org.bitBridge.shared.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommunicationDispatcher {
    private final Map<CommunicationType, CommunicationHandler> handlers = new ConcurrentHashMap<>();
    private final ExecutorService workerPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
    );
    private final Map<CommunicationType, ExecutionMode> modes = new ConcurrentHashMap<>();


    public CommunicationDispatcher() {

        //registerHandler(CommunicationType.MESSAGE, new MessageHandler());
        registerHandler(CommunicationType.MESSAGE, new MessageHandler(), ExecutionMode.ASYNC);
        //dispatcher.registerHandler(CommunicationType.FILE, new FileHandler(), ExecutionMode.SYNC);

        registerHandler(CommunicationType.FILE, (exchange, comm) -> {
            var client = exchange.getSender();
            var ctx = exchange.getContext();
            new FileTransferService(ctx).handleForwardFile(client, client.objectInputStream(), (FileDirectoryCommunication) comm);
        }, ExecutionMode.SYNC);

        // Registro de DIRECTORY (También debe ser SYNC para no corromper el stream)
        /*registerHandler(CommunicationType.DIRECTORY, (exchange, comm) -> {
            var client = exchange.getSender();
            var ctx = exchange.getContext();
            new FileTransferService(ctx).relayDirectory((FileDirectoryCommunication) comm, client, client.objectInputStream());
        }, ExecutionMode.SYNC);


        // En CommunicationDispatcher del Servidor
        registerHandler(CommunicationType.SCREEN_CAPTURE, (exchange, comm) -> {
            Logger.logInfo("Captura Recibida");
            ScreenCaptureMessage screenMsg = (ScreenCaptureMessage) comm;
            var destinatario=screenMsg.getTargetNick();
            // El servidor simplemente actúa como puente (Relay)
            exchange.sendTo(destinatario, screenMsg);
        }, ExecutionMode.SYNC);

        /*registerHandler(CommunicationType.DIRECTORY, new DirectoryHandler());
        registerHandler(CommunicationType.FILE, new FileTransferHandler());*/
    }

    public void registerHandler(CommunicationType type, CommunicationHandler handler) {
        handlers.put(type, handler);
    }

    public void registerHandler(CommunicationType type, CommunicationHandler handler, ExecutionMode mode) {
        handlers.put(type, handler);
        modes.put(type, mode);
    }


    public void dispatch(ClientHandler sender, Communication message, ServerContext context) {
        CommunicationHandler handler = handlers.get(message.getType());
        ExecutionMode mode = modes.getOrDefault(message.getType(), ExecutionMode.ASYNC); // Default seguro

        if (handler == null) return;

        Runnable task = () -> {
            try {
                handler.handle(new CommunicationExchange(sender, context), message);
            } catch (Exception e) {
                Logger.logError("Error en " + message.getType() + ": " + e.getMessage());
            }
        };

        // Decidimos según el mapa de modos, no según el Enum
        if (mode == ExecutionMode.SYNC) {
            task.run(); // Ejecución inmediata, bloquea el bucle de lectura
        } else {
            workerPool.execute(task); // Delegación, el hilo de lectura queda libre
        }
    }
}
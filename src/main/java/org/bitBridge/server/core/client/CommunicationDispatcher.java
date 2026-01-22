package org.bitBridge.server.core.client;

import org.bitBridge.server.core.ServerContext;
import org.bitBridge.server.transfer.FileTransferService;
import org.bitBridge.shared.*;

import java.util.Map;
import java.util.concurrent.*;

import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.Logger;

public class CommunicationDispatcher {
    private final Map<CommunicationType, CommunicationHandler> handlers = new ConcurrentHashMap<>();
    /*private final ExecutorService workerPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
    );*/

    private final ExecutorService workerPool = Executors.newFixedThreadPool(16,
            r -> {
                Thread t = new Thread(r);
                t.setName("MSG-Worker-" + t.getId());
                return t;
            });

    /*private final ExecutorService fileTransferPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);

        t.setName("FT-Pool-" + t.getId());
        return t;
    });*/

    // Máximo 50 transferencias simultáneas para proteger la RAM
    private final ExecutorService fileTransferPool = new ThreadPoolExecutor(
            4, 50, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()
    );

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    //private final ExecutorService fileTransferPool = Executors.newVirtualThreadPerTaskExecutor();

    private final Map<CommunicationType, ExecutionMode> modes = new ConcurrentHashMap<>();


    public CommunicationDispatcher() {

        //registerHandler(CommunicationType.MESSAGE, new MessageHandler());
        registerHandler(CommunicationType.MESSAGE, new MessageHandler(), ExecutionMode.ASYNC);

        //dispatcher.registerHandler(CommunicationType.FILE, new FileHandler(), ExecutionMode.SYNC);
        registerHandler(CommunicationType.NOTIFICATION, (exchange, comm) -> {
            if (comm instanceof FileHandshakeCommunication handshake) {


                // Entregamos la respuesta al manager para desbloquear al emisor
                exchange.getContext().transferManager().registerHandshake(
                        handshake.getSessionId(),
                        handshake
                );
            }
        }, ExecutionMode.SYNC);
        // Registro de FILE
        registerHandler(CommunicationType.FILE, (exchange, comm) -> {
            var client = exchange.getSender();
            var ctx = exchange.getContext();

            new FileTransferService(ctx).handleForwardFile(
                    client,
                    (FileDirectoryCommunication) comm
            );
        }, ExecutionMode.ASYNC);

        // CAMBIO VITAL: Usar ExecutionMode.ASYNC
        registerHandler(CommunicationType.DIRECTORY, (exchange, comm) -> {
            var client = exchange.getSender();
            var ctx = exchange.getContext();
            String sessionId = "DIR_" + System.currentTimeMillis() % 10000;

            new FileTransferService(ctx).relayDirectory(
                    (FileDirectoryCommunication) comm,
                    client,
                    sessionId
            );
        }, ExecutionMode.ASYNC); // <--- ANTES ESTABA EN SYNC

        // En CommunicationDispatcher del Servidor
        registerHandler(CommunicationType.SCREEN_CAPTURE, (exchange, comm) -> {

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


    public void dispatch(BitBridgeClient sender, Communication message, ServerContext context) {
        CommunicationHandler handler = handlers.get(message.getType());
        ExecutionMode mode = modes.getOrDefault(message.getType(), ExecutionMode.ASYNC);

        if (handler == null) return;

        Runnable task = () -> {
            try {
                //Logger.logInfo(Thread.currentThread().getName());
                handler.handle(new CommunicationExchange(sender, context), message);
            } catch (Exception e) {
                Logger.logError("Error en " + message.getType() + ": " + e.getMessage());
            }
        };

        // --- LÓGICA DE ASIGNACIÓN DE POOLS ---
        if (message.getType() == CommunicationType.FILE || message.getType() == CommunicationType.DIRECTORY) {
            // Las transferencias siempre deben ser ASYNC para no bloquear el Selector de NIO
            fileTransferPool.execute(task);
        } else {
            // Los mensajes de chat y notificaciones van al pool de mensajería
            if (mode == ExecutionMode.SYNC) {
                task.run();
            } else {
                //workerPool.execute(task);
                virtualExecutor.execute(task);
            }
        }
    }
}
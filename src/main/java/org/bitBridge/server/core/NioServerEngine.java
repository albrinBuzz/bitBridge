package org.bitBridge.server.core;

import org.bitBridge.server.core.client.NioClientHandler;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.network.ServerNetworkEngine;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class NioServerEngine implements ServerNetworkEngine {
    private ServerSocketChannel serverChannel;
    private final ServerContext context;
    private final AtomicInteger TOTALCONECTIONS = new AtomicInteger(0);
    // Configuración de Sub-Reactors
    private final int workerCount = Runtime.getRuntime().availableProcessors();
    //private final int workerCount = 4;
    private SubReactor[] workers;
    private final AtomicInteger roundRobin = new AtomicInteger(0);


    public NioServerEngine(ServerContext context) { this.context = context; }

    @Override
    public void start(int port) throws IOException {
        // 1. Inicializar Workers (Sub-Reactors)
        workers = new SubReactor[workerCount];
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new SubReactor(i);
            new Thread(workers[i], "NIO-Worker-" + i).start();
        }

        // 2. Configurar el Main Reactor (Boss) para aceptar conexiones
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(true); // El accept puede ser bloqueante en su propio hilo
        serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverChannel.bind(new InetSocketAddress(port));

        new Thread(this::acceptLoop, "NIO-Boss-Acceptor").start();
        Logger.logInfo("[NIO] Servidor iniciado con " + workerCount + " sub-reactors.");
    }



    private void acceptLoop() {
        while (serverChannel.isOpen()) {
            try {
                SocketChannel clientChannel = serverChannel.accept();
                if (clientChannel != null) {
                    if (TOTALCONECTIONS.get() >= 1000) {
                        // we've hit max limit of current open connections, so we go
                        // ahead and close this connection without processing it
                        try {
                            clientChannel.close();
                        } catch (IOException ignore) {
                        }
                        // move on to next selected key
                        continue;
                    }

                    clientChannel.configureBlocking(false);
                    clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                    TOTALCONECTIONS.incrementAndGet();
                    // Balanceo Round-Robin hacia los Sub-Reactors
                    int index = Math.abs(roundRobin.getAndIncrement() % workerCount);
                    workers[index].registerChannel(clientChannel);
                }
            } catch (IOException e) {
                if (serverChannel.isOpen()) Logger.logError("Error aceptando conexión: " + e.getMessage());
            }
        }
    }

    // Clase interna para el manejo de I/O en hilos separados
    private class SubReactor implements Runnable {
        private final Selector workerSelector;
        private final int id;
        private final ConcurrentLinkedQueue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();

        public SubReactor(int id) throws IOException {
            this.id = id;
            this.workerSelector = Selector.open();
        }
        public void registerChannel(SocketChannel channel) {
            pendingTasks.add(() -> {
                try {
                    // Dentro de SubReactor.registerChannel
                    NioClientHandler handler = new NioClientHandler(channel, context);
                    SelectionKey key = channel.register(workerSelector, SelectionKey.OP_READ, handler);
                    handler.setSelectionKey(key);

                } catch (ClosedChannelException e) {
                    Logger.logError("Error registrando canal en Worker-" + id);
                }
            });
            // Despierta al selector para que procese la cola pendingTasks
            workerSelector.wakeup();
        }

        @Override
        public void run() {
            while (workerSelector.isOpen()) {
                try {
                    // 1. Ejecutar tareas de registro pendientes antes del select
                    Runnable task;
                    while ((task = pendingTasks.poll()) != null) {
                        task.run();
                    }

                    // 2. Ahora sí, esperar eventos de red
                    if (workerSelector.select() <= 0) continue;

                    var keys = workerSelector.selectedKeys();
                    var iter = keys.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();
                        if (key.isValid() && key.isReadable()) {
                            ((NioClientHandler) key.attachment()).processRead();
                        }
                    }
                } catch (Exception e) {
                    Logger.logError("Error en Sub-Reactor " + id + ": " + e.getMessage());
                }
            }
        }
    }
// ... dentro de NioServerEngine ...

    public void unregisterChannel(SocketChannel channel) {
        // Buscamos en todos los workers cuál tiene este canal
        for (SubReactor worker : workers) {
            SelectionKey key = channel.keyFor(worker.workerSelector);
            if (key != null) {
                key.cancel();
                worker.workerSelector.wakeup(); // Importante para aplicar el cancel()
                TOTALCONECTIONS.decrementAndGet();
                return;
            }
        }

    }

    public void disableRead(NioClientHandler handler) {
        SocketChannel channel = handler.getSocketChannel();
        for (SubReactor worker : workers) {
            SelectionKey key = channel.keyFor(worker.workerSelector);
            if (key != null) {
                // Cambiamos los ops a 0 (deja de escuchar lectura)
                key.interestOps(0);
                worker.workerSelector.wakeup();
                return;
            }
        }
    }

    @Override
    public void sendTo(String clientId, Communication payload) throws IOException {
        // Buscamos al cliente por su ID en el registry
        /*var client = context.registry().getClient(clientId);
        if (client != null) {
            client.sendComunicacion(payload);
        }*/
    }
    @Override public void stop() throws IOException { serverChannel.close(); }
    @Override public boolean isActive() { return serverChannel.isOpen(); }
}
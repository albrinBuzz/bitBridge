/**
 * Copyright 2026 [Tu Nombre Completo]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitBridge.server.core;


import org.bitBridge.models.LogEntry;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.server.core.client.BitBridgeClient;
import org.bitBridge.server.core.client.NioClientHandler;
import org.bitBridge.shared.LogLevel;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.network.ServerNetworkEngine;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class NioServerEngine implements ServerNetworkEngine {
    private ServerSocketChannel serverChannel;
    private final ServerContext context;
    private Selector bossSelector;
    private final AtomicInteger TOTALCONECTIONS = new AtomicInteger(0);
    // Configuración de Sub-Reactors
    private final int workerCount = Runtime.getRuntime().availableProcessors();
    //private final int workerCount = 4;
    private SubReactor[] workers;
    private final AtomicInteger roundRobin = new AtomicInteger(0);
    ConfiguracionApp config = ConfiguracionApp.getInstancia();

    public NioServerEngine(ServerContext context) { this.context = context; }
    private Consumer<LogEntry> logListener;

    @Override
    public String start(int port) throws IOException {
        notify("Inicializando Sub-Reactors...", LogLevel.INFO);
        int workerCount = config.obtenerInt(ConfigKey.NET_WORKER_THREADS,
                Runtime.getRuntime().availableProcessors() * 4);

        // 1. Inicializar Workers (Sub-Reactors)
        workers = new SubReactor[workerCount];
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new SubReactor(i);
            new Thread(workers[i], "NIO-Worker-" + i).start();
        }

        //this.bossSelector = Selector.open();
        // 2. Configurar el Main Reactor (Boss) para aceptar conexiones
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false); // El accept puede ser bloqueante en su propio hilo
        serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverChannel.bind(new InetSocketAddress(port));

        new Thread(this::acceptLoop, "NIO-Boss-Acceptor").start();
        notify("Workers listos. Abriendo puerto " + port, LogLevel.SUCCESS);

        String startMsg = String.format("Motor NIO activo en puerto %d con %d workers", port, workerCount);
        notify(startMsg, LogLevel.SUCCESS);
        // IMPORTANTE: No usamos Logger.log aquí si queremos que la UI lo maneje,
        // pero lo retornamos para el CompletableFuture
        return startMsg;
    }



    private void acceptLoop() {
        while (serverChannel.isOpen()) {
            try {
                int maxConnections = config.obtenerInt(ConfigKey.NET_MAX_CONN, 1000);
                boolean useNoDelay = config.obtenerBoolean(ConfigKey.NET_NODELAY, true);

                SocketChannel clientChannel = serverChannel.accept();
                if (clientChannel != null) {
                    if (TOTALCONECTIONS.get() >= maxConnections) {
                        Logger.logInfo("Se alcanzo la cantida de maxima de conexiones");
                        notify("Límite de conexiones alcanzado (" + maxConnections + ")", LogLevel.WARNING);
                        clientChannel.close();
                        continue;
                    }

                    clientChannel.configureBlocking(false);
                    clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, useNoDelay);
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
        boolean found = false;
        for (SubReactor worker : workers) {
            SelectionKey key = channel.keyFor(worker.workerSelector);
            if (key != null) {
                key.cancel();
                worker.workerSelector.wakeup();
                found = true;
                break;
            }
        }
        // Decrementar siempre que se cierre, independientemente de si estaba en un selector
        // para mantener el balance con el incrementAndGet() del acceptLoop
        TOTALCONECTIONS.decrementAndGet();
    }

    public void disableRead(BitBridgeClient handler) {
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

    public void setReadEnabled(BitBridgeClient handler, boolean enabled) {
        SocketChannel channel = handler.getSocketChannel();
        // Encontrar el worker que gestiona este canal
        for (SubReactor worker : workers) {
            SelectionKey key = channel.keyFor(worker.workerSelector);
            if (key != null && key.isValid()) {
                int ops = enabled ? SelectionKey.OP_READ : 0;
                if (key.interestOps() != ops) {
                    key.interestOps(ops);
                    worker.workerSelector.wakeup();
                }
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

    @Override
    public void setLogListener(Consumer<LogEntry> listener) {
        this.logListener = listener;
    }

    @Override
    public Consumer<LogEntry> getLogListener() {
        return logListener;
    }


    private void notify(String msg, LogLevel type) {
        if (logListener != null) {
            logListener.accept(new LogEntry(msg, type));
        }
    }
    @Override public void stop() throws IOException { serverChannel.close(); }
    @Override public boolean isActive() { return serverChannel.isOpen(); }
}
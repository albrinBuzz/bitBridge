/**
 * Copyright 2026 Cristobal Roman Zamora
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
    private final AtomicInteger TOTALCONECTIONS = new AtomicInteger(0);

    // 🚨 CORREGIDO: Única fuente de verdad para el conteo de hilos worker
    private int workerCount;
    private SubReactor[] workers;
    private final AtomicInteger roundRobin = new AtomicInteger(0);
    private final ConfiguracionApp config = ConfiguracionApp.getInstancia();
    private Consumer<LogEntry> logListener;

    public NioServerEngine(ServerContext context) {
        this.context = context;
    }

    @Override
    public String start(int port) throws IOException {
        notify("Inicializando Sub-Reactors...", LogLevel.INFO);

        // 🚨 CORREGIDO: Asignación directa a la variable de instancia de la clase
        this.workerCount = config.obtenerInt(ConfigKey.NET_WORKER_THREADS,
                Runtime.getRuntime().availableProcessors() * 4);

        // 1. Inicializar Workers (Sub-Reactors)
        workers = new SubReactor[workerCount];
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new SubReactor(i);
            new Thread(workers[i], "NIO-Worker-" + i).start();
        }

        // 2. Configurar el Main Reactor (Boss) para escuchar en la red
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverChannel.bind(new InetSocketAddress(port));

        new Thread(this::acceptLoop, "NIO-Boss-Acceptor").start();
        notify("Workers listos. Abriendo puerto " + port, LogLevel.SUCCESS);

        String startMsg = String.format("Motor NIO activo en puerto %d con %d workers", port, workerCount);
        notify(startMsg, LogLevel.SUCCESS);
        return startMsg;
    }

    private void acceptLoop() {
        while (serverChannel.isOpen()) {
            try {
                int maxConnections = config.obtenerInt(ConfigKey.NET_MAX_CONN, 100000);
                boolean useNoDelay = config.obtenerBoolean(ConfigKey.NET_NODELAY, true);

                SocketChannel clientChannel = serverChannel.accept();
                if (clientChannel != null) {
                    // 🚨 CORREGIDO: Validación atómica sin alterar el estado antes de tiempo
                    if (TOTALCONECTIONS.get() >= maxConnections) {
                        Logger.logWarn("Límite de conexiones alcanzado (" + maxConnections + "). Conexión rechazada.");
                        notify("Límite de conexiones alcanzado (" + maxConnections + ")", LogLevel.WARNING);
                        clientChannel.close();
                        continue;
                    }

                    clientChannel.configureBlocking(false);
                    clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, useNoDelay);

                    // 🔒 ENTORNO NUBE: Forzar KeepAlive de TCP para limpiar conexiones muertas por Firewalls
                    clientChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

                    TOTALCONECTIONS.incrementAndGet();

                    // 🚨 CORREGIDO: Math.abs evita romper el índice si roundRobin llega a desbordar el Integer.MAX_VALUE
                    int index = Math.abs(roundRobin.getAndIncrement() % workerCount);
                    workers[index].registerChannel(clientChannel);
                }
            } catch (IOException e) {
                if (serverChannel.isOpen()) {
                    Logger.logError("Error aceptando conexión: " + e.getMessage());
                }
            }
        }
    }

    // Clase interna para el aislamiento de tareas I/O
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
                    NioClientHandler handler = new NioClientHandler(channel, context);
                    SelectionKey key = channel.register(workerSelector, SelectionKey.OP_READ, handler);
                    handler.setSelectionKey(key);
                } catch (ClosedChannelException e) {
                    Logger.logError("Error registrando canal en Worker-" + id);
                    // 🚨 CORREGIDO: Sanar el contador si el canal se murió antes de entrar al selector
                    TOTALCONECTIONS.decrementAndGet();
                }
            });
            workerSelector.wakeup();
        }

        // 🚨 NUEVO: Permite inyectar cambios de estado de llaves de forma segura en el hilo correcto
        public void queueTask(Runnable task) {
            pendingTasks.add(task);
            workerSelector.wakeup();
        }

        @Override
        public void run() {
            while (workerSelector.isOpen()) {
                try {
                    // 1. Consumir tareas de registro o mutación pendientes de forma síncrona
                    Runnable task;
                    while ((task = pendingTasks.poll()) != null) {
                        task.run();
                    }

                    // 2. Bloqueo controlado esperando eventos de sockets sin CPU Burn
                    if (workerSelector.select() <= 0) continue;

                    var keys = workerSelector.selectedKeys();
                    var iter = keys.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();

                        if (key.isValid() && key.isReadable()) {
                            NioClientHandler handler = (NioClientHandler) key.attachment();
                            if (handler != null) {
                                handler.processRead();
                            }
                        }
                    }
                } catch (Exception e) {
                    Logger.logError("Error crítico en Sub-Reactor " + id + ": " + e.getMessage());
                }
            }
        }
    }

    public void unregisterChannel(SocketChannel channel) {
        if (channel == null) return;
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
        TOTALCONECTIONS.decrementAndGet();
    }

    public void disableRead(BitBridgeClient handler) {
        if (handler == null) return;
        SocketChannel channel = handler.getSocketChannel();
        for (SubReactor worker : workers) {
            SelectionKey key = channel.keyFor(worker.workerSelector);
            if (key != null) {
                // 🚨 CORREGIDO: La mutación se delega al hilo del worker para evitar data races en el selector
                worker.queueTask(() -> {
                    if (key.isValid()) {
                        key.interestOps(0);
                    }
                });
                return;
            }
        }
    }

    public void setReadEnabled(BitBridgeClient handler, boolean enabled) {
        if (handler == null) return;
        SocketChannel channel = handler.getSocketChannel();
        for (SubReactor worker : workers) {
            SelectionKey key = channel.keyFor(worker.workerSelector);
            if (key != null) {

                worker.queueTask(() -> {
                    if (key.isValid()) {
                        int ops = enabled ? SelectionKey.OP_READ : 0;
                        key.interestOps(ops);
                    }
                });
                return;
            }
        }
    }

    @Override
    public void sendTo(String clientId, Communication payload) throws IOException {
        // Lógica de ruteo del negocio
    }

    @Override
    public void setLogListener(Consumer<LogEntry> listener) { this.logListener = listener; }

    @Override
    public Consumer<LogEntry> getLogListener() { return logListener; }

    private void notify(String msg, LogLevel type) {
        if (logListener != null) {
            logListener.accept(new LogEntry(msg, type));
        }
    }

    @Override
    public void stop() throws IOException {
        if (serverChannel != null) serverChannel.close();
        if (workers != null) {
            for (SubReactor worker : workers) {
                if (worker.workerSelector != null) worker.workerSelector.close();
            }
        }
    }

    @Override
    public boolean isActive() { return serverChannel != null && serverChannel.isOpen(); }
}
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



package org.bitBridge.Client.core;

import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.network.ClientNetworkEngine;
import org.bitBridge.shared.network.ProtocolService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class NioClientEngine implements ClientNetworkEngine, Runnable {
    private SocketChannel socketChannel;
    private Selector selector;
    private MessageDispatcher dispatcher;
    private boolean running;
    private long connectionStartTime;
    private  CountDownLatch connectionLatch;
    // Buffers para reconstrucción de paquetes JSON
    private ByteBuffer headerBuffer = ByteBuffer.allocate(8);
    private ByteBuffer payloadBuffer;
    private boolean readingHeader = true;

    public NioClientEngine(MessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void connect(String host, int port) throws IOException {
        // RESET de estado para reconexión
        this.connectionLatch = new CountDownLatch(1);
        this.readingHeader = true;
        this.headerBuffer.clear();
        this.payloadBuffer = null;

        this.selector = Selector.open();
        this.socketChannel = SocketChannel.open();
        this.socketChannel.configureBlocking(false);

        this.connectionStartTime = System.currentTimeMillis();
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);

        this.socketChannel.connect(address);
        this.socketChannel.register(selector, SelectionKey.OP_CONNECT);
        this.running = true;

        new Thread(this, "NIO-Client-Worker").start();
    }

    @Override
    public void run() {
        while (running && socketChannel.isOpen()) {
            try {
                if (selector.select(1000) == 0) continue;

                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (key.isConnectable()) {
                        finishConnection(key);
                    } else if (key.isReadable()) {
                        readIncomingData();
                    }
                }
            } catch (IOException e) {
                Logger.logError("Error en loop NIO: " + e.getMessage());
                running = false;
            }
        }
    }

    private void finishConnection(SelectionKey key) throws IOException {
        if (socketChannel.finishConnect()) {
            long duration = System.currentTimeMillis() - connectionStartTime;
            key.interestOps(SelectionKey.OP_READ);

            // ¡ESTO LEVANTA LA VALLA!
            connectionLatch.countDown();

            Logger.logInfo(String.format("NIO: Conexión establecida físicamente en %d ms.", duration));
        }
    }

    private void readIncomingData() throws IOException {
        while (true) { // Loop para procesar todos los mensajes pendientes en el socket
            if (readingHeader) {
                int read = socketChannel.read(headerBuffer);
                if (read == -1) { handleServerDisconnection(); return; }
                if (read == 0 && headerBuffer.position() == 0) return; // No hay nada más que leer
                if (headerBuffer.hasRemaining()) return; // Header incompleto, esperar al Selector

                // --- HEADER COMPLETO ---
                headerBuffer.flip();
                int jsonSize = headerBuffer.getInt();
                int typeSize = headerBuffer.getInt();

                // Sanity Check (Evitar OOM)
                if (typeSize <= 0 || typeSize > 1024 || jsonSize < 0 || jsonSize > 10 * 1024 * 1024) {
                    headerBuffer.clear();
                    throw new IOException("Protocol Desync: Header inválido (" + typeSize + ")");
                }

                payloadBuffer = ByteBuffer.allocate(8 + typeSize + jsonSize);
                headerBuffer.rewind(); // Volvemos al inicio del header para copiarlo íntegro
                payloadBuffer.put(headerBuffer);
                readingHeader = false;
            }

            if (!readingHeader) {
                int read = socketChannel.read(payloadBuffer);
                if (read == -1) { handleServerDisconnection(); return; }
                if (payloadBuffer.hasRemaining()) return; // Cuerpo incompleto, esperar

                // --- PAYLOAD COMPLETO ---
                payloadBuffer.flip();
                byte[] data = payloadBuffer.array();

                try {
                    Communication comm = ProtocolService.fromBytes(data);
                    dispatcher.dispatch(comm);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // RESET para el siguiente mensaje en la ráfaga
                    headerBuffer.clear();
                    payloadBuffer = null;
                    readingHeader = true;
                }
                // El loop continúa: si hay bytes del siguiente mensaje, se procesan YA.
            }
        }
    }

    @Override
    public void send(Communication payload) throws IOException {
        try {
            // Si el latch es null (no se ha llamado a connect), lanzamos error
            if (connectionLatch == null) {
                throw new IOException("No se ha iniciado una conexión.");
            }

            // Esperar a que finishConnection haga el countDown()
            if (!connectionLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IOException("Timeout: El servidor no aceptó la conexión a tiempo.");
            }

            // Validar el estado del canal físico
            if (socketChannel == null || !socketChannel.isConnected() || !socketChannel.isOpen()) {
                throw new IOException("El canal se cerró inesperadamente antes de enviar.");
            }

            ProtocolService.writeNIO(socketChannel, payload);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Envío interrumpido.");
        }
    }

    public void setDispatcher(MessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String getStatus() {
        return "";
    }

    @Override
    public String getHostName() {
        return "";
    }

    @Override
    public void disconnect() throws IOException {
        stop();
    }
    private void handleServerDisconnection() throws IOException {
        Logger.logWarn("[CLIENTE] El servidor ha cerrado la conexión.");
        stop(); // Limpia sockets y para el hilo

        // NOTIFICACIÓN CRÍTICA: Informar al Dispatcher o Controller
        // para que la UI cambie de estado (ej: poner iconos en rojo)
        if (dispatcher != null) {
            dispatcher.onDisconnect();
        }
    }

    @Override
    public void stop() throws IOException {
        this.running = false;
        if (selector != null && selector.isOpen()) {
            selector.wakeup(); // Despierta al hilo si está bloqueado en select(1000)
        }
        if (socketChannel != null) {
            socketChannel.close();
        }
        if (selector != null) {
            selector.close();
        }
        Logger.logInfo("NioClientEngine detenido correctamente.");
    }

    @Override
    public boolean isActive() {
        // Un canal NIO puede estar 'open' pero no 'connected' durante el handshake
        return socketChannel != null && socketChannel.isOpen() && socketChannel.isConnected();
    }
}

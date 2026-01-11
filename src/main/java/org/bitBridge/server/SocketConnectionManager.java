package org.bitBridge.server;

import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.shared.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketConnectionManager implements NetworkServer {
    private final int port;
    private final ClientRegistry registry;
    private final ServerStats stats;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public SocketConnectionManager(int port, ClientRegistry registry, ServerStats stats) {
        this.port = port;
        this.registry = registry;
        this.stats = stats;
    }

    @Override
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    // El handler se encarga de la comunicación individual
                    //ClientHandler handler = new ClientHandler(socket, registry, stats);
                    Socket clientSocket = serverSocket.accept();
                    //ClientHandler handler = new ClientHandler(clientSocket, server);

                    //new Thread(handler).start();
                } catch (IOException e) {
                    if (running) Logger.logError("Error en conexión: " + e.getMessage());
                }
            }
        }, "Network-Acceptor").start();
    }

    @Override
    public void start(int port) throws Exception {

    }

    @Override
    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) { }
    }

    @Override
    public void broadcast(Object message) {

    }

    @Override
    public boolean isRunning() { return running; }

    /*@Override
    public void setEventsListener(ServerEvents listener) { /* Implementar para GUI */

}
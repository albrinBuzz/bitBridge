package org.bitBridge.server;


import org.bitBridge.server.core.Server;
import org.bitBridge.shared.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class NetworkService {
    private final Server server;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public NetworkService(Server server) {
        this.server = server;
    }

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        Thread acceptorThread = new Thread(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    //ClientHandler handler = new ClientHandler(clientSocket, server);

                    //server.getRegistry().addClient(handler);
                   // new Thread(handler).start();
                } catch (IOException e) {
                    if (running) Logger.logError("Error aceptando conexión: " + e.getMessage());
                }
            }
        }, "Network-Acceptor");

        acceptorThread.start();
        Logger.logInfo("Servicio de red iniciado en puerto " + port);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            Logger.logError("Error cerrando ServerSocket: " + e.getMessage());
        }
    }
}
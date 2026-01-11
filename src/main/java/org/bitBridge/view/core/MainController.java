package org.bitBridge.view.core;



import org.bitBridge.Client.core.Client;
import org.bitBridge.server.core.Server;
import org.bitBridge.shared.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class MainController {
    private final Client client;
    private final IMainView view;
    private final Server server;

    public MainController(IMainView view, Client client, Server server) {
        this.view = view;
        this.client = client;
        this.server = server;
    }

    public void startServer() {
        view.updateServerUI(ServerState.STARTING, null);

        CompletableFuture.runAsync(() -> {
            try {
                server.startServer();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).thenRun(() -> {
            view.updateServerUI(ServerState.RUNNING, null);
        }).exceptionally(ex -> {
            Logger.logError("Error crítico: " + ex.getMessage());
            view.updateServerUI(ServerState.ERROR, ex.getMessage());
            return null;
        });
    }

    public void connectServer(String ip, String portStr) {
        // 1. Validación previa (Evita errores de parsing antes de lanzar el hilo)
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            view.updateConnectionUI(ConnectionState.CONNECTION_ERROR, "Puerto inválido");
            return;
        }

        // 2. Notificar inicio de conexión
        view.updateConnectionUI(ConnectionState.CONNECTING, "Iniciando conexión a " + ip);

        CompletableFuture.runAsync(() -> {
            try {
                // Log de depuración
                Logger.logInfo("Intentando conectar a " + ip + ":" + port);

                // Aquí es donde suele quedarse "colgado" si el timeout es infinito
                client.setConexion(ip, port);

            } catch (Exception e) {
                // Forzamos que la excepción suba al bloque 'exceptionally'
                throw new RuntimeException(e);
            }
        }).thenRun(() -> {
            // Se ejecuta solo si setConexion terminó con éxito
            Logger.logInfo("Conexión exitosa");
            view.updateConnectionUI(ConnectionState.CONNECTED, "Conectado a " + ip);
        }).exceptionally(ex -> {
            // Captura fallos de red o errores de lógica
            Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
            Logger.logInfo("Error en conexión: " + cause.getMessage());

            view.updateConnectionUI(ConnectionState.CONNECTION_ERROR, cause.getMessage());
            return null;
        });
    }

    public void stopServer() {
        CompletableFuture.runAsync(() -> {
            server.stopServer();
        }).thenRun(() -> {
            view.updateServerUI(ServerState.STOPPED, null);
        });
    }

    public void disconnectServer() {
        new Thread(() -> {
            client.desconect();
            view.updateConnectionUI(ConnectionState.DISCONNECTED, null);
        }).start();
    }
}

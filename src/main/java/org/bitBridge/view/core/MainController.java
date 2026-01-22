package org.bitBridge.view.core;



import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.NetObserver;
import org.bitBridge.server.core.Server;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.ServerStatusConnection;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class MainController implements NetObserver {
    private final Client client;
    private final IMainView view;
    private final Server server;

    public MainController(IMainView view, Client client, Server server) {
        this.view = view;
        this.client = client;
        this.server = server;

        this.client.addObserver(this);
    }

    public void startServer() {
        view.updateServerUI(ServerState.STARTING, null);

        server.startServer().thenRun(() -> {
            view.updateServerUI(ServerState.RUNNING, null); // Cambiado a RUNNING para que el Label sea Verde
            //addLog("¡Servidor iniciado en puerto " + config.obtener("servidor.puerto") + "!");
        }).exceptionally(ex -> {
            // Extraer la causa real (BindException) de la CompletionException
            Throwable cause = (ex instanceof CompletionException) ? ex.getCause() : ex;
            String friendlyMessage = cause.getMessage();

            Logger.logError("Error crítico: " + friendlyMessage);

            // Forzar actualización de UI con el error
            SwingUtilities.invokeLater(() -> {
                view.updateServerUI(ServerState.ERROR, friendlyMessage);

            });
            return null;
        });

        /*CompletableFuture.runAsync(() -> {
            try {

            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).thenRun(() -> {
            view.updateServerUI(ServerState.RUNNING, null);

        }).exceptionally(ex -> {
            Logger.logError("Error crítico: " + ex.getMessage());
            view.updateServerUI(ServerState.ERROR, ex.getMessage());
            return null;
        });*/
    }

    public void startAutoDiscovery() {
        view.updateConnectionUI(ConnectionState.CONNECTING, "Buscando servidor...");

        long startTime = System.currentTimeMillis();

        // Llamamos al método que ahora devuelve la promesa de conexión real
        client.conexionAutomatica()
                .thenCompose(v -> {
                    // Aplicamos el truco de UX (esperar al menos 1.5s)
                    long duration = System.currentTimeMillis() - startTime;
                    if (duration < 1500) {
                        return CompletableFuture.runAsync(() -> {
                            try { Thread.sleep(1500 - duration); } catch (InterruptedException ignored) {}
                        });
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .thenRun(() -> {
                    // Esto solo se ejecutará si promise.complete(null) fue llamado
                    Logger.logInfo("¡Conexión exitosa confirmada!");
                    view.updateConnectionUI(ConnectionState.CONNECTED, "Conectado");
                    addLog("¡Conexión exitosa confirmada!");
                })
                .exceptionally(ex -> {
                    // Esto se ejecuta si hubo un error o el timeout de 10s expiró
                    Logger.logError("Error de descubrimiento: " + ex.getMessage());
                    view.updateConnectionUI(ConnectionState.CONNECTION_ERROR, "No se encontró el servidor");
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
        CompletableFuture.runAsync(server::stopServer).thenRun(() -> {
            view.updateServerUI(ServerState.STOPPED, null);
        });
    }

    public void disconnectServer() {
        new Thread(() -> {
            client.desconect();
            view.updateConnectionUI(ConnectionState.DISCONNECTED, null);
        }).start();
    }

    public void addLog(String log){
        this.view.addLog(log);
    }


    @Override
    public void onStatusChanged(ServerStatusConnection status) {
        // Importante: El evento viene del hilo "ReadMessages",
        // debemos saltar al hilo de Swing para actualizar la UI.
        java.awt.EventQueue.invokeLater(() -> {
            if (status == ServerStatusConnection.DISCONNECTED) {
                view.updateConnectionUI(ConnectionState.DISCONNECTED, "Conexión perdida con el servidor.");
            }
        });
    }

    // Los demás métodos pueden quedar vacíos si el controlador no los necesita
    @Override public void onMessageReceived(String message) {}
    @Override public void onHostListUpdated(List<ClientInfo> hosts) {}
}

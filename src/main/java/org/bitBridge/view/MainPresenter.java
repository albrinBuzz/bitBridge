package org.bitBridge.view;


import org.bitBridge.Client.core.Client;
import org.bitBridge.server.core.Server;

public class MainPresenter {
    private final FileTalkView view;
    private final Client client;
    private final Server server;

    public MainPresenter(FileTalkView view, Client client) {
        this.view = view;
        this.client = client;
        this.server = Server.getInstance();
    }

    public void handleConnect(String ip, String port) {
        // Lógica de validación aquí
        if(ip.isEmpty()) {
            view.showErrorMessage("Error", "IP no válida");
            return;
        }
        // Llamada al cliente...
        view.updateConnectionStatus("Conectando...", false);
    }

    public void handleStartServer() {
        // Lógica para iniciar servidor...
        view.updateServerStatus("Servidor: Corriendo", true);
    }
}
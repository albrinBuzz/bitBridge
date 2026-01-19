package org.bitBridge.Tests;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.server.core.Server;

import java.io.File;
import java.io.IOException;

public class TestCon {


    public static void main(String[] args) {
        String serverIp = "127.0.0.1";
        int port = 8080;

        try {
            // 1. Iniciar Servidor
            Server servidor = Server.getInstance();
            new Thread(() -> {
                try {
                    servidor.startServer();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            Thread.sleep(100);


            // 3. Emisor
            // IMPORTANTE: Si lanzas esto en la misma PC, el hostname será igual.
            // Tu lógica de servidor añade un "1" al final si el nick se repite.
            Client emisor = new Client();

            emisor.setConexion(serverIp, port);
            emisor.enviarMensaje("Como estas");
            Thread.sleep(200);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

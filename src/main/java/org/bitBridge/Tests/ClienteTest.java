package org.bitBridge.Tests;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.server.core.Server;
import org.bitBridge.shared.Logger;

import java.io.File;

public class ClienteTest {
    public static void main(String[] args) throws Exception {
        // 1. Inicializamos el cliente local (este será el "Emisor")

        //Server server=Server.getInstance();

        //server.starServerCLI(args);

        Client client = new Client();

        client.conexionAutomatica();
        Thread.sleep(1500);
        client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/mi_archivo_50mb.txt"));
        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/BitBridge_Shared/leerExel.py"));
        //client.sendDirectoryToHost(new ClientInfo("fedo"),new File("/home/cris/BitBridge_Shared_Test/"));

    }
}
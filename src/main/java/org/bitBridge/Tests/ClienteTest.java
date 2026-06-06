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
        client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/telemetria_produccion.txt"));
        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/Descargas/yellow_tripdata_2022-01.parquet"));
        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/BitBridge_Shared/leerExel.py"));
        //client.sendDirectoryToHost(new ClientInfo("fedo"),new File("/home/cris/BitBridge_Shared_Test/"));

        //client.sendDirectoryToHost(new ClientInfo("fedo"),new File("/home/cris/BitBridge_Shared/BitBridge_Shared_Test/"));

    }
}
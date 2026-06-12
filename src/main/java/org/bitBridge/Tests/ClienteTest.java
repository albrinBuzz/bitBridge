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

        //client.conexionAutomatica();
        client.setConexion("192.168.100.187",8080);
        //client.setConexion("localhost",8080);
        Thread.sleep(1500);
        //rsync -avhzP --progress --stats target/BitBridge-CLI-CLIENTE.jar target/BitBridge-CLI.jar target/BitBridge-Desktop.jar  cris@192.168.100.192:/home/cris
        //rsync -avhzP --progress --stats /home/cris/baseDatos/ cris@192.168.100.192:/home/cris/BitBridge_Shared/baseDatos


        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/target/BitBridge-CLI.jar"));
        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/target/BitBridge-Desktop.jar"));
        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/target/BitBridge-CLI-CLIENTE.jar"));
        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/telemetria_produccion.txt"));
        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/Descargas/yellow_tripdata_2022-01.parquet"));
        //client.sendFileToHost(new ClientInfo("fedo"),new File("/home/cris/BitBridge_Shared/leerExel.py"));
        //client.sendDirectoryToHost(new ClientInfo("fedo"),new File("/home/cris/BitBridge_Shared_Test/"));

        //client.sendDirectoryToHost(new ClientInfo("fedo"),new File("/home/cris/BitBridge_Shared/BitBridge_Shared_Test/"));

    }
}
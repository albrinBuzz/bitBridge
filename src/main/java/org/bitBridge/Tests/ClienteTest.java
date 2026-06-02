package org.bitBridge.Tests;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;

import java.io.File;

public class ClienteTest {
    public static void main(String[] args) throws InterruptedException {
        Client client=new Client();
        client.conexionAutomatica();
        Thread.sleep(1000);
        client.sendDirectoryToHost(new ClientInfo("frodo"),new File("/home/cris/BitBridge_Shared_Test"));
    }
}

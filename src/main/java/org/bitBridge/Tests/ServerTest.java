package org.bitBridge.Tests;


import org.bitBridge.server.core.Server;

import java.io.IOException;

public class ServerTest {
    public static void main(String[] args) throws IOException {
        Server server=Server.getInstance();

        server.starServerCLI();
    }
}
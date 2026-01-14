package org.bitBridge.Client.network;


import org.bitBridge.shared.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class DiscoveryService {
    private final int UDP_PORT = 9090;

    public ServerInfo discoverServer() throws IOException {
        try (DatagramSocket socketUdp = new DatagramSocket(UDP_PORT)) {
            socketUdp.setSoTimeout(10000); // 10 segundos de espera máximo
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            Logger.logInfo("[UDP] Esperando señal del servidor...");
            socketUdp.receive(packet);

            String received = new String(packet.getData(), 0, packet.getLength());
            // Lógica de split extraída aquí
            String[] argsServer = received.split("\\[|]");
            String address = argsServer[1];
            int port = Integer.parseInt(argsServer[3]);

            return new ServerInfo(address, port);
        }
    }

    // Record sencillo para transportar la data
    public record ServerInfo(String address, int port) {}
}

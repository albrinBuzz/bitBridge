package org.bitBridge.Tests;

import java.net.*;
import java.util.Collections;
import java.util.List;

public class ClienteDescubrimiento {
    public static void main(String[] args) {
        // 1. Buscamos nuestra propia IP real para saber en qué red escuchar
        InetAddress miIp = getAutomaticSelfAddress();
        System.out.println("Buscando servidores desde mi IP: " + (miIp != null ? miIp.getHostAddress() : "Desconocida"));

        try {
            // Creamos el socket en el puerto 8888
            // Usamos 0.0.0.0 para escuchar en todas, pero el truco está en setBroadcast
            DatagramSocket socket = new DatagramSocket(8888, InetAddress.getByName("0.0.0.0"));
            socket.setBroadcast(true);

            System.out.println("Esperando señal del servidor (Broadcast)...");

            byte[] recvBuf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);

            // El programa se queda aquí hasta que llegue un paquete
            while (true) {
                socket.receive(packet);

                String mensaje = new String(packet.getData(), 0, packet.getLength());
                String ipServidor = packet.getAddress().getHostAddress();

                // Verificamos que el mensaje sea el nuestro (BitBridge)
                if (mensaje.startsWith("SERVIDOR_VIDEO_IP:")) {
                    System.out.println("\n----------------------------------------------");
                    System.out.println("¡SERVIDOR ENCONTRADO!");
                    System.out.println("Nombre/IP anunciada: " + mensaje.split(":")[1]);
                    System.out.println("IP detectada por socket: " + ipServidor);
                    System.out.println("----------------------------------------------");
                    
                    // Aquí podrías romper el bucle y empezar la descarga del video de 1GB
                    // break; 
                }
            }
        } catch (Exception e) {
            System.err.println("Error: Asegúrate de que no haya otro programa usando el puerto 8888.");
            e.printStackTrace();
        }
    }

    /**
     * Misma lógica del servidor para mantener consistencia en la red
     */
    public static InetAddress getAutomaticSelfAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface iface : interfaces) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                String name = iface.getDisplayName().toLowerCase();
                if (iface.isVirtual() || name.contains("virtual") || name.contains("vbox") || 
                    name.contains("vmware") || name.contains("pseudo")) continue;

                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address) return addr;
                }
            }
        } catch (Exception e) { }
        try { return InetAddress.getLocalHost(); } catch (Exception e) { return null; }
    }
}
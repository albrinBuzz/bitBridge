package org.bitBridge.Tests;

import java.net.*;
import java.util.Collections;
import java.util.List;

public class ServidorBroadcast {
    public static void main(String[] args) {
        try {
            // 1. Detectar la IP correcta automáticamente
            InetAddress ipLocal = getAutomaticSelfAddress();
            
            if (ipLocal == null) {
                System.err.println("No se pudo detectar una interfaz de red activa.");
                return;
            }

            System.out.println("----------------------------------------------");
            System.out.println("INTERFAZ DETECTADA: " + ipLocal.getHostAddress());
            System.out.println("----------------------------------------------");

            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);

            // 2. Construir el mensaje usando la IP que detectamos
            String mensaje = "SERVIDOR_VIDEO_IP:" + ipLocal.getHostAddress();
            byte[] buffer = mensaje.getBytes();

            // Dirección de broadcast universal
            InetAddress address = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 8888);

            System.out.println("Anunciando servidor en el puerto 8888 cada 3 segundos...");
            
            while (true) {
                socket.send(packet);
                System.out.println("Enviando anuncio: " + mensaje);
                Thread.sleep(3000); 
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Busca la IP real de la máquina, ignorando interfaces virtuales (VirtualBox, VMware)
     */
    public static InetAddress getAutomaticSelfAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            
            for (NetworkInterface iface : interfaces) {
                // Filtros básicos: debe estar encendida y no ser local (127.0.0.1)
                if (!iface.isUp() || iface.isLoopback()) continue;

                // Filtro "Anti-Virtual": Ignorar interfaces de software de virtualización
                String name = iface.getDisplayName().toLowerCase();
                if (iface.isVirtual() || name.contains("virtual") || name.contains("vbox") || 
                    name.contains("vmware") || name.contains("pseudo") || name.contains("virtualbox")) {
                    continue;
                }

                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    // Solo nos interesan direcciones IPv4
                    if (addr instanceof Inet4Address) {
                        return addr; 
                    }
                }
            }
        } catch (Exception e) {
            // Silencioso
        }
        
        // Si todo falla, intentamos el método estándar
        try { 
            return InetAddress.getLocalHost(); 
        } catch (Exception e) { 
            return null; 
        }
    }
}
package org.bitBridge.Tests;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

public class BuscadorIP {
    public static void main(String[] args) {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                
                // Filtramos: debe estar activa, no ser loopback y no ser virtual (como VirtualBox)
                if (netint.isUp() && !netint.isLoopback() && !netint.isVirtual()) {
                    
                    Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                    for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                        
                        // Solo nos interesan las IPv4 (como la 10.155.x.x)
                        if (inetAddress instanceof Inet4Address) {
                            System.out.println("Interfaz: " + netint.getDisplayName());
                            System.out.println("IP Local Real: " + inetAddress.getHostAddress());
                            System.out.println("-----------------------------------");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
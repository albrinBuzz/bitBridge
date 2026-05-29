package org.bitBridge.shared.network;

import org.bitBridge.server.core.Server;
import org.bitBridge.shared.LogLevel;
import org.bitBridge.shared.Logger;
import javax.jmdns.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class NetworkManager {
    private final List<JmDNS> jmdnsInstances = new ArrayList<>();
    private final String SERVICE_TYPE = "_bitbridge._tcp.local.";
    private final AtomicBoolean isScanning = new AtomicBoolean(false);

    /**
     * Obtiene TODAS las IPs IPv4 válidas de las interfaces activas.
     * Esto evita quedar atrapado en 127.0.0.1 o IPs de Docker/VirtualBox.
     */
    public static List<InetAddress> getAllLocalIps() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                // Filtramos interfaces inactivas, loopback o puramente virtuales si es posible
                if (netint.isUp() && !netint.isLoopback()) {
                    Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                    for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                        // Solo IPv4 para evitar complicaciones de ruteo en redes locales simples
                        if (inetAddress instanceof Inet4Address) {
                            addresses.add(inetAddress);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            Logger.logError("Error al listar interfaces: " + e.getMessage());
        }
        return addresses;
    }

    // --- SECCIÓN SERVIDOR (ANUNCIO EN TODAS LAS INTERFACES) ---

    public void startServerAnnouncement(int port, String serverName, Server server) {
        List<InetAddress> targetIps = getAllLocalIps();

        if (targetIps.isEmpty()) {
            Logger.logError("[mDNS] No se encontraron interfaces de red activas.");
            return;
        }

        for (InetAddress addr : targetIps) {
            try {
                // Creamos una instancia de JmDNS por cada interfaz física/wifi
                JmDNS jmdns = JmDNS.create(addr, serverName + "-" + addr.getHostAddress());
                jmdnsInstances.add(jmdns);

                ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE,
                        serverName, port, "owner=" + System.getProperty("user.name"));

                jmdns.registerService(serviceInfo);

                Logger.logInfo(String.format("[📡] ANUNCIANDO EN: %s | IP: %s | Puerto: %d",
                        netInterfaceName(addr), addr.getHostAddress(), port));

                server.notifyUI(String.format("[📡] ANUNCIANDO EN: %s | IP: %s | Puerto: %d",
                        netInterfaceName(addr), addr.getHostAddress(), port), LogLevel.INFO);

            } catch (IOException e) {
                Logger.logWarn("[mDNS] No se pudo anunciar en " + addr.getHostAddress() + ": " + e.getMessage());
            }
        }
    }

    // --- SECCIÓN CLIENTE (BÚSQUEDA MULTI-INTERFAZ) ---

    public void startLookingForServers(BiConsumer<String, Integer> onServerFound) {
        if (isScanning.getAndSet(true)) {
            Logger.logWarn("[mDNS] Escaneo ya en curso.");
            return;
        }

        List<InetAddress> targetIps = getAllLocalIps();
        for (InetAddress addr : targetIps) {
            try {
                JmDNS jmdns = JmDNS.create(addr, "BitBridge-Scanner-" + addr.getHostAddress());
                jmdnsInstances.add(jmdns);

                Logger.logInfo("[🔍] Escaneando desde interfaz: " + addr.getHostAddress());

                jmdns.addServiceListener(SERVICE_TYPE, new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {
                        jmdns.requestServiceInfo(event.getType(), event.getName());
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        Logger.logInfo("[-] Nodo desconectado: " + event.getName());
                    }

                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        ServiceInfo info = event.getInfo();
                        String[] addresses = info.getHostAddresses();
                        if (addresses.length > 0) {
                            // Devolvemos la IP encontrada
                            onServerFound.accept(addresses[0], info.getPort());
                        }
                    }
                });
            } catch (IOException e) {
                Logger.logError("[mDNS] Error al iniciar scanner en " + addr.getHostAddress());
            }
        }
    }

    private String netInterfaceName(InetAddress addr) {
        try {
            return NetworkInterface.getByInetAddress(addr).getDisplayName();
        } catch (Exception e) { return "Desconocida"; }
    }

    public void stopAll() {
        for (JmDNS jmdns : jmdnsInstances) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
            } catch (IOException e) { /* Ignorar al cerrar */ }
        }
        jmdnsInstances.clear();
        isScanning.set(false);
        Logger.logInfo("[🧹] NetworkManager: Todas las instancias JmDNS cerradas.");
    }
}
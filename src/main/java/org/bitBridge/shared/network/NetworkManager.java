package org.bitBridge.shared.network;

import org.bitBridge.server.core.Server;
import org.bitBridge.shared.LogLevel;
import org.bitBridge.shared.Logger;
import javax.jmdns.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class NetworkManager {
    private final List<JmDNS> jmdnsInstances = new ArrayList<>();
    private final String SERVICE_TYPE = "_bitbridge._tcp.local.";
    private final AtomicBoolean isScanning = new AtomicBoolean(false);

    /**
     * Obtiene TODAS las IPs IPv4 válidas de las interfaces físicas activas.
     * Purga de raíz interfaces virtuales como docker0, br-X, vethX, vboxnetX, lo, etc.
     */
    public static List<InetAddress> getAllLocalIps() {
        List<InetAddress> addresses = new ArrayList<>();

        // Patrón para descartar interfaces virtuales comunes en Linux/Windows/Mac
        String ignorePattern = "^(br-|docker|veth|vboxnet|lo|tun|tap|p2p|wlo|vnic|dummy).*";

        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                String name = netint.getName().toLowerCase();

                // 1. Filtrar por banderas nativas del sistema operativo
                if (!netint.isUp() || netint.isLoopback() || netint.isVirtual()) {
                    continue;
                }

                // 2. Control estricto por software: Purgar nombres de software de virtualización/puentes
                if (name.matches(ignorePattern)) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                    // Solo IPv4 para mantener consistencia en la red local física
                    if (inetAddress instanceof Inet4Address) {
                        addresses.add(inetAddress);
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

        Logger.logInfo("[mDNS] Iniciando subsistema de descubrimiento ZeroConf (JmDNS)...");

        if (targetIps == null || targetIps.isEmpty()) {
            String errLog = "[mDNS] [❌] ERROR CRÍTICO: No se encontraron interfaces físicas activas (IPv4) para binding.";
            Logger.logError(errLog);
            server.notifyUI(errLog, LogLevel.ERROR);
            return;
        }

        Logger.logInfo(String.format("[mDNS] Detectadas %d interfaces aptas para multidifusión local.", targetIps.size()));

        for (InetAddress addr : targetIps) {
            String ifaceName = netInterfaceName(addr);
            String hostAddr = addr.getHostAddress();

            try {
                long startTime = System.currentTimeMillis();

                // Generar identificador único para evitar colisiones de nombres mDNS en el mismo segmento
                String scopedInstanceName = String.format("%s-%s", serverName, hostAddr.replace(".", "-"));

                // Forzar enlace exclusivo a la interfaz física actual
                JmDNS jmdns = JmDNS.create(addr, scopedInstanceName);
                jmdnsInstances.add(jmdns);

                // Propiedades adicionales (TXT Records) para auditoría de infraestructura remota
                String txtOwner = "owner=" + System.getProperty("user.name", "unknown");
                String txtOs = "os=" + System.getProperty("os.name", "Linux").replace(" ", "_");
                String txtType = "infra=BitBridge-Hub";

                ServiceInfo serviceInfo = ServiceInfo.create(
                        SERVICE_TYPE,
                        serverName,
                        port,
                        0, 0, // weight, priority (estándar)
                        true, // textRecord como mapa persistente
                        Map.of("owner", txtOwner, "os", txtOs, "type", txtType)
                );

                // Registro en el bus mDNS (Lanza paquetes UDP Multicast a la dirección 224.0.0.251)
                jmdns.registerService(serviceInfo);

                long duration = System.currentTimeMillis() - startTime;

                // Log Estructurado de Éxito
                String successLog = String.format(
                        "[📡 mDNS] BROADCAST UP -> IFACE: %-6s | IP: %-15s | PORT: %d | TYPE: %s | INSTANCE: %s (%d ms)",
                        ifaceName, hostAddr, port, SERVICE_TYPE, scopedInstanceName, duration
                );

                Logger.logInfo(successLog);
                server.notifyUI(successLog, LogLevel.INFO);

            } catch (IOException e) {
                String warnLog = String.format(
                        "[⚠️ mDNS FAILED] -> No se pudo instanciar socket multicast en IFACE: %s (%s). Motivo: %s",
                        ifaceName, hostAddr, e.getMessage()
                );
                Logger.logWarn(warnLog);
                server.notifyUI(warnLog, LogLevel.WARNING);
            } catch (Exception e) {
                // Catch genérico por interfaz para evitar colapsar todo el arranque si una interfaz virtual (Docker/VBox) falla
                String errLog = String.format(
                        "[❌ mDNS CRITICAL] -> Error inesperado en bound de interfaz %s: %s",
                        hostAddr, e.getMessage()
                );
                Logger.logError(errLog);
                server.notifyUI(errLog, LogLevel.ERROR);
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

                Logger.logInfo("[🔍] Escaneando desde interfaz física: " + addr.getHostAddress());

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
                            // Devolvemos la IP física encontrada
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
        Logger.logInfo("[🧹] NetworkManager: Todas las instancias JmDNS cerradas de forma limpia.");
    }
}
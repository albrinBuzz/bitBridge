package org.bitBridge.utils;



import org.bitBridge.shared.Logger;

import javax.jmdns.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class NetworkManager {
    private JmDNS jmdns;
    private final String SERVICE_TYPE = "_bitbridge._tcp.local.";
    private final AtomicBoolean isScanning = new AtomicBoolean(false);

    /**
     * Obtiene la IP local real (evita problemas con interfaces virtuales como Docker/VMWare)
     */
    public static String getLocalIp() {

        try  {
            InetAddress ip = InetAddress.getLocalHost();
            //System.out.println("IP local: " + ip.getHostAddress());
            // No necesita conectarse realmente, solo abrir el puerto hacia afuera para ver qué IP usa
            //socket.connect(new InetSocketAddress("8.8.8.8", 80), 1000);
            return ip.getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // --- SECCIÓN SERVIDOR (ANUNCIO) ---

    // --- SECCIÓN SERVIDOR (ANUNCIO) ---

    public void startServerAnnouncement(int port, String serverName) {
        try {
            String localIp = getLocalIp();
            InetAddress addr = InetAddress.getByName(localIp);

            if (jmdns == null) {
                jmdns = JmDNS.create(addr, serverName + "-mdns");
                Logger.logInfo("[mDNS] Nodo creado en interfaz: " + addr.getHostAddress());
            }

            ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE,
                    serverName, port, "owner=" + System.getProperty("user.name"));

            jmdns.registerService(serviceInfo);

            Logger.logInfo(String.format("[📡] SERVIDOR ACTIVO: [%s] | IP: %s | Puerto: %d",
                    serverName, localIp, port));

        } catch (IOException e) {
            Logger.logError("[mDNS] Error crítico al anunciar servidor: " + e.getMessage());
        }
    }

    // --- SECCIÓN CLIENTE (BÚSQUEDA) ---

    public void startLookingForServers(BiConsumer<String, Integer> onServerFound) {
        if (isScanning.getAndSet(true)) {
            Logger.logWarn("[mDNS] El escaneo ya está en curso.");
            //return;
        }

        try {
            InetAddress addr = InetAddress.getByName(getLocalIp());
            if (jmdns == null) {
                jmdns = JmDNS.create(addr, "BitBridge-Client");
            }

            Logger.logInfo("[🔍] Iniciando búsqueda de nodos en la red: " + addr.getHostAddress());

            jmdns.addServiceListener(SERVICE_TYPE, new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    Logger.logInfo("[+] Servicio detectado: " + event.getName() + ". Resolviendo...");
                    jmdns.requestServiceInfo(event.getType(), event.getName());
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    Logger.logInfo("[-] Servicio fuera de línea: " + event.getName());
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    ServiceInfo info = event.getInfo();
                    String[] addresses = info.getHostAddresses();

                    if (addresses.length > 0) {
                        String ip = addresses[0];
                        int port = info.getPort();
                        Logger.logInfo(String.format("[✨] NODO RESUELTO: %s -> %s:%d",
                                event.getName(), ip, port));
                        onServerFound.accept(ip, port);
                    } else {
                        Logger.logWarn("[?] No se pudo resolver la dirección para: " + event.getName());
                    }
                }
            });

        } catch (IOException e) {
            Logger.logError("[mDNS] Error en el cliente de búsqueda: " + e.getMessage());
        }
    }

    // --- LIMPIEZA (IMPORTANTE PARA EL FIREWALL) ---

    public void stopAll() {
        if (jmdns != null) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
                jmdns = null;
                isScanning.set(false);
                System.out.println("[🧹] Servicios de red detenidos y puertos liberados.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // --- UTILIDADES EXTERNAS ---

    public String getPublicIP() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .build();

            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return "No disponible";
        }
    }
}
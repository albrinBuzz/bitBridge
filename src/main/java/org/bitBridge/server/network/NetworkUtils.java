package org.bitBridge.server.network;


import javax.jmdns.*;
import java.net.*;
import java.net.http.*;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public class NetworkUtils {
    private JmDNS jmdns;
    public static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static InetAddress getBroadcastAddress() {

        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            byte[] ip = localAddress.getAddress();
            ip[3] = (byte) 255; // Fuerza el broadcast a la máscara 255.255.255.0
            return InetAddress.getByAddress(ip);
        } catch (Exception e) {
            return null;
        }
    }

    public void startDiscovery(int port, String serverName) {
        try {
            // 1. Obtener la IP local
            InetAddress addr = InetAddress.getLocalHost();

            // 2. Crear instancia de JmDNS
            jmdns = JmDNS.create(addr);

            // 3. Registrar el servicio: tipo "_bitbridge._tcp.local."
            // Esto es mucho más amigable con el firewall que un Broadcast UDP manual
            ServiceInfo serviceInfo = ServiceInfo.create("_bitbridge._tcp.local.",
                    serverName, port, "BitBridge Transfer Service");

            jmdns.registerService(serviceInfo);
            System.out.println("[AntiFirewall] Servidor anunciado automáticamente como: " + serverName);

        } catch (Exception e) {
            System.err.println("Error en el anuncio del servidor: " + e.getMessage());
        }
    }

    public void lookForServers() {
        try {
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

            // Escuchamos el tipo de servicio específico de BitBridge
            jmdns.addServiceListener("_bitbridge._tcp.local.", new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    // El servicio fue detectado, ahora pedimos los detalles (IP/Port)
                    jmdns.requestServiceInfo(event.getType(), event.getName());
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    System.out.println("Servidor desconectado: " + event.getName());
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    // ¡AQUÍ TENEMOS LA CONEXIÓN!
                    String ip = event.getInfo().getInetAddresses()[0].getHostAddress();
                    int port = event.getInfo().getPort();
                    System.out.println("[MÁGICO] Servidor encontrado en: " + ip + ":" + port);
                    // Aquí ya puedes llamar a tu lógica de conexión TCP
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getPublicIP(){
        try {
            // URL del servicio que devuelve la IP pública
            String url = "https://api.ipify.org"; // También puedes usar "https://checkip.amazonaws.com" o "https://icanhazip.com"

            // Crear el cliente HTTP
            HttpClient client = HttpClient.newHttpClient();

            // Crear la solicitud GET
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            // Enviar la solicitud y obtener la respuesta
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Mostrar la IP pública
            System.out.println("IP pública: " + response.body());
            return response.body();
        } catch (Exception e) {

            e.printStackTrace();
            return  "";
        }
    }
    /*public String getPublicIP(){
        try {
            // URL del servicio que devuelve la IP pública
            String url = "https://api.ipify.org"; // También puedes usar "https://checkip.amazonaws.com" o "https://icanhazip.com"

            // Crear el cliente HTTP
            HttpClient client = HttpClient.newHttpClient();

            // Crear la solicitud GET
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            // Enviar la solicitud y obtener la respuesta
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Mostrar la IP pública
            System.out.println("IP pública: " + response.body());
            return response.body();
        } catch (Exception e) {

            e.printStackTrace();
            return  "";
        }
    }*/
}
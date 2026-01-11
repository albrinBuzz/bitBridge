package org.bitBridge.server.network;


import java.net.*;
import java.net.http.*;

public class NetworkUtils {
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
            ip[3] = (byte) 255;
            return InetAddress.getByAddress(ip);
        } catch (Exception e) {
            return null;
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
package org.bitBridge.Client.network;

import org.bitBridge.Client.services.AudioPlaybackService;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpStreamSender {
    private DatagramSocket udpSocket;
    private InetAddress serverAddress;
    private int port;

    public UdpStreamSender(String host, int port) throws Exception {
        this.udpSocket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName(host);
        this.port = port;
    }

    public void sendFrame(byte[] data) {
        try {
            // UDP no garantiza orden ni llegada, pero es instantáneo
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, port);
            udpSocket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startUdpRelay() {
        new Thread(() -> {
            try (DatagramSocket serverSocket = new DatagramSocket(7878)) {
                byte[] buffer = new byte[65507]; // Tamaño máximo UDP

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                    serverSocket.receive(receivePacket);

                    // Lógica de relay: El servidor sabe a quién enviar por un ID en el paquete
                    // o por una tabla de sesiones previa hecha por TCP
                    //forwardPacket(receivePacket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void startUdpReceiver(AudioPlaybackService audioService) {
        new Thread(() -> {
            try (DatagramSocket receiverSocket = new DatagramSocket(7878)) {
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    receiverSocket.receive(packet);

                    // Directo al hardware sin pasar por el Dispatcher de Objetos
                    audioService.play(packet.getData());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
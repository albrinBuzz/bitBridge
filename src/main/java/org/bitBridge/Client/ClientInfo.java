package org.bitBridge.Client;

import java.io.Serializable;
import java.net.Socket;

public class ClientInfo implements Serializable {
    private String address;
    private long connectionTime;
    private String nick;
    private int port;

    public ClientInfo(Socket socket, String nick,int puerto) {
        this.address = socket.getInetAddress().toString();
        this.address=address.substring(1);
        this.connectionTime = System.currentTimeMillis();
        this.port=puerto;
        this.nick=nick;
    }
    // Constructor para pruebas o cuando el socket no está disponible inmediatamente
    public ClientInfo(String address, String nick, int puerto) {
        this.address = address;
        this.nick = nick;
        this.port = puerto;
        this.connectionTime = System.currentTimeMillis();
    }



    public String getAddress() {
        return address;
    }

    public long getConnectionTime() {
        //return System.currentTimeMillis() - connectionTime;
        return connectionTime;
    }

    public String formatUptime() {
        long uptime = System.currentTimeMillis() - connectionTime;
        long seconds = (uptime / 1000) % 60;
        long minutes = (uptime / (1000 * 60)) % 60;
        long hours = (uptime / (1000 * 60 * 60)) % 24;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getNick() {
        return this.nick;
    }
}

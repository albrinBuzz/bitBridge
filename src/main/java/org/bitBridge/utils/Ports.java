package org.bitBridge.utils;

import java.io.IOException;

public class Ports {
    public static void abrirPuertos() {
        String OS = System.getProperty("os.name").toLowerCase();

        if (OS.indexOf("win") >= 0) {
            // Abrir puertos TCP 9090 y 9091
            String[] command1 = {
                    "powershell.exe",
                    "-Command",
                    "New-NetFirewallRule -DisplayName 'Abrir Puerto 9090 Inbound' -Profile 'Private,Public' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 9090"
            };
            ejecucion(command1);

            String[] command2 = {
                    "powershell.exe",
                    "-Command",
                    "New-NetFirewallRule -DisplayName 'Abrir Puerto 9091 Inbound' -Profile 'Private,Public' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 9091"
            };
            ejecucion(command2);

            // Comando para permitir salida (Outbound) en los puertos 9090 y 9091
            String[] command3 = {
                    "powershell.exe",
                    "-Command",
                    "New-NetFirewallRule -DisplayName 'Abrir Puerto 9090 Outbound' -Profile 'Private,Public' -Direction Outbound -Action Allow -Protocol TCP -LocalPort 9090"
            };
            ejecucion(command3);

            String[] command4 = {
                    "powershell.exe",
                    "-Command",
                    "New-NetFirewallRule -DisplayName 'Abrir Puerto 9091 Outbound' -Profile 'Private,Public' -Direction Outbound -Action Allow -Protocol TCP -LocalPort 9091"
            };
            ejecucion(command4);

            // Comando para abrir el puerto UDP 9092
            String[] command5 = {
                    "powershell.exe",
                    "-Command",
                    "New-NetFirewallRule -DisplayName 'Abrir Puerto 9092 Inbound' -Profile 'Private,Public' -Direction Inbound -Action Allow -Protocol UDP -LocalPort 9092"
            };
            ejecucion(command5);

            String[] command6 = {
                    "powershell.exe",
                    "-Command",
                    "New-NetFirewallRule -DisplayName 'Abrir Puerto 9092 Outbound' -Profile 'Private,Public' -Direction Outbound -Action Allow -Protocol UDP -LocalPort 9092"
            };
            ejecucion(command6);

        } else {
            // Abrir puertos TCP 9090, 9091 y UDP 9092 en Linux
            String[] command1 = {
                    "tilix", // Puedes usar otros terminales si no usas GNOME
                    "--","bash",
                    "-c",
                    "sudo firewall-cmd --zone=public --add-port=9090/tcp --permanent"
            };
            ejecucion(command1);

            String[] command2 = {
                    "tilix", // Puedes usar otros terminales si no usas GNOME
                    "--","bash",
                    "-c",
                    "sudo firewall-cmd --zone=public --add-port=9091/tcp --permanent"
            };
            ejecucion(command2);

            String[] command3 = {
                    "tilix", // Puedes usar otros terminales si no usas GNOME
                    "--","bash",
                    "-c",
                    "sudo firewall-cmd --zone=public --add-port=9092/udp --permanent"
            };
            ejecucion(command3);

            // Recargar la configuración del firewall
            String[] command4 = {
                    "tilix", // Puedes usar otros terminales si no usas GNOME
                    "--","bash",
                    "-c",
                    "sudo firewall-cmd --reload"
            };
            ejecucion(command4);
        }
    }

    private static void ejecucion(String... argumentos) {
        ProcessBuilder processBuilder = new ProcessBuilder(argumentos);
        try {
            Process process = processBuilder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage());
            System.out.println("Traza Debug");
            e.printStackTrace();
        }
    }
}

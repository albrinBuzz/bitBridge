package org.bitBridge.shared.network;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

public class NetworkDiagnosticEngine {

    // Puertos organizados por categorías para un escaneo exhaustivo
    private static final Map<Integer, String> SERVICE_MAP = Map.ofEntries(
            // --- NÚCLEO BITBRIDGE ---
            Map.entry(8080, "BitBridge Core / Web-Alt"),
            Map.entry(8888, "BitBridge Proxy / Jupyter"),
            Map.entry(5050, "BitBridge Stream / PGAdmin"),
            Map.entry(9090, "BitBridge Auth / Prometheus"),
            Map.entry(7070, "BitBridge Control / AnyDesk"),

            // --- INFRAESTRUCTURA & ACCESO REMOTO ---
            Map.entry(22, "SSH (Linux/Admin)"),
            Map.entry(23, "Telnet (Unsecure)"),
            Map.entry(3389, "RDP (Windows Remote)"),
            Map.entry(5900, "VNC Server"),
            Map.entry(21, "FTP Control"),

            // --- WEB & MICROSERVICIOS ---
            Map.entry(80, "HTTP Web Server"),
            Map.entry(443, "HTTPS Secure"),
            Map.entry(3000, "Node.js / React Dev"),
            Map.entry(5000, "Flask / Docker Registry"),
            Map.entry(8000, "Django / Python Dev"),
            Map.entry(8443, "HTTPS Alt / Tomcat"),

            // --- BASES DE DATOS & CACHÉ ---
            Map.entry(3306, "MySQL / MariaDB"),
            Map.entry(5432, "PostgreSQL"),
            Map.entry(6379, "Redis (In-Memory)"),
            Map.entry(27017, "MongoDB"),
            Map.entry(1433, "MSSQL Server"),
            Map.entry(9200, "ElasticSearch"),
            Map.entry(11211, "Memcached"),

            // --- CONTENEDORES & DEVOPS ---
            Map.entry(2375, "Docker API (Unsecure)"),
            Map.entry(2376, "Docker API (TLS)"),
            Map.entry(6443, "Kubernetes API"),
            Map.entry(15672, "RabbitMQ Management"),

            // --- SERVICIOS DE RED & LAN ---
            Map.entry(53, "DNS Server"),
            Map.entry(445, "SMB / Samba (File Share)"),
            Map.entry(1883, "MQTT (IoT Discovery)"),
            Map.entry(5353, "mDNS / Avahi (Discovery)")
    );
    // Array plano para el bucle de escaneo
    private static final int[] ALL_PORTS = SERVICE_MAP.keySet().stream().mapToInt(i -> i).toArray();
    private static final int SCAN_TIMEOUT = 120; // ms

    /**
     * NIVEL 1: Quick Audit de Red (Instantáneo)
     * Foco: Subredes, Broadcast y Segmentación.
     */
    public static String getQuickAudit(String primaryIp) {
        StringBuilder sb = new StringBuilder();
        sb.append(" ╔══════════════════════════════════════════════════════════════╗\n");
        sb.append(" ║  BITBRIDGE NETWORK                    ║\n");
        sb.append(" ╚══════════════════════════════════════════════════════════════╝\n\n");

        try {
            sb.append(" 🌐 [ ANÁLISIS DE CAPA 3: SUBRED & BROADCAST ]\n");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback()) continue;

                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (!(ia.getAddress() instanceof Inet4Address)) continue;

                    String ip = ia.getAddress().getHostAddress();
                    short prefix = ia.getNetworkPrefixLength();
                    String subnet = calculateSubnet(ip, prefix);
                    String broadcast = (ia.getBroadcast() != null) ? ia.getBroadcast().getHostAddress() : "NO SOPORTADO";

                    sb.append(String.format(" ■ INTERFAZ: %-10s\n", ni.getName().toUpperCase()));
                    sb.append(String.format("   ├─ IP Local:      %-15s\n", ip));
                    sb.append(String.format("   ├─ ID de Red:     %-15s (/%d)\n", subnet, prefix));
                    sb.append(String.format("   ├─ Broadcast Dir: %-15s\n", broadcast));

                    // Verificación de capacidad de Broadcast (UDP)
                    if (ni.supportsMulticast()) {
                        sb.append("   ├─ Multicast:     SOPORTADO (Ideal para auto-discovery)\n");
                    } else {
                        sb.append("   ├─ Multicast:     [!] NO SOPORTADO (Discovery manual requerido)\n");
                    }

                    // Debug de IPs Reservadas/Especiales
                    if (ip.startsWith("192.168.1.0") || ip.endsWith(".255") || ip.endsWith(".1")) {
                        sb.append("   │  [!] INFO: IP en rango crítico (Red/BC/Gateway).\n");
                    }

                    if (ip.equals(primaryIp)) sb.append("   ├─ STATUS: >>> CURRENT BINDING NODE <<<\n");
                }
            }
        } catch (Exception e) { sb.append(" [!] ERROR L3: ").append(e.getMessage()); }
        return sb.toString();
    }

    /**
     * NIVEL 2: Connectivity & Route Debug (1-3 seg)
     */
    public static String runConnectivityTest() {
        StringBuilder sb = new StringBuilder("\n ⚡ [ DEBUG DE RUTEAMIENTO & LATENCIA ]\n");
        String gw = detectGateway();
        sb.append("  ├─ Gateway (Default): ").append(gw).append("\n");

        long gPing = pingHost(gw);
        if (gPing < 0) {
            sb.append("  │  [!] ALERTA: Gateway inaccesible (Ping bloqueado o aislamiento).\n");
        } else {
            sb.append("  │  └─ Latencia Gateway: ").append(gPing).append("ms\n");
        }

        // Prueba de DNS externo para verificar ruteo fuera de la LAN
        sb.append("  ├─ DNS (Google 8.8.8.8): ").append(pingHost("8.8.8.8") >= 0 ? "ACCESIBLE" : "BLOQUEADO").append("\n");
        sb.append("  └─ DNS (Cloudflare 1.1.1.1): ").append(pingHost("1.1.1.1") >= 0 ? "ACCESIBLE" : "BLOQUEADO").append("\n");

        return sb.toString();
    }

    /**
     * NIVEL 3: Deep LAN Mapping (3-6 seg)
     * Foco: Descubrir nodos BitBridge activos.
     */
    public static String runDeepPortScan(String primaryIp) {
        StringBuilder sb = new StringBuilder("\n 🚀 [ BITBRIDGE NETWORK MAP ]\n");
        sb.append(" ══════════════════════════════════════════════════════════════\n");

        try {
            InetAddress localAddr = InetAddress.getByName(primaryIp);
            NetworkInterface ni = NetworkInterface.getByInetAddress(localAddr);
            String subnet = "0.0.0.0";
            int prefix = 24;

            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                if (ia.getAddress() instanceof Inet4Address && ia.getAddress().getHostAddress().equals(primaryIp)) {
                    prefix = ia.getNetworkPrefixLength();
                    subnet = calculateSubnet(primaryIp, (short)prefix);
                    break;
                }
            }

            String baseIp = subnet.substring(0, subnet.lastIndexOf(".") + 1);
            ExecutorService executor = Executors.newFixedThreadPool(100);
            List<Future<String>> tasks = new ArrayList<>();

            sb.append(String.format("  📡 SEGMENTO: %s/%d\n", subnet, prefix));
            sb.append("  ⏱️  MAPEANDO NODOS... (Esto puede tardar unos segundos)\n\n");

            for (int i = 1; i < 255; i++) {
                final String host = baseIp + i;
                if (host.equals(primaryIp)) continue;

                tasks.add(executor.submit(() -> {
                    StringBuilder hostResult = new StringBuilder();
                    List<String> coreServices = new ArrayList<>();
                    List<String> otherServices = new ArrayList<>();
                    boolean hostUp = false;

                    for (int port : ALL_PORTS) {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(host, port), SCAN_TIMEOUT);
                            hostUp = true;

                            String svcName = SERVICE_MAP.getOrDefault(port, "Unknown");
                            // Categorización visual
                            if (port == 8080 || port == 8888 || port == 5050 || port == 9090) {
                                coreServices.add(svcName + ":" + port);
                            } else {
                                otherServices.add(svcName + ":" + port);
                            }
                        } catch (Exception ignored) {}
                    }

                    if (hostUp) {
                        String name = getHostname(host);
                        hostResult.append(String.format("  ■ HOST: %-15s | %s\n", host, name));

                        if (!coreServices.isEmpty()) {
                            hostResult.append("    ├─ [BITBRIDGE]: ").append(String.join(", ", coreServices)).append("\n");
                        }

                        if (!otherServices.isEmpty()) {
                            hostResult.append("    ├─ [SERVICES]:  ").append(String.join(", ", otherServices)).append("\n");
                        }

                        // Detección de OS Hint mejorada
                        if (otherServices.stream().anyMatch(s -> s.contains("22") || s.contains("5353"))) {
                            hostResult.append("    └─ [INFO]:      Posible nodo LINUX (Fedora/Ubuntu)\n");
                        } else if (otherServices.stream().anyMatch(s -> s.contains("3389") || s.contains("445"))) {
                            hostResult.append("    └─ [INFO]:      Posible nodo WINDOWS\n");
                        } else {
                            hostResult.append("    └─ [INFO]:      Dispositivo genérico detectado.\n");
                        }
                        return hostResult.toString() + "\n";
                    }
                    return null;
                }));
            }

            int found = 0;
            for (Future<String> f : tasks) {
                try {
                    String r = f.get(2500, TimeUnit.MILLISECONDS);
                    if (r != null) { sb.append(r); found++; }
                } catch (Exception ignored) {}
            }
            executor.shutdownNow();

            sb.append(" ══════════════════════════════════════════════════════════════\n");
            if (found == 0) {
                sb.append("  [!] RESULTADO: No se encontraron dispositivos activos.\n");
                sb.append("  💡 TIP: Verifica si el aislamiento de AP está activo en el router.");
            } else {
                sb.append(String.format("  [✓] ÉXITO: %d nodos identificados en la subred local.\n", found));
            }

        } catch (Exception e) {
            sb.append("  [!] ERROR CRÍTICO: ").append(e.getMessage());
        }
        return sb.toString();
    }

    /**
     * Resolución de nombre con fallback para evitar cuelgues de DNS
     */
    private static String getHostname(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String name = addr.getCanonicalHostName();
            return name.equals(ip) ? "Unnamed Device" : name;
        } catch (Exception e) {
            return "Unknown Host";
        }
    }
    // --- MÉTODOS DE CÁLCULO DE RED (REVISADOS) ---

    private static String detectGateway() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String cmd = os.contains("win") ? "netstat -rn" : "ip route";
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("default") || line.contains(" 0.0.0.0 ")) {
                    String[] parts = line.trim().split("\\s+");
                    return parts[2];
                }
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }

    private static long pingHost(String host) {
        if (host.equals("UNKNOWN")) return -1;
        try {
            long s = System.currentTimeMillis();
            if (InetAddress.getByName(host).isReachable(1000)) return System.currentTimeMillis() - s;
        } catch (Exception ignored) {}
        return -1;
    }

    private static String calculateSubnet(String ip, short prefix) {
        try {
            int mask = 0xffffffff << (32 - prefix);
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            int ipInt = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
            int subInt = ipInt & mask;
            return String.format("%d.%d.%d.%d", (subInt >> 24) & 0xff, (subInt >> 16) & 0xff, (subInt >> 8) & 0xff, subInt & 0xff);
        } catch (Exception e) { return "0.0.0.0"; }
    }
}
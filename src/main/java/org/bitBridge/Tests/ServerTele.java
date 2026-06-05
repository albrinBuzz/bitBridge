package org.bitBridge.Tests;

import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.SocketPurpose;
import org.bitBridge.shared.core.comunication.model.basic.HandshakeMessage;
import org.bitBridge.shared.core.comunication.model.basic.TelemetryPacket;
import org.bitBridge.shared.network.ProtocolService;
import org.bitBridge.view.swing.components.server.ServerDashboard;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

/**
 * Orquestador Único de Telemetría Remota para BitBridge.
 * Versión Expandida: Imprime e integra el dataset completo en CLI y GUI.
 */
public class ServerTele {

    // ==========================================
    // 1. INTERFAZ DE ESTRATEGIA VISUAL
    // ==========================================
    public interface TelemetryRenderer {
        void render(TelemetryPacket packet);
        void showMessage(String message);
    }

    // ==========================================
    // 2. IMPLEMENTACIÓN: MONITOR DE TERMINAL AVANZADO (CLI)
    // ==========================================
    public static class CliTelemetryRenderer implements TelemetryRenderer {
        @Override
        public void render(TelemetryPacket packet) {
            // Limpiar pantalla de consola de forma nativa (VT100)
            System.out.print("\033[H\033[2J");
            System.out.flush();

            System.out.println("\033[1;36m╔════════════════════════════════════════════════════════════════════════════════════════╗\033[0m");
            System.out.printf("\033[1;36m║\033[0m  \033[1;37mBITBRIDGE COMMAND CENTER CLI\033[0m | Engine: %-13s | OS: %-21s \033[1;36m║\033[0m\n", packet.coreVersion, packet.osVersion);
            System.out.printf("\033[1;36m║\033[0m  Uptime: %-22s | User: %-23s | Salud: %-11s \033[1;36m║\033[0m\n", packet.uptime, packet.username, packet.healthText);
            System.out.println("\033[1;36m╠════════════════════════════════════════════════════════════════════════════════════════╣\033[0m");

            // Sección Hardware
            System.out.printf("\033[1;36m║\033[0m  \033[1;33m[HARDWARE ASSETS]\033[0m                                                                      \033[1;36m║\033[0m\n");
            System.out.printf("\033[1;36m║\033[0m  CPU Cores: %-3d | Carga Hilos: %-6.1f%% | RAM Usada: %-3d%%                                    \033[1;36m║\033[0m\n", packet.cpuCores, packet.threadLoad, packet.ramPercent);
            System.out.printf("\033[1;36m║\033[0m  Heap Used: %-12s | Direct NIO: %-11s | Max VM Memory: %-16s \033[1;36m║\033[0m\n",
                    formatBytes(packet.heapUsed), formatBytes(packet.directMemoryUsed), formatBytes(packet.maxMemory));
            System.out.println("\033[1;36m╠════════════════════════════════════════════════════════════════════════════════════════╣\033[0m");

            // Sección Conectividad y Rendimiento
            System.out.printf("\033[1;36m║\033[0m  \033[1;32m[NETWORK TRAFFIC & LIFELINE]\033[0m                                                                   \033[1;36m║\033[0m\n");
            System.out.printf("\033[1;36m║\033[0m  Endpoint: %s:%-5d | Interfaz Activa: %-46s \033[1;36m║\033[0m\n", packet.currentPrimaryIp, packet.port, packet.activeInterfaceName);
            System.out.printf("\033[1;36m║\033[0m  Mensajes: %-13d | Transferido: %-12s | Tasa: %-8.2f KB/s %-15s \033[1;36m║\033[0m\n",
                    packet.totalMessages, formatBytes(packet.totalBytesTransferred), packet.currentKbs, packet.trafficBar);
            System.out.println("\033[1;36m╠════════════════════════════════════════════════════════════════════════════════════════╣\033[0m");

            // Sección Hilos en Ejecución (Top 3 resumido para consola)
            System.out.printf("\033[1;36m║\033[0m  \033[1;35m[JVM RUNTIME THREADS INSPECTOR]\033[0m (Total: %-4d | Workers: %-3d | Daemon: %-3d)           \033[1;36m║\033[0m\n",
                    packet.totalThreadsCount, packet.bitBridgeWorkers, packet.daemonThreadsCount);
            if (packet.threadDetails != null && !packet.threadDetails.isEmpty()) {
                int limit = Math.min(3, packet.threadDetails.size());
                for (int i = 0; i < limit; i++) {
                    var t = packet.threadDetails.get(i);
                    System.out.printf("\033[1;36m║\033[0m   ↳ ID: %-4d | %-35s | STATE: %-12s | %-6s \033[1;36m║\033[0m\n", t.id(), truncate(t.name(), 35), t.state(), t.type());
                }
            }
            System.out.println("\033[1;36m╠════════════════════════════════════════════════════════════════════════════════════════╣\033[0m");

            // Topología de red resumida
            System.out.printf("\033[1;36m║\033[0m  \033[1;36m[MAPA DE TOPOLOGÍA LOCAL EN RUTA]\033[0m                                                       \033[1;36m║\033[0m\n");
            if (packet.networkTopology != null) {
                int netLimit = Math.min(2, packet.networkTopology.size());
                for (int i = 0; i < netLimit; i++) {
                    var net = packet.networkTopology.get(i);
                    System.out.printf("\033[1;36m║\033[0m   • %-14s | MTU: %-5d | IPs asignadas: %-36d \033[1;36m║\033[0m\n", net.typeStr() + " " + net.name(), net.mtu(), net.addresses().size());
                }
            }
            System.out.println("\033[1;36m╠════════════════════════════════════════════════════════════════════════════════════════╣\033[0m");

            // Logs Recientes
            System.out.printf("\033[1;36m║\033[0m  \033[1;34m[LIVE TELEMETRY LOGS]\033[0m                                                                  \033[1;36m║\033[0m\n");
            if (packet.shortLogHistory != null) {
                int logLimit = Math.min(2, packet.shortLogHistory.size());
                for (int i = 0; i < logLimit; i++) {
                    System.out.printf("\033[1;36m║\033[0m   > %-78s \033[1;36m║\033[0m\n", truncate(packet.shortLogHistory.get(i), 78));
                }
            }
            System.out.println("\033[1;36m╚════════════════════════════════════════════════════════════════════════════════════════╝\033[0m");
        }

        @Override
        public void showMessage(String message) {
            System.out.println("\033[1;34m[SYS-NETWORK]\033[0m " + message);
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            return String.format("%.2f %sB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1) + "");
        }

        private String truncate(String str, int length) {
            if (str == null) return "";
            return str.length() <= length ? str : str.substring(0, length - 3) + "...";
        }
    }

    // ==========================================
    // 3. IMPLEMENTACIÓN: ADAPTADOR INTEGRAL DE DASHBOARD (GUI)
    // ==========================================
    public static class GuiTelemetryRenderer implements TelemetryRenderer {
        private ServerDashboard dashboard;

        public GuiTelemetryRenderer() {
            FlatOneDarkIJTheme.setup();
            SwingUtilities.invokeLater(() -> {
                // Instanciación directa del componente unificado sin dependencias locales de Server
                this.dashboard = new ServerDashboard();
                this.dashboard.setVisible(true);
            });
        }

        @Override
        public void render(TelemetryPacket packet) {
            if (dashboard != null) {
                // Envía el paquete masivo directamente a la lógica de pintado del Dashboard
                //dashboard.updateTelemetryData(packet);
            }
        }

        @Override
        public void showMessage(String message) {
            System.out.println("[GUI-NET] " + message);
        }
    }

    // ==========================================
    // 4. LÓGICA CENTRAL DE CONEXIÓN Y FLUJO (NIO)
    // ==========================================
    public static void main(String[] args) {
        boolean useCli = false;
        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg)) {
                useCli = true;
                break;
            }
        }

        TelemetryRenderer renderer = useCli ? new CliTelemetryRenderer() : new GuiTelemetryRenderer();

        String host = "127.0.0.1";
        int port = 8080;
        String sessionId = "OPERATOR-" + (System.currentTimeMillis() % 1000);

        renderer.showMessage("Abriendo Socket NIO hacia la central remota...");

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);

            channel.connect(new InetSocketAddress(host, port));

            if (channel.isConnected()) {
                renderer.showMessage("Canal establecido. Negociando Handshake Pasivo...");

                HandshakeMessage handshake = new HandshakeMessage(sessionId, SocketPurpose.PASSIVE_LISTENER, "");
                ProtocolService.writeNIO(channel, handshake);

                // Escucha reactiva indefinida del stream binario
                while (channel.isOpen()) {
                    Object incomingData = ProtocolService.readNIO(channel);

                    if (incomingData instanceof TelemetryPacket telemetry) {
                        renderer.render(telemetry);
                    } else if (incomingData != null) {
                        renderer.showMessage(incomingData.toString());
                    }
                }
            }
        } catch (Exception e) {
            Logger.logError("[CRÍTICO] Fallo catastrófico en red remota: " + e.getMessage());
            renderer.showMessage("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
package org.bitBridge.Tests;

import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.shared.CommunicationType;
import org.bitBridge.shared.Mensaje;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MixedStressTestRunner {
    public static void main(String[] args) {
        final int TOTAL_CLIENTS = 500; // Clientes totales
        final int MESSAGES_PER_CLIENT = 1000; // Cuántos mensajes envía cada uno
        final String SERVER_IP = "127.0.0.1";
        final int PORT = 8080;
        final File TEST_FILE = new File("/home/cris/ldr/el-senor-de-los-anillos.mp4");

        System.out.println("🔥 INICIANDO ATAQUE MIXTO: Archivos + Mensajes");
        var executor = Executors.newFixedThreadPool(500);
        try  {
            for (int i = 0; i < TOTAL_CLIENTS; i++) {
                int clientId = i;
                executor.submit(() -> {
                    try {
                        Client c = new Client();
                        c.setHostName("bot_receptor_" + clientId);
                        c.setConexion(SERVER_IP, PORT);

                        // --- FASE 1: SPAM DE MENSAJES (Saturar workerPool) ---
                        executor.submit(() -> {
                            for (int m = 0; m < MESSAGES_PER_CLIENT; m++) {
                                try {
                                    c.enviarComunicacion(new Mensaje("Spam #" + m + " de cliente " + clientId, CommunicationType.MESSAGE));
                                    Thread.sleep(50); // Simula escritura rápida de chat
                                } catch (Exception ignored) {}
                            }
                        });

                        // --- FASE 2: TRANSFERENCIA PESADA (Saturar fileTransferPool) ---
                        ClientInfo target = new ClientInfo(SERVER_IP, "bot_receptor_" + clientId, PORT);
                        c.sendFileToHost(target, TEST_FILE);

                    } catch (Exception e) {
                        System.err.println("❌ Fallo en cliente " + clientId + ": " + e.getMessage());
                    }
                });
                Thread.sleep(20);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
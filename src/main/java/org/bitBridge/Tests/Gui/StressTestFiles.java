package org.bitBridge.Tests.Gui;


import org.bitBridge.Client.FileTransferManager;
import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.shared.FileTransferState;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StressTestFiles {
    public static void main(String[] args) {
        // 1. Mock del controlador para que no intente abrir ventanas
        TransferenciaController mockController = new TransferenciaController() {
            @Override
            public void updateProgress(FileTransferState state, String id, int progress) {
                // Solo printeamos cada 50% para no saturar la consola
                if (progress % 50 == 0) System.out.println("ID: " + id + " -> " + progress + "%");
            }
        };

        // 2. Pool de hilos para lanzar transferencias en paralelo
        ExecutorService testerPool = Executors.newFixedThreadPool(50);
        String serverIp = "127.0.0.1";
        int port = 8080;

        // 3. Lanzar 1000 transferencias
        for (int i = 0; i < 1000; i++) {
            final int id = i;
            testerPool.submit(() -> {
                FileTransferManager manager = new FileTransferManager(mockController);
                // Creamos un archivo temporal para la prueba
                File dummyFile = createDummyFile("test_file_" + id + ".txt", 1024 * 5); // 5KB

                System.out.println("Lanzando transferencia: " + id);
                //manager.sendFile(dummyFile, "Destinatario_" + id, serverIp, port);
            });
        }
    }

    private static File createDummyFile(String name, long size) {
        try {
            File f = new File(name);
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            raf.setLength(size);
            raf.close();
            return f;
        } catch (IOException e) { return null; }
    }
}
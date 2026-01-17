package org.bitBridge.Client.services;

import org.bitBridge.Client.DirectoryTransferManager;
import org.bitBridge.Client.FileTransferManager;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class MessageDispatcher {
    private final Client client;
    private final ClientContext context;
    private SourceDataLine speakers;
    // Mapa que asocia la Clase del objeto con su manejador
    private final Map<Class<?>, ClientActionHandler<Object>> handlers = new HashMap<>();

    public MessageDispatcher(Client client, ClientContext context) {
        this.client = client;
        this.context = context;
        setupHandlers();
    }

    private void setupAudioPlayback() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
        speakers = AudioSystem.getSourceDataLine(format);
        speakers.open(format);
        speakers.start();
    }

    private void setupHandlers() {
        // Registro de manejadores por tipo de clase de objeto

        // 1. Manejo de lista de clientes
        register(ClientListMessage.class, (data, cl, ctx) -> {
            cl.notifyHostobserves(((ClientListMessage) data).getClientNicks());
        });

        // 2. Manejo de mensajes de chat
        register(Mensaje.class, (data, cl, ctx) -> {
            cl.handleIncomingMessage(((Mensaje) data).getContenido());
        });

        // 3. Manejo de transferencias (Handshake)
        register(FileHandshakeCommunication.class, (data, cl, ctx) -> {
            handleTransfer((FileHandshakeCommunication) data);
        });
// En el método setupHandlers() de tu MessageDispatcher.java
        register(AudioFrameMessage.class, (data, cl, ctx) -> {
            AudioFrameMessage msg = (AudioFrameMessage) data;
            var audioPlaybackService = new AudioPlaybackService();
            audioPlaybackService.play(msg.getAudioData());
            // El Dispatcher solo invoca la clase de servicio
            //cl.getAudioPlaybackService().play(msg.getAudioData());
        });

        register(ScreenCaptureMessage.class, (data, cl, ctx) -> {
            // T0: Inicio del proceso (en cuanto el Dispatcher recibe el objeto)
            long t0 = System.nanoTime();
            ScreenCaptureMessage msg = (ScreenCaptureMessage) data;

            ctx.executor().submit(() -> {
                try {
                    // T1: Tiempo de espera en la cola del Executor
                    long t1 = System.nanoTime();
                    byte[] imageData = msg.getImageData();
                    int sizeKB = imageData.length / 1024;

                    // Lógica de carpetas
                    java.nio.file.Path directory = java.nio.file.Paths.get("received_captures");
                    if (!java.nio.file.Files.exists(directory)) {
                        java.nio.file.Files.createDirectories(directory);
                    }

                    String timestamp = new java.text.SimpleDateFormat("HHmmss_SSS").format(new java.util.Date());
                    java.nio.file.Path targetPath = directory.resolve("cap_" + timestamp + ".jpg");

                    // T2: Inicio de la escritura a disco
                    long t2 = System.nanoTime();

                    java.nio.file.Files.write(targetPath, imageData,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.SYNC);

                    // T3: Fin de la escritura
                    long t3 = System.nanoTime();

                    // Cálculos (Convertimos nanosegundos a milisegundos)
                    double queueTime = (t1 - t0) / 1_000_000.0;
                    double writeTime = (t3 - t2) / 1_000_000.0;
                    double totalTime = (t3 - t0) / 1_000_000.0;

                    Logger.logInfo(String.format(
                            "[PERF] Foto: %d KB | Cola: %.2fms | Disco: %.2fms | Total: %.2fms",
                            sizeKB, queueTime, writeTime, totalTime
                    ));

                } catch (IOException e) {
                    Logger.logError("Error de telemetría: " + e.getMessage());
                }
            });
        });
    }

    // Helper para registrar con tipado seguro
    @SuppressWarnings("unchecked")
    private <T> void register(Class<T> clazz, ClientActionHandler<T> handler) {
        handlers.put(clazz, (ClientActionHandler<Object>) handler);
    }

    public void dispatch(Object incoming) {
        if (incoming == null) return;

        // Buscamos el handler basado en la clase del objeto recibido
        ClientActionHandler<Object> handler = handlers.get(incoming.getClass());

        if (handler != null) {
            handler.handle(incoming, client, context);
        } else {
             Logger.logWarn("No se encontró handler para el objeto: " + incoming.getClass().getSimpleName());
        }
    }

    private void handleTransfer(FileHandshakeCommunication handshake) {
        context.executor().submit(() -> {
            var info = handshake.getFileInfo();
            try {
                if (info.getType() == CommunicationType.FILE) {
                    new FileTransferManager(context.transferController())
                            .receiveFiles(context.serverAddress(), String.valueOf(context.serverPort()), handshake);
                } else {
                    new DirectoryTransferManager(context.transferController())
                            .reciveDirectory(context.serverAddress(), String.valueOf(context.serverPort()), handshake);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
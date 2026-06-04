package org.bitBridge.Client.handlers;

import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.ScreenCaptureMessage;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

@org.bitBridge.Client.handlers.ClientHandler(ScreenCaptureMessage.class)
public class ClientScreenCaptureHandler implements ClientActionHandler<ScreenCaptureMessage> {
    @Override
    public void handle(ScreenCaptureMessage msg, Client cl, ClientContext ctx) throws Exception {
        long t0 = System.nanoTime();

        ctx.executor().submit(() -> {
            try {
                long t1 = System.nanoTime();
                byte[] imageData = msg.getImageData();
                int sizeKB = imageData.length / 1024;

                Path directory = Paths.get("received_captures");
                if (!Files.exists(directory)) {
                    Files.createDirectories(directory);
                }

                String timestamp = new SimpleDateFormat("HHmmss_SSS").format(new Date());
                Path targetPath = directory.resolve("cap_" + timestamp + ".jpg");

                long t2 = System.nanoTime();
                Files.write(targetPath, imageData, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                long t3 = System.nanoTime();

                double queueTime = (t1 - t0) / 1_000_000.0;
                double writeTime = (t3 - t2) / 1_000_000.0;
                double totalTime = (t3 - t0) / 1_000_000.0;

                Logger.logInfo(String.format(
                        "[PERF] Foto: %d KB | Cola: %.2fms | Disco: %.2fms | Total: %.2fms",
                        sizeKB, queueTime, writeTime, totalTime
                ));
            } catch (IOException e) {
                Logger.logError("Error de telemetría de captura: " + e.getMessage());
            }
        });
    }
}
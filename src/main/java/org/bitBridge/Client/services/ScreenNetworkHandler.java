package org.bitBridge.Client.services;



import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.ScreenCaptureMessage;
import org.bitBridge.shared.Logger;
import java.awt.AWTException;
import java.io.IOException;

public class ScreenNetworkHandler {
    private final ClientContext context;
    private final ScreenCaptureService captureService;

    public ScreenNetworkHandler(ClientContext context) throws AWTException {
        this.context = context;
        // Instanciamos el servicio de lógica pura que creamos antes
        this.captureService = new ScreenCaptureService();
    }

    /**
     * Orquesta la captura local y la envía al servidor.
     * Se ejecuta en un hilo separado para no congelar la UI.
     */
    public void sendScreenSnapshot(String targetNick, float quality) {
        context.executor().submit(() -> {
            try {
                // 1. Obtener bytes de la lógica pura
                byte[] imageData = captureService.captureScreenAsBytes(quality);

                // 2. Empaquetar en tu objeto de comunicación
                ScreenCaptureMessage message = new ScreenCaptureMessage(
                        imageData,
                        context.client().getHostName(),
                        targetNick
                );

                context.client().enviarComunicacion(message);

                Logger.logInfo("Snapshot de pantalla enviado exitosamente a " + targetNick);

            } catch (IOException e) {
                Logger.logError("Error de red al enviar captura: " + e.getMessage());
            } catch (Exception e) {
                Logger.logError("Error inesperado en ScreenNetworkHandler: " + e.getMessage());
            }
        });
    }
}

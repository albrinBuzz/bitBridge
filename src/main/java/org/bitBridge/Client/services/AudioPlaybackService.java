package org.bitBridge.Client.services;



import org.bitBridge.shared.Logger;
import javax.sound.sampled.*;

public class AudioPlaybackService {
    private SourceDataLine speakers;
    private final AudioFormat format;

    public AudioPlaybackService() {
        // Formato estándar: 44.1kHz, 16 bits, Mono, Signed, BigEndian
        this.format = new AudioFormat(44100, 16, 1, true, true);
    }

    /**
     * Inicializa la línea de audio si no está abierta.
     */
    private void ensureAudioLineOpen() throws LineUnavailableException {
        if (speakers == null || !speakers.isOpen()) {
            speakers = AudioSystem.getSourceDataLine(format);
            speakers.open(format);
            speakers.start();
            Logger.logInfo("Hardware de audio inicializado correctamente.");
        }
    }

    /**
     * Reproduce los bytes recibidos en los altavoces.
     */
    public void play(byte[] audioData) {
        try {
            ensureAudioLineOpen();
            // Escribe los bytes en el buffer de la tarjeta de sonido
            speakers.write(audioData, 0, audioData.length);
        } catch (LineUnavailableException e) {
            Logger.logError("No se pudo acceder a los altavoces: " + e.getMessage());
        } catch (Exception e) {
            Logger.logError("Error inesperado en reproducción de audio: " + e.getMessage());
        }
    }

    /**
     * Libera los recursos de la tarjeta de sonido.
     */
    public void close() {
        if (speakers != null) {
            speakers.stop();
            speakers.close();
            Logger.logInfo("Servicio de audio cerrado.");
        }
    }
}
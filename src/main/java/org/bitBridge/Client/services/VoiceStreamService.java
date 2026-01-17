package org.bitBridge.Client.services;



import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.shared.AudioFrameMessage;
import javax.sound.sampled.*;

public class VoiceStreamService {
    private final ClientContext context;
    private TargetDataLine microphone;
    private boolean isRunning = false;

    public VoiceStreamService(ClientContext context) {
        this.context = context;
    }

    public void startRecording(String targetNick) {
        context.executor().submit(() -> {
            try {
                // Configuración estándar: 44.1kHz, 16 bits, Mono
                AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
                microphone = AudioSystem.getTargetDataLine(format);
                microphone.open(format);
                microphone.start();
                isRunning = true;

                byte[] buffer = new byte[4096]; // Paquetes de 4KB (aprox 46ms de audio)
                while (isRunning) {
                    int count = microphone.read(buffer, 0, buffer.length);
                    if (count > 0) {

                        //context.client().enviarObjetoConReset(new AudioFrameMessage(buffer));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void stop() {
        isRunning = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
    }
}

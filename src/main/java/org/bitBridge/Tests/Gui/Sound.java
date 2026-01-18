package org.bitBridge.Tests.Gui;

import java.awt.*;
import javax.sound.sampled.*;
public class Sound {
    public static void main(String[] args) {
        try {
            generateTone(880, 100, 0.1);


        /*Toolkit toolkit = Toolkit.getDefaultToolkit();

        // Generate the system beep sound
        System.out.println("Beep sound about to play...");
        toolkit.beep();
        System.out.println("Beep sound played.");

        // Optional: Keep the program running briefly so you can hear the sound
        // (sometimes the program exits before the sound finishes playing)

            Thread.sleep(1000); // Sleep for 1 second*/
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public static void generateTone(int hz, int msecs, double vol) throws LineUnavailableException {
        float sampleRate = 8000f;
        byte[] buf = new byte[1];
        // Definir el formato de audio (8-bit, Mono, Signed)
        AudioFormat af = new AudioFormat(sampleRate, 8, 1, true, false);
        SourceDataLine sdl = AudioSystem.getSourceDataLine(af);

        sdl.open(af);
        sdl.start();

        for (int i = 0; i < msecs * (sampleRate / 1000); i++) {
            double angle = i / (sampleRate / hz) * 2.0 * Math.PI;
            buf[0] = (byte) (Math.sin(angle) * 127.0 * vol);
            sdl.write(buf, 0, 1);
        }

        sdl.drain(); // Espera a que termine de sonar
        sdl.stop();
        sdl.close();
    }
}

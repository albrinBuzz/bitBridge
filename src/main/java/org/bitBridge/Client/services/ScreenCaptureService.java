package org.bitBridge.Client.services;

import org.bitBridge.Client.core.ClientContext;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class ScreenCaptureService {
    private final Robot robot;
    private final Toolkit toolkit;

    public ScreenCaptureService() throws AWTException {
        this.robot = new Robot();
        this.toolkit = Toolkit.getDefaultToolkit();
    }

    /**
     * Captura la pantalla principal y devuelve los bytes comprimidos.
     * @param quality Valor entre 0.0 y 1.0 (0.1 es mucha compresión, 0.9 es alta calidad)
     */
    public byte[] captureScreenAsBytes(float quality) throws IOException {
        // 1. Obtener el tamaño de la pantalla principal
        Rectangle screenRect = new Rectangle(toolkit.getScreenSize());

        // 2. Realizar la captura de imagen
        BufferedImage screenshot = robot.createScreenCapture(screenRect);

        // Opcional: Podrías dibujar el cursor manualmente aquí si fuera necesario
        // ya que Robot.createScreenCapture no captura el puntero del ratón.

        // 3. Comprimir y convertir a bytes
        return compressBufferedImage(screenshot, quality);
    }

    /**
     * Lógica avanzada de compresión JPG
     */
    private byte[] compressBufferedImage(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Buscamos un escritor de imágenes para JPG
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No se encontró escritor JPG");

        ImageWriter writer = writers.next();

        // Configuramos los parámetros de compresión
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality); // Aquí controlamos el peso del objeto
        }

        // Escribimos la imagen al stream de bytes
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    /**
     * Captura una región específica (útil para compartir solo una ventana)
     */
    public byte[] captureRegionAsBytes(Rectangle region, float quality) throws IOException {
        BufferedImage regionShot = robot.createScreenCapture(region);
        return compressBufferedImage(regionShot, quality);
    }
}
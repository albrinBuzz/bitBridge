package org.bitBridge.web;


import jakarta.faces.FacesException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import org.primefaces.event.CaptureEvent;

import javax.imageio.stream.FileImageOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Base64;

@Named
@ViewScoped
public class CameraBean implements Serializable {

    private String base64Photo;

    // getter + setter
    public String getBase64Photo() { return base64Photo; }
    public void setBase64Photo(String base64Photo) { this.base64Photo = base64Photo; }

    public void receivePhoto() {
        try {
            if (base64Photo == null || !base64Photo.contains("base64,")) {
                addMsg("No se recibió imagen.");
                return;
            }

            String base64 = base64Photo.split(",")[1];
            byte[] bytes = Base64.getDecoder().decode(base64);

            File file = new File("capturas/capture_" + System.currentTimeMillis() + ".png");
            file.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }

            addMsg("Foto recibida y guardada: " + file.getName());

        } catch (Exception e) {
            addMsg("Error al procesar la imagen: " + e.getMessage());
        }
    }

    private void addMsg(String msg) {
        FacesContext.getCurrentInstance()
                .addMessage(null, new FacesMessage(msg));
    }
    private String filename;

    private String getRandomImageName() {
        int i = (int) (Math.random() * 10000000);

        return String.valueOf(i);
    }

    public String getFilename() {
        return filename;
    }

    public void oncapture(CaptureEvent captureEvent) {
        filename = getRandomImageName();
        byte[] data = captureEvent.getData();

        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        String newFileName = externalContext.getRealPath("") + File.separator + "resources" + File.separator + "demo"
                + File.separator + "images" + File.separator + "photocam" + File.separator + filename + ".jpeg";

        FileImageOutputStream imageOutput;
        try {
            imageOutput = new FileImageOutputStream(new File(newFileName));
            imageOutput.write(data, 0, data.length);
            imageOutput.close();
        }
        catch (IOException e) {
            throw new FacesException("Error in writing captured image.", e);
        }
    }
}

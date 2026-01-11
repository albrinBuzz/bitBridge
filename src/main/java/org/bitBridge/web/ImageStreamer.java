package org.bitBridge.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseId;
import jakarta.inject.Named;

import org.bitBridge.shared.Logger;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import java.io.*;
import java.nio.file.Files;

@Named
@ApplicationScoped // Obligatorio para que la URL sea accesible por el navegador
public class ImageStreamer {

    public StreamedContent getImage() throws FileNotFoundException {
        FacesContext context = FacesContext.getCurrentInstance();

        if (context.getCurrentPhaseId() == PhaseId.RENDER_RESPONSE) {
            return new DefaultStreamedContent();
        }

        String path = context.getExternalContext().getRequestParameterMap().get("imageUrl");
        if (path == null || path.isEmpty()) return null;

        File file = new File(path);

        if (file.exists() && !file.isDirectory()) {
            ///Logger.logInfo("Es una imagen, creando StreamedContent...");

            InputStream stream = new FileInputStream(file);
            //Logger.logInfo("Stream creado para: " + file.getName());

            try {
                return DefaultStreamedContent.builder()
                        .contentType(Files.probeContentType(file.toPath()))
                        .stream(() -> stream)
                        .build();
            }  catch (IOException e) {
                Logger.logInfo("Error al leer el archivo: " + file.getName()+" "+e.getMessage());
            }
        }
        Logger.logInfo("No es una imagen válida o es un directorio: " + file.getName());
        return null;
    }
}

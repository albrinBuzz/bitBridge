package org.bitBridge.web;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

@Named
@ViewScoped
public class FolderUploadBean implements Serializable {

    private final String BASE_DIR = "./uploads/";

    public void uploadFolder() {

        try {
            FacesContext context = FacesContext.getCurrentInstance();
            HttpServletRequest request =
                    (HttpServletRequest) context.getExternalContext().getRequest();

            Collection<Part> parts = request.getParts();

            for (Part part : parts) {

                String relative = part.getSubmittedFileName();

                if (relative == null || relative.isEmpty())
                    continue;

                // Aquí viene la ruta completa relativa, ej: carpeta/subcarpeta/archivo.txt
                Path target = Paths.get(BASE_DIR + File.separator + relative);

                Files.createDirectories(target.getParent());

                try (InputStream input = part.getInputStream()) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            addMessage("Carpeta subida correctamente.");

        } catch (Exception e) {
            e.printStackTrace();
            addMessage("Error al subir carpeta: " + e.getMessage());
        }
    }

    private void addMessage(String msg) {
        FacesContext
                .getCurrentInstance()
                .addMessage(null, new FacesMessage(msg));
    }
}

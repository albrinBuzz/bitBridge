package org.bitBridge.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseId;
import jakarta.inject.Named;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@Named
@ApplicationScoped
public class MediaStreamer {
    private String textContent;

    public StreamedContent getImage() {
        return getStreamedContent("imageUrl");
    }

    public StreamedContent getVideo() {
        return getStreamedContent("videoUrl");
    }

    private StreamedContent getStreamedContent(String paramName) {
        FacesContext context = FacesContext.getCurrentInstance();
        if (context.getCurrentPhaseId() == PhaseId.RENDER_RESPONSE) {
            return new DefaultStreamedContent();
        }

        String path = context.getExternalContext().getRequestParameterMap().get(paramName);
        if (path == null) return null;

        File file = new File(path);
        try {
            // Detecta automáticamente si es image/png, image/jpeg, video/mp4, etc.
            String contentType = Files.probeContentType(file.toPath());

            return DefaultStreamedContent.builder()
                    .contentType(contentType)
                    .stream(() -> {
                        try {
                            return new FileInputStream(file);
                        } catch (FileNotFoundException e) {
                            return null;
                        }
                    })
                    .build();
        } catch (IOException e) {
            return null;
        }
    }


    public void previewTextFile(File file) {
        if (file == null || file.isDirectory()) return;

        try {
            // Leemos las primeras 100 líneas para la previsualización
            List<String> lines = Files.lines(file.toPath())
                    .limit(1000)
                    .collect(Collectors.toList());
            this.textContent = String.join("\n", lines);
        } catch (IOException e) {
            this.textContent = "Error al leer el archivo: " + e.getMessage();
        }
    }




    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }


    public boolean isTextFile(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase();

        // Lista extendida de extensiones comunes de archivos de texto
        return name.endsWith(".txt") ||
                name.endsWith(".log") ||
                name.endsWith(".java") ||
                name.endsWith(".conf") ||
                name.endsWith(".cpp") ||
                name.endsWith(".c") ||
                name.endsWith(".py") ||
                name.endsWith(".js") ||
                name.endsWith(".html") ||
                name.endsWith(".css") ||
                name.endsWith(".json") ||
                name.endsWith(".xml") ||
                name.endsWith(".md") ||
                name.endsWith(".csv") ||
                name.endsWith(".yaml") ||
                name.endsWith(".yml") ||
                name.endsWith(".ini") ||
                name.endsWith(".rst") ||
                name.endsWith(".php") ||
                name.endsWith(".sql") ||
                name.endsWith(".bat") ||
                name.endsWith(".sh") ||
                name.endsWith(".rtf") ||
                name.endsWith(".tex") ||
                name.endsWith(".ppt") ||
                name.endsWith(".epub") ||
                name.endsWith(".plist") ||
                name.endsWith(".dtd") ||
                name.endsWith(".diff") ||
                name.endsWith(".patch") ||
                name.endsWith(".bib") ||
                name.endsWith(".lic") ||
                name.endsWith(".env");
    }


}

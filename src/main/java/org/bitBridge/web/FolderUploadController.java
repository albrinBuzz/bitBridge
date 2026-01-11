package org.bitBridge.web;


import org.bitBridge.server.ConfiguracionServidor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;

@RestController
@RequestMapping("/api")
public class FolderUploadController {

    @PostMapping("/upload-folder")
    public ResponseEntity<String> uploadFolder(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String relativePath
    ) {
        // 1. Obtener la base de forma agnóstica
        ConfiguracionServidor config = ConfiguracionServidor.getInstancia();
        String baseDirConfig = config.obtener("cliente.directorio_descargas");

        // Convertimos a Path (esto ya maneja los separadores de Windows/Linux automáticamente)
        Path rootBase = Paths.get(baseDirConfig).toAbsolutePath().normalize();

        try {
            if (file.isEmpty() || relativePath == null || relativePath.isBlank()) {
                return ResponseEntity.badRequest().body("Archivo o ruta inválida");
            }

            // 2. Construcción SEGURA y agnóstica de la ruta de destino
            // Usamos normalize() para limpiar cualquier ".." malintencionado
            Path targetPath = rootBase.resolve(relativePath).normalize();

            // SEGURIDAD: Validamos que el destino final siga estando dentro de la carpeta base
            if (!targetPath.startsWith(rootBase)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Intento de acceso fuera del directorio permitido");
            }

            // 3. Setup Automático: Crear el árbol de directorios si no existe
            // targetPath incluye el nombre del archivo, por eso usamos getParent()
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }

            // 4. Guardado eficiente
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return ResponseEntity.ok("Archivo guardado en: " + targetPath.getFileName());

        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Error de permisos en el servidor");
        } catch (Exception e) {
            // Loguear el error internamente y devolver un mensaje genérico
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al procesar la subida: " + e.getMessage());
        }
    }
}
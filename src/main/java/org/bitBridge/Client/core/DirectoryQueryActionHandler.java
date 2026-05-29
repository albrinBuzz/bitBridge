package org.bitBridge.Client.core;

//package org.bitBridge.Client.services.handlers;

import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.DirectoryQuery;
import org.bitBridge.shared.core.comunication.DirectoryQueryResponse;
import org.bitBridge.shared.core.comunication.NodoDirectorio;

import java.nio.file.Files;
import java.nio.file.Path;


public class DirectoryQueryActionHandler implements ClientActionHandler<DirectoryQuery> {

    // Pre-calculamos el Path normalizado una sola vez para comparaciones rápidas
    private static final Path BASE_PATH = Path.of(ConfiguracionApp.getInstancia().getSharedDir())
            .toAbsolutePath()
            .normalize();

    @Override
    public void handle(DirectoryQuery query, Client client, ClientContext context) {
        // Delegamos al executor inmediatamente para no bloquear el hilo del Selector de NIO
        context.executor().submit(() -> {
            try {
                String target = query.getTargetPath();

                // 1. Resolución de ruta segura
                Path pathParaEscanear = (target == null || target.isBlank())
                        ? BASE_PATH
                        : Path.of(target).toAbsolutePath().normalize();

                // 2. Validación de Seguridad (Anti-Traversal)
                if (!pathParaEscanear.startsWith(BASE_PATH)) {
                    Logger.logWarn("Bloqueado intento de acceso fuera de base: " + pathParaEscanear);
                    return;
                }

                // 3. Verificación de existencia rápida
                if (!Files.exists(pathParaEscanear)) {
                    Logger.logWarn("Ruta no encontrada: " + pathParaEscanear);
                    return;
                }

                // 4. Carga de contenido (Escaneo bajo demanda)
                NodoDirectorio nodoRaiz = new NodoDirectorio(pathParaEscanear);
                nodoRaiz.cargarContenido();

                // 5. Respuesta asíncrona
                DirectoryQueryResponse response = new DirectoryQueryResponse(
                        nodoRaiz,
                        query.getSourceIp(),
                        query.getTargetIp(),
                        query.getToken()
                );

                client.enviarComunicacion(response);


            } catch (Exception e) {
                Logger.logError("Fallo en DirectoryQuery: " + e.getMessage());
            }
        });
    }
}
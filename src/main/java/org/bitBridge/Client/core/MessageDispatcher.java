package org.bitBridge.Client.core;

import io.github.classgraph.*;
import org.bitBridge.Client.handlers.AudioFrameHandler;
import org.bitBridge.Client.handlers.ClientHandler;
import org.bitBridge.shared.Logger;
import java.util.HashMap;
import java.util.Map;

public class MessageDispatcher {
    private final Client client;
    private final ClientContext context;
    private final Map<Class<?>, ClientActionHandler<Object>> handlers = new HashMap<>();

    public MessageDispatcher(Client client, ClientContext context) {
        this.client = client;
        this.context = context;
        autoDiscoverHandlers();
    }

    /**
     * Escanea dinámicamente las clases del cliente que tengan la anotación @ClientHandler
     */
    @SuppressWarnings("unchecked")
    private void autoDiscoverHandlers() {
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages("org.bitBridge.Client.handlers")
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()) {

            ClassInfoList handlerClasses = scanResult.getClassesWithAnnotation(ClientHandler.class.getName());

            for (ClassInfo classInfo : handlerClasses) {
                Class<?> clazz = classInfo.loadClass();
                if (ClientActionHandler.class.isAssignableFrom(clazz)) {
                    ClientHandler annotation = clazz.getAnnotation(ClientHandler.class);

                    // Instanciamos el handler de forma genérica
                    ClientActionHandler<?> handlerInstance = (ClientActionHandler<?>) clazz.getDeclaredConstructor().newInstance();

                    // Extraemos la clase clave de la anotación (ej: Mensaje.class)
                    Class<?> targetClass = annotation.value();

                    // Insertamos DIRECTO en el mapa eludiendo el método register de tipado estricto
                    handlers.put(targetClass, (ClientActionHandler<Object>) handlerInstance);
                }
            }
            Logger.logInfo("Core Cliente: Mapeo dinámico de acciones completado. Total handlers: " + handlers.size());
        } catch (Exception e) {
            Logger.logError("Error crítico inicializando los handlers del cliente: " + e.getMessage());
        }
    }

    // Registro manual heredado para mantener compatibilidad exacta (ej: DirectoryQueryActionHandler)
    private <T> void register(Class<T> clazz, ClientActionHandler<T> handler) {
        handlers.put(clazz, (ClientActionHandler<Object>) handler);
    }

    public void dispatch(Object incoming) throws Exception {
        if (incoming == null) return;

        ClientActionHandler<Object> handler = handlers.get(incoming.getClass());

        if (handler != null) {
            handler.handle(incoming, client, context);
        } else {
            Logger.logWarn("No se encontró handler para el objeto: " + incoming.getClass().getSimpleName());
        }
    }

    public void onDisconnect() {
        client.onDisconnect();
    }
}
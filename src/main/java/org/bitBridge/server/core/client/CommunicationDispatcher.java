/**
 * Copyright 2026 Cristobal Roman Zamora
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitBridge.server.core.client;

import io.github.classgraph.*;
import org.bitBridge.server.core.ServerContext;
import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.*;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CommunicationDispatcher {

    // Record atómico para evitar lecturas cruzadas desincronizadas en múltiples mapas
    private record HandlerMetadata(
            CommunicationHandler instance,
            ExecutionMode mode,
            ThreadCarrier carrier
    ) {}

    private final Map<String, HandlerMetadata> registry = new ConcurrentHashMap<>();
    private final ExecutorService fileTransferPool;
    private final ExecutorService virtualExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("V-Worker-", 1).factory()
    );

    public CommunicationDispatcher() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        this.fileTransferPool = new ThreadPoolExecutor(
                cpuCores * 2,
                cpuCores * 8,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5000),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "XFR-Worker-" + threadNumber.getAndIncrement());
                        t.setPriority(Thread.NORM_PRIORITY + 1);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        autoDiscoverHandlers();
    }

    @SuppressWarnings("unchecked")
    private void autoDiscoverHandlers() {
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages("org.bitBridge.server.handlers")
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()) {

            ClassInfoList handlerClasses = scanResult.getClassesWithAnnotation(ServerHandler.class.getName());

            for (ClassInfo classInfo : handlerClasses) {
                Class<?> clazz = classInfo.loadClass();
                if (CommunicationHandler.class.isAssignableFrom(clazz)) {
                    ServerHandler annotation = clazz.getAnnotation(ServerHandler.class);
                    CommunicationHandler handlerInstance = (CommunicationHandler) clazz.getDeclaredConstructor().newInstance();

                    Class<? extends Communication> messageClass = annotation.value();
                    String actionSuffix = annotation.action();

                    String uniqueKey = buildKey(messageClass, actionSuffix);

                    // Almacenamos el empaquetado completo de metadatos en un único mapa asíncrono
                    registry.put(uniqueKey, new HandlerMetadata(
                            handlerInstance,
                            annotation.mode(),
                            annotation.carrier()
                    ));

                    Logger.logInfo("Core Servidor: Handler registrado -> " + clazz.getSimpleName() + " para Mensaje [" + uniqueKey + "] en carril [" + annotation.carrier() + "]");
                }
            }
        } catch (Exception e) {
            Logger.logError("Error crítico inicializando el autodescubrimiento: " + e.getMessage());
        }
    }

    public void dispatch(BitBridgeClient sender, Communication message, ServerContext context) {
        if (message == null || sender == null) return;

        Class<? extends Communication> messageClass = message.getClass();

        // 🚨 POLIMORFISMO: El mensaje dictamina su propia acción, eliminando los bloques 'instanceof'
        String action = message.getSubAction();

        // Búsqueda por especificidad exacta
        String lookupKey = buildKey(messageClass, action);
        HandlerMetadata metadata = registry.get(lookupKey);

        // Fallback dinámico si se requería una acción genérica de la clase de red
        if (metadata == null && !action.isEmpty()) {
            metadata = registry.get(buildKey(messageClass, ""));
        }

        if (metadata == null) {
            Logger.logWarn("Servidor recibió un mensaje sin handler registrado para: " + messageClass.getName());
            return;
        }

        final HandlerMetadata finalMetadata = metadata;
        Runnable task = () -> {
            try {
                finalMetadata.instance().handle(new CommunicationExchange(sender, context), message);
            } catch (Exception e) {
                Logger.logError("Excepción procesando lógica de negocio: " + e.getMessage());
            }
        };

        // 🚨 ENRUTAMIENTO LIMPIO: Basado en las capacidades del Handler, no en strings del núcleo
        if (finalMetadata.mode() == ExecutionMode.SYNC) {
            task.run();
        } else {
            if (finalMetadata.carrier() == ThreadCarrier.PHYSICAL) {
                fileTransferPool.execute(task);
            } else {
                virtualExecutor.execute(task);
            }
        }
    }

    private String buildKey(Class<? extends Communication> messageClass, String action) {
        return messageClass.getName() + ":" + (action == null ? "" : action);
    }

    public void shutdown() {
        fileTransferPool.shutdown();
        virtualExecutor.shutdown();
        try {
            if (!fileTransferPool.awaitTermination(5, TimeUnit.SECONDS)) {
                fileTransferPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            fileTransferPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
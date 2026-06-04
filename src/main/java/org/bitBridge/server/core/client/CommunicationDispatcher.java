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
import org.bitBridge.shared.core.comunication.model.basic.FileDirectoryCommunication;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CommunicationDispatcher {

    private final Map<String, CommunicationHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, ExecutionMode> modes = new ConcurrentHashMap<>();

    // Pool físico acotado para I/O intensivo de disco (File Transfer)
    private final ExecutorService fileTransferPool;

    // El motor central para mensajes rápidos, asíncronos y paralelos basados en hilos virtuales
    // Modificado para asignar una nomenclatura clara a la factoría de hilos virtuales
    private final ExecutorService virtualExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("V-Worker-", 1).factory()
    );

    public CommunicationDispatcher() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        this.fileTransferPool = new ThreadPoolExecutor(
                cpuCores * 2,                // Hilos base permanentes
                cpuCores * 8,                // Máximo de hilos físicos bajo estrés pesado
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
                new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure natural si el pool explota
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

                    // 🚨 CORREGIDO: La clave DEBE basarse en el Mensaje que maneja, no en el nombre del Handler
                    Class<? extends Communication> messageClass = annotation.value();
                    String actionSuffix = annotation.action().isEmpty() ? "" : annotation.action();

                    String uniqueKey = messageClass.getName() + ":" + actionSuffix;

                    handlers.put(uniqueKey, handlerInstance);
                    modes.put(uniqueKey, annotation.mode());

                    // Log limpio para verificar el mapeo correcto en el arranque
                    Logger.logInfo("Core Servidor: Handler registrado -> " + clazz.getSimpleName() + " para Mensaje [" + uniqueKey + "]");
                }
            }
        } catch (Exception e) {
            Logger.logError("Error crítico inicializando el autodescubrimiento: " + e.getMessage());
        }
    }

    public void registerHandler(Class<? extends Communication> clazz, CommunicationHandler handler) {
        registerHandler(clazz, handler, ExecutionMode.ASYNC);
    }

    public void registerHandler(Class<? extends Communication> clazz, CommunicationHandler handler, ExecutionMode mode) {
        String baseKey = clazz.getName() + ":";
        handlers.put(baseKey, handler);
        modes.put(baseKey, mode);
    }

    public void dispatch(BitBridgeClient sender, Communication message, ServerContext context) {
        if (message == null || sender == null) return;

        Class<? extends Communication> messageClass = message.getClass();
        String action = "";

        // 1. Discriminación polimórfica para el canal de archivos compartidos
        if (message instanceof FileDirectoryCommunication fileDirComm) {
            action = fileDirComm.isDirectory() ? "DIRECTORY" : "FILE";
        }

        // 2. Intento de búsqueda de alta especificidad (Clase + Acción)
        String lookupKey = messageClass.getName() + ":" + action;
        CommunicationHandler handler = handlers.get(lookupKey);

        // 3. CORREGIDO: Fallback absoluto. Si no encuentra con acción, o si la acción vino vacía,
        // forzamos la búsqueda con la clave base "NombreClase:" que generó ClassGraph.
        if (handler == null) {
            String fallbackKey = messageClass.getName() + ":";
            handler = handlers.get(fallbackKey);
            if (handler != null) {
                lookupKey = fallbackKey; // Reajustamos la clave activa para el mapa de modos de ejecución
            }
        }

        // 4. Si después del bypass sigue en null, el handler realmente no existe
        if (handler == null) {
            Logger.logWarn("Servidor recibió un mensaje sin handler registrado para: " + messageClass.getName());
            return;
        }

        final CommunicationHandler finalHandler = handler;
        final String activeKey = lookupKey;
        ExecutionMode mode = modes.getOrDefault(activeKey, ExecutionMode.ASYNC);

        Runnable task = () -> {
            try {
                finalHandler.handle(new CommunicationExchange(sender, context), message);
            } catch (Exception e) {
                Logger.logError("Excepción procesando lógica de negocio en [" + activeKey + "]: " + e.getMessage());
            }
        };

        // Asignación inteligente de carriles de ejecución
        String className = messageClass.getSimpleName();
        if (className.equals("FilePullRequest") || action.equals("FILE") || action.equals("DIRECTORY")) {
            fileTransferPool.execute(task);
        } else {
            if (mode == ExecutionMode.SYNC) {
                task.run();
            } else {
                virtualExecutor.execute(task);
            }
        }
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
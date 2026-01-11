package org.bitBridge.Client.core;



import org.bitBridge.controller.TransferenciaController;

import java.util.concurrent.ExecutorService;

public record ClientContext(
        String serverAddress,
        int serverPort,
        TransferenciaController transferController,
        ExecutorService executor
) {}
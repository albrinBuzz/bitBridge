package org.bitBridge.server.core;


import org.bitBridge.server.client.ClientRegistry;
import org.bitBridge.server.client.NicknameService;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.server.transfer.TransferSessionManager;

/**
 * Provee acceso seguro a los servicios del servidor sin exponer el ciclo de vida del Server.
 */
public record ServerContext(
        ClientRegistry registry,
        NicknameService nicknameService,
        TransferSessionManager transferManager,
        ServerStats stats,
        Server server // Mantenemos una referencia limitada para métodos de orquestación
) {
    // Aquí puedes añadir métodos de conveniencia si son necesarios
}
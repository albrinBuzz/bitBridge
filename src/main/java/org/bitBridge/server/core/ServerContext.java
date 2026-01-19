package org.bitBridge.server.core;


import org.bitBridge.server.NetworkServer;
import org.bitBridge.server.core.client.ClientRegistry;
import org.bitBridge.server.core.client.CommunicationDispatcher;
import org.bitBridge.server.core.client.NicknameService;
import org.bitBridge.server.stats.ServerStats;
import org.bitBridge.server.transfer.TransferSessionManager;
import org.bitBridge.shared.network.ServerNetworkEngine;

/**
 * Provee acceso seguro a los servicios del servidor sin exponer el ciclo de vida del Server.
 */
public class ServerContext {
    private final ClientRegistry registry;
    private final NicknameService nicknameService;
    private final TransferSessionManager transferManager;
    private final ServerStats stats;
    private final Server server;
    private final CommunicationDispatcher dispatcher;
    private ServerNetworkEngine networkEngine; // <--- Cambiamos a Engine y permitimos setter

    public ServerContext(ClientRegistry registry, NicknameService nicknameService,
                         TransferSessionManager transferManager, ServerStats stats,
                         Server server, CommunicationDispatcher dispatcher) {
        this.registry = registry;
        this.nicknameService = nicknameService;
        this.transferManager = transferManager;
        this.stats = stats;
        this.server = server;
        this.dispatcher = dispatcher;
    }

    // Getters...
    public ServerNetworkEngine getNetworkEngine() { return networkEngine; }
    public void setNetworkEngine(ServerNetworkEngine engine) { this.networkEngine = engine; }

    // Mantén los otros getters del record original
    public ClientRegistry registry() { return registry; }
    public TransferSessionManager transferManager() { return transferManager; }



    public ClientRegistry getRegistry() {
        return registry;
    }

    public NicknameService getNicknameService() {
        return nicknameService;
    }

    public TransferSessionManager getTransferManager() {
        return transferManager;
    }

    public ServerStats getStats() {
        return stats;
    }

    public Server getServer() {
        return server;
    }

    public CommunicationDispatcher getDispatcher() {
        return dispatcher;
    }
}
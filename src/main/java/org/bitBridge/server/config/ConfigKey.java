package org.bitBridge.server.config;



public enum ConfigKey {
    // RED
    SERVER_NAME("servidor.nombre"),
    SERVER_PORT("servidor.puerto"),
    NET_MAX_CONN("net.max_conn"),
    NET_BACKLOG("net.backlog"),
    NET_KEEPALIVE("net.keepalive"),
    NET_NODELAY("net.nodelay"),
    NET_ENCRYPTION("net.encryption"),

    NET_SELECTOR_THREADS("net.threads.selector"),
    /** Cantidad de hilos para procesamiento de lógica y tareas pesadas (Workers) */
    NET_WORKER_THREADS("net.threads.worker"),

    // STORAGE
    DOWNLOAD_DIR("cliente.directorio_descargas"),
    SHARED_DIR("server.shared.dir"),
    TRANSFER_MAX_ACTIVE("transfer.max_active"),
    TRANSFER_AUTO_RESUME("transfer.auto_resume"),
    TRANSFER_OVERWRITE("transfer.overwrite"),

    // PERFORMANCE
    NIO_BUFFER_SIZE("nio.buffer_size"),
    NIO_ZERO_COPY("nio.zero_copy"),
    NIO_THREADS("nio.threads"),
    NIO_DIRECT_BUF("nio.direct_buffer"),
    NIO_POOL_CAPACITY("nio.pool_capacity"),

    POOL_MSG_SIZE("pool.msg.size"),
    POOL_MSG_CAP("pool.msg.capacity"),

    // --- POOL DE MEMORIA: DIRECTORIOS (64KB aprox) ---
    POOL_DIR_SIZE("pool.dir.size"),
    POOL_DIR_CAP("pool.dir.capacity"),

    // --- POOL DE MEMORIA: TRANSFERENCIAS (512KB+ aprox) ---
    POOL_TRANS_SIZE("pool.transfer.size"),
    POOL_TRANS_CAP("pool.transfer.capacity"),

    // SECURITY
    AUTH_REQUIRED("auth.required"),
    AUTH_KEY("auth.key");


    private final String key;

    ConfigKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
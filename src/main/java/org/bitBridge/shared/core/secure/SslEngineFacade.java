package org.bitBridge.shared.core.secure;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import org.bitBridge.shared.Logger;

/**
 * Copyright 2026 Cristobal Roman Zamora
 * Abstracción pura de SSLEngine adaptada para reactores NIO de BitBridge.
 */
public class SslEngineFacade {
    private final SSLEngine sslEngine;
    private final ByteBuffer netIn;
    private final ByteBuffer netOut;
    private final ByteBuffer appIn;

    public static SslEngineFacade crearParaServidor(SSLContext sslContext, SocketChannel channel) throws IOException {
        InetSocketAddress remote = (InetSocketAddress) channel.getRemoteAddress();
        if (remote == null) {
            throw new IOException("❌ [SERVER-TLS] No se puede cifrar un canal que no ha completado el acuerdo TCP.");
        }

        String hostLimpio = remote.getAddress().getHostAddress();
        int puerto = remote.getPort();

        SSLEngine engine = sslContext.createSSLEngine(hostLimpio, puerto);
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(false);

        return new SslEngineFacade(engine);
    }

    public static SslEngineFacade crearParaCliente(SSLContext sslContext, String host, int port) {
        SSLEngine engine = sslContext.createSSLEngine(host, port);
        engine.setUseClientMode(true);
        return new SslEngineFacade(engine);
    }

    private SslEngineFacade(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
        SSLSession session = this.sslEngine.getSession();

        // Buffers directos nativos para optimizar transferencias de bajo nivel
        this.netIn = ByteBuffer.allocateDirect(session.getPacketBufferSize());
        this.netOut = ByteBuffer.allocateDirect(session.getPacketBufferSize());
        this.appIn = ByteBuffer.allocateDirect(session.getApplicationBufferSize());
    }

    // --- ENCAPSULAMIENTO DE BUFFERS ---

    public SSLEngine getEngine() { return sslEngine; }
    public ByteBuffer getNetIn() { return netIn; }
    public ByteBuffer getNetOut() { return netOut; }
    public ByteBuffer getAppIn() { return appIn; }

    /**
     * Devuelve el estado actual del Handshake para que el Reactor/Socket sepa qué hacer
     * (escribir en red, leer de red, o esperar tareas).
     */
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return sslEngine.getHandshakeStatus();
    }

    // --- MÉTODOS DE FLUJO CRIPTOGRÁFICO RESILIENTES ---

    /**
     * Descifra datos desde netIn hacia appIn de manera segura.
     * Quien use este método debe verificar si el resultado es BUFFER_UNDERFLOW para meter más bytes a netIn.
     */
    public SSLEngineResult unwrap() throws SSLException {
        // appIn no se limpia a ciegas si quedaron datos remanentes parciales
        SSLEngineResult result = sslEngine.unwrap(netIn, appIn);
        return result;
    }

    /**
     * Cifra datos desde un buffer externo de la aplicación (appOut) hacia netOut.
     */
    public SSLEngineResult wrap(ByteBuffer appOut) throws SSLException {
        netOut.clear();
        SSLEngineResult result = sslEngine.wrap(appOut, netOut);
        netOut.flip(); // Listo para ser leído e inyectado al destino final (ej. socket)
        return result;
    }

    /**
     * CORRECCIÓN CRÍTICA: Ejecuta tareas delegadas de alto coste computacional.
     * Mapeamos las tareas a Virtual Threads, pero usamos join() para asegurar la
     * sincronía secuencial que exige la máquina de estados de TLS.
     */
    public void runDelegatedTasks() {
        Runnable task;
        while ((task = sslEngine.getDelegatedTask()) != null) {
            Runnable subTask = task;
            try {
                // 🔥 Se crea el Virtual Thread y bloqueamos hasta que acabe la validación criptográfica
                Thread vThread = Thread.ofVirtual().start(subTask);
                vThread.join();
            } catch (InterruptedException e) {
                Logger.logError("🚨 [TLS-FACADE] Tarea delegada interrumpida de forma anómala: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
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

package org.bitBridge.Client.core;

import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.Client.core.secure.SslClientContextFactory;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.secure.TofuTrustManager;
import org.bitBridge.shared.memory.DirectBufferPool;
import org.bitBridge.shared.network.ClientNetworkEngine;
import org.bitBridge.shared.network.ProtocolService;
import org.bitBridge.shared.network.tls.NioTlsHandler;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class NioClientEngine implements ClientNetworkEngine, Runnable {
    private SocketChannel socketChannel;
    private Selector selector;
    private MessageDispatcher dispatcher;
    private boolean running;
    private long connectionStartTime;
    private CountDownLatch connectionLatch;

    // 🔒 Componentes de control criptográfico
    private NioTlsHandler tlsHandler;
    private SSLContext sslContext;
    private final ConfiguracionApp config = ConfiguracionApp.getInstancia();

    // Buffers lógicos originales de BitBridge para parseo de paquetes limpios
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(8);
    private ByteBuffer payloadBuffer;
    private boolean readingHeader = true;

    private Client clientOrquestador;
    private boolean tlsHandshakeComplete;
    boolean tlsHabilitado;
    private String host;
    private int port;
    public NioClientEngine(MessageDispatcher dispatcher, Client clientOrquestador,String host,int port) {
        this.dispatcher = dispatcher;
        this.clientOrquestador = clientOrquestador;
        Logger.logInfo(host+":"+port);

        try {
            String tlsProp = config.obtener(ConfigKey.NET_TLS_ENABLED);

            tlsHabilitado = (tlsProp != null) && tlsProp.trim().equalsIgnoreCase("true");
            //tlsHabilitado =false;
            Logger.logInfo(ConfigKey.NET_TLS_ENABLED+"="+tlsProp);
            Logger.logInfo(String.valueOf(tlsHabilitado));
            if (tlsHabilitado) {
                this.sslContext = SslClientContextFactory.crearContextoCliente(host, port);
                Logger.logInfo("🔒 [CLIENTE] Contexto SSL/TLS 1.3 inicializado con éxito.");
            }else {
                Logger.logWarn("🔒 [CLIENTE] Contexto SSL/TLS 1.3 Desabilitado.");
            }
        } catch (Exception e) {
            Logger.logError("❌ Error preparando SSL en Cliente. Operará sin cifrado: " + e.getMessage());
        }
    }

    @Override
    public void connect(String host, int port) throws IOException {
        this.connectionLatch = new CountDownLatch(1);
        this.readingHeader = true;
        this.headerBuffer.clear();
        this.payloadBuffer = null;

        this.selector = Selector.open();
        this.socketChannel = SocketChannel.open();
        this.socketChannel.configureBlocking(false);

        this.connectionStartTime = System.currentTimeMillis();
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);

        this.socketChannel.connect(address);
        this.socketChannel.register(selector, SelectionKey.OP_CONNECT);
        this.running = true;

        new Thread(this, "NIO-Client-Worker").start();
    }

    @Override
    public void run() {
        while (running && socketChannel.isOpen()) {
            try {
                if (selector.select(1000) == 0) continue;

                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (!key.isValid()) continue;

                    if (key.isConnectable()) {
                        finishConnection(key);
                    } else if (key.isReadable()) {
                        readIncomingData(key);
                    }
                }
            } catch (IOException e) {
                Logger.logError("Error en loop NIO Cliente: " + e.getMessage());
                running = false;
                try { handleServerDisconnection(); } catch (IOException ignored) {}
            }
        }
    }

    private void finishConnection(SelectionKey key) throws IOException {
        if (socketChannel.finishConnect()) {
            long duration = System.currentTimeMillis() - connectionStartTime;
            Logger.logInfo(String.format("NIO: Conexión física establecida en %d ms.", duration));

            // Determinamos el modo evaluando la presencia del contexto criptográfico
            if (sslContext != null) {
                Logger.logInfo("🔒 [MODE] Inicializando canal seguro TLS 1.3...");
                this.tlsHandshakeComplete = false;

                // Instanciamos el handler reactivo
                this.tlsHandler = new NioTlsHandler(socketChannel, sslContext, key, true, tlsHabilitado);
                key.interestOps(SelectionKey.OP_READ);

                // Forzar el estado inicial (NEED_WRAP -> ClientHello)
                this.tlsHandler.handleReadEvent();
            } else {
                Logger.logWarn("🔓 [MODE] Operando en modo Texto Plano (Fallback).");
                this.tlsHandshakeComplete = true; // No hay handshake criptográfico que esperar

                if (this.connectionLatch != null) {
                    this.connectionLatch.countDown();
                }

                // 🎯 CRÍTICO: Registramos interés en lectura para recibir datos planos del Server
                key.interestOps(SelectionKey.OP_READ);

                if (clientOrquestador != null) {
                    clientOrquestador.onConnectionReady();
                }
            }
        }
    }


    public void readIncomingData(SelectionKey key) throws IOException {
        try {
            // 1. 🟢 CONTROL DE RUTA: MODO TEXTO PLANO
            if (sslContext == null) {
                SocketChannel channel = (SocketChannel) key.channel();

                // Asigna o reutiliza un buffer de lectura para texto plano
                ByteBuffer bufferPlano = ByteBuffer.allocate(4096);
                int bytesRead = channel.read(bufferPlano);

                if (bytesRead == -1) {
                    throw new IOException("El servidor remoto cerró la conexión física de forma limpia.");
                }

                if (bytesRead > 0) {
                    bufferPlano.flip();
                    Logger.logInfo(String.format("Llegan Datos (Modo Texto Plano) » Bytes crudos: %d", bytesRead));

                    // Despacha directo a tu pipeline de negocio (JSON/MessagePack parsing)
                    procesarBytesTextoPlano(bufferPlano);
                }
                return; // 🎯 Salimos del método, evitando tocar la infraestructura TLS
            }

            // 2. 🔒 CONTROL DE RUTA: MODO SEGURO (TLS 1.3)
            boolean wasHandshakeCompleteBefore = this.tlsHandshakeComplete;

            // Delegar lectura y descifrado al Handler reactivo
            ByteBuffer datosDescifrados = this.tlsHandler.handleReadEvent();

            // Actualizar las banderas locales consultando la verdad del motor TLS
            this.tlsHandshakeComplete = this.tlsHandler.isHandshakeComplete();

            Logger.logInfo("Llegan Datos");
            Logger.logInfo("El handshake terminó: " + this.tlsHandshakeComplete);
            Logger.logInfo("Encriptacion Lista: " + this.tlsHandshakeComplete);

            // Lógica de disparo en el flanco de subida (transición false -> true)
            if (!wasHandshakeCompleteBefore && this.tlsHandshakeComplete) {
                Logger.logInfo("🎉 ¡Detectado fin de Handshake en canal de lectura! Abriendo compuertas...");

                TofuTrustManager tofu = SslClientContextFactory.getActivoTofuManager();
                if (tofu != null && tofu.getUltimoCertificadoValidado() != null) {
                    X509Certificate certServidor = tofu.getUltimoCertificadoValidado();

                    Logger.logInfo("🔒 Conexión securizada con el nodo: " + certServidor.getSubjectX500Principal().getName());
                    Logger.logInfo("📅 Válido hasta: " + certServidor.getNotAfter());

                    // 1. Auditoría criptográfica de la sesión nativa
                    if (this.tlsHandler != null && this.tlsHandler.getSslEngine() != null) {
                        SSLSession session = this.tlsHandler.getSslEngine().getSession();
                        Logger.logInfo("🕵️ [CIPHER SUITE] " + session.getCipherSuite());
                    }

                    // 2. Inyección del certificado al orquestador principal
                    if (this.clientOrquestador != null) {
                       // this.clientOrquestador.setCertificadoServidorActual(certServidor);
                    }
                }

                // 3. Liberar hilos que esperaban para enviar datos por la red (ej: método send)
                if (this.connectionLatch != null) {
                    this.connectionLatch.countDown();
                }

                // 4. Disparar lógica de negocio de la aplicación
                if (this.clientOrquestador != null) {
                    this.clientOrquestador.onConnectionReady();
                }
            }

            // Procesamiento secundario del payload de negocio descifrado
            if (datosDescifrados != null && datosDescifrados.hasRemaining()) {
                procesarBytesTextoPlano(datosDescifrados);
            } else {
                Logger.logInfo("Datos nulos o de control en el app In (Handshake/KeepAlive Packet).");
            }

        } catch (IOException e) {
            Logger.logError("❌ Error crítico leyendo/descifrando datos en el motor del cliente: " + e.getMessage());
            closeConnection();
        }
    }

    private void closeConnection() throws IOException {
        stop();
    }

    private void procesarBytesTextoPlano(ByteBuffer appIn) throws IOException {
        while (appIn.hasRemaining()) {
            if (readingHeader) {
                while (headerBuffer.hasRemaining() && appIn.hasRemaining()) {
                    headerBuffer.put(appIn.get());
                }

                if (headerBuffer.hasRemaining()) {
                    return;
                }

                headerBuffer.flip();
                int jsonSize = headerBuffer.getInt();
                int typeSize = headerBuffer.getInt();
                int fullPacketSize = 8 + typeSize + jsonSize;

                if (typeSize <= 0 || typeSize > 1024 || jsonSize < 0 || fullPacketSize > 50 * 1024 * 1024) {
                    headerBuffer.clear();
                    throw new IOException("Protocol Desync en Cliente: Estructura de cabecera inválida.");
                }

                payloadBuffer = ByteBuffer.allocate(fullPacketSize);
                headerBuffer.rewind();
                payloadBuffer.put(headerBuffer);
                readingHeader = false;
            }

            if (!readingHeader) {
                while (payloadBuffer.hasRemaining() && appIn.hasRemaining()) {
                    payloadBuffer.put(appIn.get());
                }

                if (payloadBuffer.hasRemaining()) {
                    return;
                }

                payloadBuffer.flip();

                byte[] data = new byte[payloadBuffer.remaining()];
                payloadBuffer.get(data);

                try {
                    Communication comm = ProtocolService.fromBytes(data);
                    if (dispatcher != null) {
                        dispatcher.dispatch(comm);
                    }
                } catch (Exception e) {
                    Logger.logError("Error despachando payload desde el cliente: " + e.getMessage());
                } finally {
                    headerBuffer.clear();
                    payloadBuffer = null;
                    readingHeader = true;
                }
            }
        }
    }

    @Override
    public void send(Communication payload) throws IOException {
        try {
            if (connectionLatch == null) throw new IOException("No se ha iniciado una conexión.");
            if (!connectionLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IOException("Timeout: El canal seguro TLS no se pudo consolidar.");
            }

            // 1. Obtener el buffer del servicio
            ByteBuffer appOut = ProtocolService.toNioBuffer(payload, 50);

            if (sslContext != null && isEncryptedAndReady()) {
                // 2. EXTRAER BYTES DE FORMA SEGURA
                // Creamos un array del tamaño exacto del contenido remanente
                byte[] data = new byte[appOut.remaining()];
                appOut.get(data); // Copia el contenido del buffer al array

                // 3. Enviar al handler TLS
                tlsHandler.sendSecureMessage(data);

                // 4. Limpiar si el buffer era del pool
                if (appOut.isDirect()) {
                    DirectBufferPool.release(appOut);
                }
            } else {
                // Modo texto plano
                while (appOut.hasRemaining()) {
                    socketChannel.write(appOut);
                }
                if (appOut.isDirect()) {
                    DirectBufferPool.release(appOut);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Envío interrumpido.");
        }
    }

    public boolean isEncryptedAndReady() {
        return sslContext == null || (this.tlsHandler != null && this.tlsHandler.isHandshakeComplete());
    }

    @Override public String getStatus() { return isEncryptedAndReady() ? "SECURE" : "CONNECTING"; }
    @Override public String getHostName() { return socketChannel != null ? socketChannel.socket().getInetAddress().getHostName() : "UNKNOWN"; }

    @Override
    public void disconnect() throws IOException { stop(); }

    private void handleServerDisconnection() throws IOException {
        Logger.logWarn("[CLIENTE] El servidor ha cerrado la conexión de forma remota.");
        stop();
        if (dispatcher != null) dispatcher.onDisconnect();
    }

    @Override
    public void stop() throws IOException {
        this.running = false;
        if (selector != null && selector.isOpen()) selector.wakeup();
        if (socketChannel != null) socketChannel.close();
        if (selector != null) selector.close();
        Logger.logInfo("NioClientEngine TLS detenido correctamente.");
    }

    @Override
    public boolean isActive() {
        return socketChannel != null && socketChannel.isOpen() && socketChannel.isConnected() && isEncryptedAndReady();
    }

    @Override
    public void setHost(String host, int port) {

        this.host=host;
        this.port=port;
    }
}
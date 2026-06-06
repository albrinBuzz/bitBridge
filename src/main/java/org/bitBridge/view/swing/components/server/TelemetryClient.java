package org.bitBridge.view.swing.components.server;

import org.bitBridge.shared.core.comunication.SocketPurpose;
import org.bitBridge.shared.core.comunication.model.basic.HandshakeMessage;
import org.bitBridge.shared.core.comunication.model.basic.telemetry.TelemetryHandshakePacket;
import org.bitBridge.shared.core.comunication.model.basic.telemetry.TelemetryStreamPacket;
import org.bitBridge.shared.network.ProtocolService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelemetryClient {

    // Interfaz para notificar eventos a la interfaz o controladores sin acoplarlos
    public interface TelemetryListener {
        void onConnected();
        void onHandshakeReceived(TelemetryHandshakePacket packet);
        void onStreamReceived(TelemetryStreamPacket packet);
        void onError(String message, Throwable cause);
        void onDisconnected();
    }

    private SocketChannel networkChannel;
    private Thread networkWorker;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final TelemetryListener listener;

    public TelemetryClient(TelemetryListener listener) {
        if (listener == null) throw new IllegalArgumentException("Listener cannot be null");
        this.listener = listener;
    }

    public void connect(String host, int port) {
        if (isRunning.getAndSet(true)) return; // Evita condiciones de carrera al conectar

        networkWorker = new Thread(() -> {
            try {
                networkChannel = SocketChannel.open();
                networkChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                networkChannel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
                networkChannel.configureBlocking(true);

                // OPTIMIZACIÓN: Timeout de conexión de 5 segundos para evitar bloqueos infinitos
                networkChannel.socket().connect(new InetSocketAddress(host, port), 5000);

                if (networkChannel.isConnected()) {
                    listener.onConnected();

                    HandshakeMessage handshake = new HandshakeMessage("OPERATOR-REMOTE", SocketPurpose.PASSIVE_LISTENER, "");
                    ProtocolService.writeNIO(networkChannel, handshake);

                    executeEventLoop();
                }
            } catch (Exception e) {
                listener.onError("Fallo al establecer conexión con el HUB remoto.", e);
            } finally {
                disconnect();
            }
        }, "BitBridge-TelemetryClient-Worker");

        networkWorker.setDaemon(true);
        networkWorker.start();
    }

    private void executeEventLoop() {
        while (isRunning.get() && networkChannel.isOpen()) {
            try {
                Object incoming = ProtocolService.readNIO(networkChannel);
                if (incoming == null) continue;

                // Enrutamiento polimórfico limpio
                if (incoming instanceof TelemetryHandshakePacket handshake) {
                    listener.onHandshakeReceived(handshake);
                } else if (incoming instanceof TelemetryStreamPacket stream) {
                    listener.onStreamReceived(stream);
                }
            } catch (ClosedChannelException e) {
                // Desconexión normal/esperada del canal
                break;
            } catch (IOException e) {
                // Error de lectura aislado (ej: paquete corrupto), notificamos pero evaluamos si continuar
                listener.onError("Error de lectura de datos en el socket.", e);
                break;
            } catch (Exception e) {
                listener.onError("Error inesperado procesando paquete de telemetría.", e);
            }
        }
    }

    public void disconnect() {
        if (!isRunning.getAndSet(false)) return;

        try {
            if (networkChannel != null && networkChannel.isOpen()) {
                networkChannel.close();
            }
        } catch (IOException ignored) {
        } finally {
            listener.onDisconnected();
        }
    }

    public boolean isConnected() {
        return isRunning.get() && networkChannel != null && networkChannel.isConnected();
    }
}
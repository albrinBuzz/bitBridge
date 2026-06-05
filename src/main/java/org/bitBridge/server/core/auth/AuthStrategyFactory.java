package org.bitBridge.server.core.auth;



import org.bitBridge.shared.core.comunication.SocketPurpose;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;

public class AuthStrategyFactory {

    public static AuthStrategy getStrategy(SocketPurpose purpose) {
        return switch (purpose) {
            case CHAT_COMMAND -> (handler, handshake, context) -> {
                handler.nick = context.getServer().getUniqueNick(handshake.getIdentity());
                context.getServer().registerClient(handler, 8080);
                handler.sendComunicacion(new Mensaje("Conectado como: " + handler.nick));
                context.getServer().broadcastMessage("[ " + handler.nick + "] Se ha unido al sistema.", handler);
            };

            case FILE_TRANSFER -> (handler, handshake, context) -> {
                handler.nick = handshake.getIdentity();
                context.getTransferManager().registerReceptor(handler.nick, handler);
                Logger.logInfo("[AUTH] Canal de datos acoplado para archivos: " + handler.nick);
            };

            case AUDIO_STREAMING -> (handler, handshake, context) -> {
                handler.nick = handshake.getIdentity();
                // Aquí delegas a tu futuro gestor de llamadas VoIP
                // context.getAudioManager().registerAudioNode(handler.nick, handler);
                Logger.logInfo("[AUTH] Canal de Audio dedicado enlazado: " + handler.nick);
            };

            case PASSIVE_LISTENER -> (handler, handshake, context) -> {
                handler.nick = handshake.getIdentity() + "-listener";
                // No se une al chat general, solo se guarda en una lista de observadores pasivos
                // context.getServer().registerPassiveObserver(handler);
                Logger.logInfo("[AUTH] Cliente pasivo conectado para monitoreo: " + handler.nick);
            };
        };
    }
}
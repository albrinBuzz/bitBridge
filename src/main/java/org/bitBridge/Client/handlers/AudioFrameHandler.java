package org.bitBridge.Client.handlers;

import org.bitBridge.Client.core.Client;
import org.bitBridge.Client.core.ClientActionHandler;
import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.Client.services.AudioPlaybackService;
import org.bitBridge.shared.core.comunication.model.basic.AudioFrameMessage;

@ClientHandler(AudioFrameMessage.class)
public class AudioFrameHandler implements ClientActionHandler<AudioFrameMessage> {
    private final AudioPlaybackService audioPlaybackService = new AudioPlaybackService();

    @Override
    public void handle(AudioFrameMessage data, Client cl, ClientContext ctx) throws Exception {
        audioPlaybackService.play(data.getAudioData());
    }

    /*public static interface ClientActionHandler<T> {
        void handle(T data, Client client, ClientContext context) throws Exception;
    }*/
}
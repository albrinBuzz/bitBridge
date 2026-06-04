package org.bitBridge.Client.handlers;

import org.bitBridge.Client.core.ClientContext;
import org.bitBridge.Client.services.AudioPlaybackService;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.model.basic.AudioFrameMessage;

@MessageMapping(AudioFrameMessage.class)
public class AudioPlaybackHandler implements MessageHandler<AudioFrameMessage, ClientContext> {
    private final AudioPlaybackService audioPlaybackService = new AudioPlaybackService();

    @Override
    public void handle(AudioFrameMessage msg, ClientContext context) throws Exception {
        audioPlaybackService.play(msg.getAudioData());
    }
}
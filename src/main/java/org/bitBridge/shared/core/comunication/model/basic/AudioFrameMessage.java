package org.bitBridge.shared.core.comunication.model.basic;

import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

@BufferPoolMapping(DirectBufferPool.BufferType.TRANSFER) // Datos de streaming multimedia pesados
public class AudioFrameMessage extends Communication {
    private final byte[] audioData;

    public AudioFrameMessage(byte[] audioData) {
        this.audioData = audioData;
    }
    public byte[] getAudioData() { return audioData; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }
}
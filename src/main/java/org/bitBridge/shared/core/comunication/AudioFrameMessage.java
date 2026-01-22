package org.bitBridge.shared;

public class AudioFrameMessage extends Communication {
    private final byte[] audioData;

    public AudioFrameMessage(byte[] audioData) {
        super(CommunicationType.AUDIO_STREAM);
        this.audioData = audioData;
    }

    public byte[] getAudioData() { return audioData; }
}
package org.bitBridge.Client.managers;

public interface TransferManager {

    void pause();
    void resume();

    void stop();

    void cancel();
}

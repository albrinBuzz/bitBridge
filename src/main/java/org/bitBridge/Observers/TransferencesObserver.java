package org.bitBridge.Observers;


import org.bitBridge.Client.TransferManager;
import org.bitBridge.models.TransferProgress;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.core.comunication.FileHandshakeAction;
import org.bitBridge.shared.core.comunication.FileHandshakeCommunication;
import org.bitBridge.shared.FileTransferState;

public interface TransferencesObserver{
    void addTransference(String mode, Transferencia transferencia, TransferManager transferManager);
    void updateTransference(FileTransferState mode, String id, int progress);
    void endTransference(String mode, String addr);
    void notifyException(String message);
    void updateTransferenceFull(TransferProgress progress);
    boolean notifyTranference(FileHandshakeCommunication handshakeCommunication);
    void notifyTranference(FileHandshakeAction action);
}

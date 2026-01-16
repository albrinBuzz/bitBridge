package org.bitBridge.Observers;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.shared.ServerStatusConnection;

import java.util.List;

// Este es un puerto. No importa si quien lo implementa es Swing o FX.
public interface NetObserver {
    void onMessageReceived(String message);
    void onStatusChanged(ServerStatusConnection status);
    void onHostListUpdated(List<ClientInfo> hosts);

}
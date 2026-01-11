package org.bitBridge.Observers;



import org.bitBridge.shared.ServerStatusConnection;

import java.util.List;

public interface Observer {
    void updateServerConnection (ServerStatusConnection statusConnection);
    void updateClientsList(List<String> clients);
    void updateMessaje(String message);

}

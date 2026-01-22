package org.bitBridge.Observers;



import org.bitBridge.shared.core.comunication.ServerStatusConnection;

import java.util.List;

public interface Observer {
    void updateServerConnection (ServerStatusConnection statusConnection);
    void updateClientsList(List<String> clients);
    void updateMessaje(String message);

}

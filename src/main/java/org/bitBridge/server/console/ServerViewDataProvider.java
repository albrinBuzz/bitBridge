package org.bitBridge.server.console;


import org.bitBridge.Client.ClientInfo;

import java.util.Collection;

public interface ServerViewDataProvider {

    int getPort();
    String getStartTime();
    long getUptimeMillis();
    long getBytesSent();
    Collection<ClientInfo> getClients();
}

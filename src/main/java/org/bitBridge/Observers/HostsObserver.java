package org.bitBridge.Observers;


import org.bitBridge.Client.ClientInfo;

import java.util.List;

public interface HostsObserver {

    void updateAllHosts(List<ClientInfo>hostList);
}

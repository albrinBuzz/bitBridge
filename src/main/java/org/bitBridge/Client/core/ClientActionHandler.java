package org.bitBridge.Client.core;

import org.bitBridge.shared.Communication;

public interface ClientActionHandler<T> {
    void handle(T data, Client client, ClientContext context);
}
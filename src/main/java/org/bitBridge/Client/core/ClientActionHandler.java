package org.bitBridge.Client.core;

import java.io.IOException;

public interface ClientActionHandler<T> {
    void handle(T data, Client client, ClientContext context) throws Exception;
}
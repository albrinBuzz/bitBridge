package org.bitBridge.Client.core;

public interface ClientActionHandler<T> {
    void handle(T data, Client client, ClientContext context);
}
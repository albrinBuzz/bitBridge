package org.bitBridge.server;

import java.io.IOException;

public interface NetworkServer {
    void start() throws IOException;

    void start(int port) throws Exception;
    void stop();
    void broadcast(Object message);
    boolean isRunning();
    //void setEventsListener(ServerEvent listener);
}

package org.bitBridge.server.console;


public class ConsoleMonitor implements Runnable {

    private final ServerViewDataProvider provider;

    public ConsoleMonitor(ServerViewDataProvider provider) {
        this.provider = provider;
    }

    @Override
    public void run() {
        try {
            loop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loop() throws Exception {
        while (true) {
            render();
            Thread.sleep(100);
        }
    }

    private void render() {
        /*clear();
        printHeader();
        printStats();
        printClients();*/
    }
}

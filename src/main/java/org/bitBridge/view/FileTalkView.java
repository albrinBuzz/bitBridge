package org.bitBridge.view;

public interface FileTalkView {
    void updateConnectionStatus(String text, boolean isConnected);
    void updateServerStatus(String text, boolean isRunning);
    void showErrorMessage(String title, String message);
    void openWindow(String windowType); // "CONFIG", "TRANSFERS", etc.
}
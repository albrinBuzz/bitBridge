package org.bitBridge.view.core;

import org.bitBridge.shared.LogLevel;
import org.bitBridge.view.swing.StatusBarPanel;

public interface IMainView {
    void updateServerUI(ServerState state, String errorMessage);
    void showAlert(String title, String content);
    void updateTheme(String themeName);
    void updateConnectionUI(ConnectionState state, String detail);
    void addLog(String message, LogLevel type);
}
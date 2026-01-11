package org.bitBridge.view.core;

public interface IMainView {
    void updateServerUI(ServerState state, String errorMessage);
    void showAlert(String title, String content);
    void updateTheme(String themeName);
    void updateConnectionUI(ConnectionState state, String detail);




}
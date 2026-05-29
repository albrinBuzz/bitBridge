package org.bitBridge.models;


import org.bitBridge.shared.LogLevel;

/**
 * DTO para transportar información de eventos sin depender de la UI.
 */
public record LogEntry(String message, LogLevel level) {
    public static LogEntry info(String m) { return new LogEntry(m, LogLevel.INFO); }
    public static LogEntry success(String m) { return new LogEntry(m, LogLevel.SUCCESS); }
    public static LogEntry error(String m) { return new LogEntry(m, LogLevel.ERROR); }
}
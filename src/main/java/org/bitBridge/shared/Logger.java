package org.bitBridge.shared;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final String LOG_FILE = "bitBridge.log";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static boolean enableColors = true;

    // --- COLORES ANSI INTENSOS Y NEGRITAS ---
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    private static final String TIME_COLOR = "\u001B[90m";     // Gris oscuro
    private static final String CLASS_COLOR = "\u001B[38;5;39m"; // Azul brillante (Steel Blue)
    private static final String METHOD_COLOR = "\u001B[38;5;76m"; // Verde lima
    private static final String LINE_COLOR = "\u001B[38;5;214m";  // Naranja suave
    private static final String MSG_COLOR = "\u001B[97m";      // Blanco intenso

    public enum LogLevel {
        // [Fondo;Texto m
        DEBUG("\u001B[1;34m", "[DEBUG]"), // Azul Negrita
        INFO("\u001B[1;32m",  "[INFO ]"), // Verde Negrita
        WARN("\u001B[1;33m",  "[WARN ]"), // Amarillo Negrita
        ERROR("\u001B[1;31m", "[ERROR]"); // Rojo Negrita

        final String color;
        final String label;
        LogLevel(String color, String label) {
            this.color = color;
            this.label = label;
        }
    }

    //private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    // Formato robusto: 2026-06-06 21:50:34.411
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static synchronized void log(LogLevel level, String message) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
        String time = dtf.format(LocalDateTime.now());

        // CAMBIO: Ahora obtenemos el nombre completo del paquete + clase
        String fullClassName = ste.getClassName();

        System.out.println(formatForConsole(level, time, fullClassName, ste, message));
        saveToFile(formatForFile(level, time, fullClassName, ste, message));
    }

    private static String formatForConsole(LogLevel lvl, String time, String fullCls, StackTraceElement ste, String msg) {
        if (!enableColors) return formatForFile(lvl, time, fullCls, ste, msg);

        // Separamos el paquete de la clase para darles colores distintos
        String packageName = "";
        String className = fullCls;
        if (fullCls.contains(".")) {
            packageName = fullCls.substring(0, fullCls.lastIndexOf('.') + 1);
            className = fullCls.substring(fullCls.lastIndexOf('.') + 1);
        }

        // Estructura: TIME [LEVEL] package.Class::method(line) » message
        return String.format("%s%s%s %s%s%s %s%s%s%s%s%s::%s%s%s(%s%d%s) %s %s%s%s",
                TIME_COLOR, time, RESET,             // Tiempo
                lvl.color, lvl.label, RESET,         // Nivel [INFO]
                TIME_COLOR, packageName, RESET,      // Paquete (en gris para no saturar)
                BOLD + CLASS_COLOR, className, RESET, // Clase (en Azul brillante)
                METHOD_COLOR, ste.getMethodName(), RESET, // Método
                LINE_COLOR, ste.getLineNumber(), RESET,   // Línea
                BOLD + "»",                          // Separador
                MSG_COLOR, msg, RESET);              // Mensaje
    }

    private static String formatForFile(LogLevel lvl, String time, String fullCls, StackTraceElement ste, String msg) {
        return String.format("[%s] %s %s::%s(L:%d) - %s",
                time, lvl.label, fullCls, ste.getMethodName(), ste.getLineNumber(), msg);
    }

    private static void saveToFile(String fullLog) {
        File file = new File(LOG_FILE);
        if (file.exists() && file.length() > MAX_FILE_SIZE) rotateLogs(file);
        try (FileWriter fw = new FileWriter(LOG_FILE, true); PrintWriter pw = new PrintWriter(fw)) {
            pw.println(fullLog);
        } catch (IOException e) {
            System.err.println("Error al escribir log: " + e.getMessage());
        }
    }

    private static void rotateLogs(File currentFile) {
        File backup = new File(LOG_FILE + ".bak");
        if (backup.exists()) backup.delete();
        currentFile.renameTo(backup);
    }

    public static void logInfo(String m) { log(LogLevel.INFO, m); }
    public static void logError(String m) { log(LogLevel.ERROR, m); }
    public static void logWarn(String m) { log(LogLevel.WARN, m); }
    public static void logDebug(String m) { log(LogLevel.DEBUG, m); }
}
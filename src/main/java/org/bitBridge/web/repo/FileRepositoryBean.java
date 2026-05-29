package org.bitBridge.web.repo;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.bitBridge.models.LogChat;
import org.bitBridge.models.LogEntry;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@Named
@ApplicationScoped
public class FileRepositoryBean {

    // Usamos una lista sincronizada porque muchos usuarios escribirán al mismo tiempo
    private final List<LogChat> globalLogs= Collections.synchronizedList(new LinkedList<>());;

    @PostConstruct
    public void init() {

        // Mock inicial compartido
        globalLogs.add(new LogChat("System", "Welcome_to_BitBridge.txt", "1 KB", "fa-info-circle", "RECEIVED"));
        globalLogs.add(new LogChat("Fedora-Node_01", "dump_kiosko_v2.sql", "842 KB • SQL DATA", "fa-database", "SENT"));
        globalLogs.add(new LogChat("Cristobal", "NioEngine.java", "15.4 KB • JAVA SOURCE", "fa-file-code", "SENT"));
        globalLogs.add(new LogChat("Cristobal", "NioEngine.java", "15.4 KB • JAVA SOURCE", "fa-file-code", "SENT"));
        globalLogs.add(new LogChat("Cristobal", "NioEngine.java", "15.4 KB • JAVA SOURCE", "fa-file-code", "SENT"));
        globalLogs.add(new LogChat("Cristobal", "NioEngine.java", "15.4 KB • JAVA SOURCE", "fa-file-code", "SENT"));
        globalLogs.add(new LogChat("Cristobal", "NioEngine.java", "15.4 KB • JAVA SOURCE", "fa-file-code", "SENT"));
        globalLogs.add(new LogChat("Cristobal", "NioEngine.java", "15.4 KB • JAVA SOURCE", "fa-file-code", "SENT"));
    }

    public void addEntry(LogChat entry) {

        synchronized(globalLogs) {
            globalLogs.add(entry); // Añadimos al inicio para que lo nuevo salga arribas
            // Control de poda: mantenemos solo los últimos 50
            if (globalLogs.size() > 50) {
                globalLogs.remove(globalLogs.size() - 1);
            }
        }
    }

    public List<LogChat> getGlobalLogs() {
        //globalLogs.sort(Comparator.comparing(LogChat::getFecha));
        return globalLogs;
    }
}
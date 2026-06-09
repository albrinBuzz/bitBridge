package org.bitBridge.controller;

import org.bitBridge.Client.managers.TransferManager;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.models.TransferProgress;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.FileTransferState;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.FileHandshakeAction;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controlador que gestiona el ciclo de vida de las transferencias.
 * Implementa el patrón Observador con soporte multi-suscriptor (Thread-Safe).
 */
public class TransferenciaController {

    private final Map<String, Transferencia> transferMap;
    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

    // Lista multi-observador segura para operaciones concurrentes de lectura/escritura
    private final List<TransferencesObserver> observers = new CopyOnWriteArrayList<>();

    public TransferenciaController() {
        this.transferMap = new HashMap<>();
    }

    /**
     * Registra un nuevo observador en el controlador.
     */
    public void addTransferencesObserver(TransferencesObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * Elimina un observador registrado.
     */
    public void removeTransferencesObserver(TransferencesObserver observer) {
        if (observer != null) {
            observers.remove(observer);
        }
    }

    // Método para agregar una nueva transferencia
    public String addTransference(String mode, String srcAddr, String dstAddr, String fileName, TransferManager transferManager, long length) {
        UUID uuid = UUID.randomUUID();
        String id = uuid.toString();

        Transferencia transferencia = new Transferencia(id, fileName, srcAddr, dstAddr, FileTransferState.IN_PROGRESS, transferManager);
        transferencia.setTamano(length);
        transferMap.put(id, transferencia);

        startTimes.put(id, System.currentTimeMillis());

        // Notificar a todos los observadores registrados
        for (TransferencesObserver observer : observers) {
            observer.addTransference(mode, transferencia, transferManager);
        }
        return id;
    }

    // Método para actualizar el progreso de una transferencia
    public void updateProgress(FileTransferState transferState, String id, int progress) {
        Transferencia transferencia = transferMap.get(id);
        if (transferencia != null) {
            transferencia.setProgress(progress);

            // Notificar la actualización simple a toda la lista de suscritos
            for (TransferencesObserver observer : observers) {
                observer.updateTransference(transferState, id, progress);
            }
        } else {
            Logger.logInfo("No existe la transferencia con ID: " + id);
        }
    }

    public void updateProgressMetrics(FileTransferState state, String id, long currentBytes, long totalBytes) {
        Transferencia trans = transferMap.get(id);
        Long startTime = startTimes.get(id);

        if (trans != null && startTime != null && totalBytes > 0) {
            long now = System.currentTimeMillis();
            long durationMillis = now - startTime;

            int percentage = (int) ((currentBytes * 100) / totalBytes);
            trans.setProgress(percentage);

            double speedMBs = 0;
            if (durationMillis > 0) {
                speedMBs = (currentBytes / 1048576.0) / (durationMillis / 1000.0);
            }

            String eta = "Calc...";
            if (currentBytes > 0) {
                long remainingBytes = totalBytes - currentBytes;
                long msRemaining = (long) (remainingBytes * ((double) durationMillis / currentBytes));

                long sec = (msRemaining / 1000) % 60;
                long min = (msRemaining / 60000);
                eta = String.format("%02d:%02d", min, sec);
            }

            TransferProgress progressObj = new TransferProgress(id, percentage, speedMBs, eta, state);

            // Notificar las métricas avanzadas calculadas a todos los observadores
            for (TransferencesObserver observer : observers) {
                observer.updateTransferenceFull(progressObj);
            }
        } else {
            StringBuilder motivo = new StringBuilder("Fallo al actualizar métricas para ID: " + id + ". Motivo: ");
            if (trans == null) motivo.append("[Transferencia no encontrada] ");
            if (startTime == null) motivo.append("[Tiempo de inicio ausente] ");
            if (totalBytes <= 0) motivo.append("[Tamaño inválido: ").append(totalBytes).append("] ");
        }
    }

    public boolean notifyTranference(FileHandshakeCommunication handshakeCommunication) {
        boolean autoAccept = ConfiguracionApp.getInstancia()
                .obtenerBoolean(org.bitBridge.server.config.ConfigKey.TRANSFER_AUTO_ACCEPT, false);

        if (autoAccept) {
            Logger.logInfo("🤖 [CONTROLADOR] Auto-Accept activo. Autorizando transferencia entrante de forma automática.");
            return true;
        }

        // Para flujos de confirmación condicionales (boolean), evaluamos los observadores.
        // Si al menos uno de ellos procesa y aprueba (UI o módulo de políticas), se acepta.
        if (!observers.isEmpty()) {
            Logger.logInfo("🖥️ [CONTROLADOR] Solicitando confirmación manual a los observadores...");
            boolean aprobado = false;
            for (TransferencesObserver observer : observers) {
                // Si algún observador responde afirmativamente, capturamos la bandera
                if (observer.notifyTranference(handshakeCommunication)) {
                    aprobado = true;
                }
            }
            return aprobado;
        }

        Logger.logWarn("⚠️ [CONTROLADOR] No hay observadores registrados ni Auto-Accept activo. Rechazando por seguridad.");
        return false;
    }

    public void notifyTranference(FileHandshakeAction action) {
        for (TransferencesObserver observer : observers) {
            observer.notifyTranference(action);
        }
    }

    public void changeState(String fileName, FileTransferState newState) {
        Transferencia transferencia = transferMap.get(fileName);
        if (transferencia != null) {
            transferencia.setState(newState);
            Logger.logInfo("Cambiando estado de la transferencia: " + fileName + " a " + newState);
        }
    }

    public void pauseTransference(String fileName) {
        Transferencia transferencia = transferMap.get(fileName);
        if (transferencia != null && transferencia.getState() == FileTransferState.IN_PROGRESS) {
            transferencia.pause();
            changeState(fileName, FileTransferState.PAUSED);
        }
    }

    public void resumeTransference(String fileName) {
        Transferencia transferencia = transferMap.get(fileName);
        if (transferencia != null && transferencia.getState() == FileTransferState.PAUSED) {
            transferencia.resume();
            changeState(fileName, FileTransferState.IN_PROGRESS);
        }
    }

    public void cancelTransference(String fileName) {
        Transferencia transferencia = transferMap.get(fileName);
        if (transferencia != null) {
            transferencia.cancel();
            changeState(fileName, FileTransferState.CANCELLED);
        }
    }

    public void removeTransference(String id) {
        transferMap.remove(id);
        startTimes.remove(id);
    }

    public void endTransference(String fileName) {
        removeTransference(fileName);
        for (TransferencesObserver observer : observers) {
            observer.endTransference(null, fileName); // Ajustado para propagar el término del flujo
        }
    }

    public Transferencia getTransference(String fileName) {
        return transferMap.get(fileName);
    }

    public Map<String, Transferencia> getAllTransferencias() {
        return transferMap;
    }

    @Deprecated
    public TransferencesObserver setTransferencesObserver(TransferencesObserver transferencesObserver) {
        this.addTransferencesObserver(transferencesObserver);
        return transferencesObserver;
    }
}
package org.bitBridge.controller;



import org.bitBridge.Client.TransferManager;
import org.bitBridge.Observers.TransferencesObserver;
import org.bitBridge.models.TransferProgress;
import org.bitBridge.models.Transferencia;
import org.bitBridge.shared.FileTransferState;
import org.bitBridge.shared.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransferenciaController {

    private Map<String, Transferencia> transferMap; // Mapa de transferencias activas
    //private TransferencesView view; // Vista para notificar cambios
    private TransferencesObserver transferencesObserver;
    private Map<String, Long> startTimes = new ConcurrentHashMap<>();
    // Constructor
    /*public TransferenciaController(TransferencesView view) {
        this.view = view;
        this.transferMap = new HashMap<>();
    }*/

    public TransferenciaController() {
        this.transferMap = new HashMap<>();
    }

    // Método para agregar una nueva transferencia
    public String addTransference(String mode, String srcAddr, String dstAddr, String fileName, TransferManager transferManager) {
        UUID uuid = UUID.randomUUID();
        String id = uuid.toString();

        Transferencia transferencia = new Transferencia(id, fileName, srcAddr, dstAddr, FileTransferState.IN_PROGRESS, transferManager);
        transferMap.put(id, transferencia);

        // REGISTRAMOS EL TIEMPO DE INICIO AQUÍ
        startTimes.put(id, System.currentTimeMillis());

        if (transferencesObserver != null) {
            transferencesObserver.addTransference(mode, transferencia, transferManager);
        }
        return id;
    }

    // Método para actualizar el progreso de una transferencia
    public void updateProgress(FileTransferState transferState,String id, int progress) {
        //transferencesObserver.updateTransference(FileTransferState.RECEIVING, recipientNick, (int)((totalBytesRead * 100) / fileSize));
        Transferencia transferencia = transferMap.get(id);
        if (transferencia != null) {
            transferencia.setProgress(progress);
            //Logger.logInfo("Actualizando progreso de la transferencia: " + id + " - " + progress + "%");
            //transferencesObserver.updateTransference(FileTransferState.SENDING, id, (int) ((totalBytesReaded * 100) / length));
            if (transferencesObserver!=null){
                transferencesObserver.updateTransference(transferState, id,progress);
            }

            // Notificar a la vista para actualizar la barra de progreso
            //Platform.runLater(() -> view.updateTransferenceProgress(fileName, progress));
        }else {
            Logger.logInfo("no existe la transferencia");
        }
    }

    // Método para cambiar el estado de una transferencia (pausar, reanudar, cancelar)
    public void changeState(String fileName, FileTransferState newState) {
        Transferencia transferencia = transferMap.get(fileName);
        if (transferencia != null) {
            transferencia.setState(newState);
            Logger.logInfo("Cambiando estado de la transferencia: " + fileName + " a " + newState);

            // Notificar a la vista sobre el cambio de estado
            //Platform.runLater(() -> view.updateTransferenceState(fileName, newState));
        }
    }

    // Pausar una transferencia
    public void pauseTransference(String fileName) {
        Transferencia transferencia = transferMap.get(fileName);
        if (transferencia != null && transferencia.getState() == FileTransferState.IN_PROGRESS) {
            transferencia.pause();
            changeState(fileName, FileTransferState.PAUSED);
        }
    }

    // Reanudar una transferencia
    public void resumeTransference(String fileName) {
        Transferencia transferencia = transferMap.get(fileName);
        if (transferencia != null && transferencia.getState() == FileTransferState.PAUSED) {
            transferencia.resume();
            changeState(fileName, FileTransferState.IN_PROGRESS);
        }
    }

    // Cancelar una transferencia
    public void cancelTransference(String fileName) {
        Transferencia transferencia = transferMap.get(fileName);
        if (transferencia != null) {
            transferencia.cancel();
            changeState(fileName, FileTransferState.CANCELLED);
        }
    }



    public void updateProgressMetrics(FileTransferState state, String id, long currentBytes, long totalBytes) {
        Transferencia trans = transferMap.get(id);
        Long startTime = startTimes.get(id);

        if (trans != null && startTime != null && totalBytes > 0) {
            long now = System.currentTimeMillis();
            long durationMillis = now - startTime;

            // 1. Porcentaje
            int percentage = (int) ((currentBytes * 100) / totalBytes);
            trans.setProgress(percentage);

            // 2. Velocidad (MB/s)
            double speedMBs = 0;
            if (durationMillis > 0) {
                // (Bytes / 1024 / 1024) / (Segundos)
                speedMBs = (currentBytes / 1048576.0) / (durationMillis / 1000.0);
            }

            // 3. ETA (Tiempo estimado)
            String eta = "Calc...";
            if (currentBytes > 0) {
                long remainingBytes = totalBytes - currentBytes;
                // Tiempo restante en ms = (bytes restantes) * (tiempo transcurrido / bytes ya enviados)
                long msRemaining = (long) (remainingBytes * ((double) durationMillis / currentBytes));

                long sec = (msRemaining / 1000) % 60;
                long min = (msRemaining / 60000);
                eta = String.format("%02d:%02d", min, sec);
            }

            // 4. Notificar al Observer
            if (transferencesObserver != null) {
                // Creamos el objeto con toda la info
                TransferProgress progress = new TransferProgress(id, percentage, speedMBs, eta, state);

                // NOTA: Deberías añadir este método a tu interfaz TransferencesObserver
                transferencesObserver.updateTransferenceFull(progress);

                // Mantenemos compatibilidad con tu método viejo por si acaso
                transferencesObserver.updateTransference(state, id, percentage);
            }
        }
    }

    public void removeTransference(String id) {
        transferMap.remove(id);
        startTimes.remove(id); // Limpiar tiempo al terminar
    }
    // Finalizar una transferencia (llamado cuando se completa o se ha cancelado)
    public void endTransference(String fileName) {
        removeTransference(fileName);
    }

    // Obtener una transferencia por su nombre
    public Transferencia getTransference(String fileName) {
        return transferMap.get(fileName);
    }

    // Método para obtener todas las transferencias
    public Map<String, Transferencia> getAllTransferencias() {
        return transferMap;
    }

    public void setTransferencesObserver(TransferencesObserver transferencesObserver) {
        this.transferencesObserver = transferencesObserver;
    }
}

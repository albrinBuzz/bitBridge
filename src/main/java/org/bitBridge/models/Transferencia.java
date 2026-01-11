package org.bitBridge.models;


import org.bitBridge.Client.TransferManager;
import org.bitBridge.shared.FileTransferState;

public class Transferencia {

    private String id;
    private String fileName; // Nombre del archivo
    private String srcAddr; // Dirección de origen
    private String dstAddr; // Dirección de destino
    private int progress; // Porcentaje de progreso
    private FileTransferState state; // Estado de la transferencia
    private TransferManager transferManager; // El administrador de la transferencia

    // Constructor
    public Transferencia(String id,String fileName, String srcAddr, String dstAddr, FileTransferState state, TransferManager transferManager) {
        this.fileName = fileName;
        this.srcAddr = srcAddr;
        this.dstAddr = dstAddr;
        this.state = state;
        this.transferManager = transferManager;
        this.progress = 0; // Inicializamos el progreso en 0
        this.id=id;
    }

    // Métodos para obtener información de la transferencia
    public String getFileName() {
        return fileName;
    }

    public String getSrcAddr() {
        return srcAddr;
    }

    public String getDstAddr() {
        return dstAddr;
    }

    public int getProgress() {
        return progress;
    }

    public FileTransferState getState() {
        return state;
    }

    // Métodos para actualizar el estado y progreso
    public void setProgress(int progress) {
        this.progress = progress;
        // Si el progreso llega al 100%, podemos cambiar el estado
        if (progress == 100) {
            this.state = FileTransferState.COMPLETED;
        }
    }

    public void setState(FileTransferState state) {
        this.state = state;
    }

    public void start() {
        // Simula que inicia la transferencia
        this.state = FileTransferState.IN_PROGRESS;
        // Lógica para iniciar la transferencia (puedes usar transferManager para esto)
        //transferManager.startTransfer();
    }

    public void pause() {
        // Pausa la transferencia
        this.state = FileTransferState.PAUSED;
        transferManager.pause();
    }

    public void resume() {
        // Reanuda la transferencia
        this.state = FileTransferState.IN_PROGRESS;
        transferManager.resume();
    }

    public void cancel() {
        // Cancela la transferencia
        this.state = FileTransferState.CANCELLED;
        //transferManager.cancelTransfer();
    }

    public String getId() {
        return id;
    }


    // Para obtener un estado legible de la transferencia
    public String getStateDescription() {
        switch (state) {
            case IN_PROGRESS:
                return "En progreso";
            case PAUSED:
                return "Pausado";
            case COMPLETED:
                return "Completado";
            case CANCELLED:
                return "Cancelado";
            default:
                return "Desconocido";
        }
    }
}

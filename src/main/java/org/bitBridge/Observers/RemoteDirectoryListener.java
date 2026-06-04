package org.bitBridge.Observers;

import org.bitBridge.shared.core.comunication.model.basic.NodoDirectorio;

public interface RemoteDirectoryListener {
    /**
     * Se activa cada vez que llega una respuesta del servidor.
     * @param nodo El directorio solicitado con sus hijos inmediatos cargados.
     */
    void onDirectoryDataReceived(NodoDirectorio nodo);
}
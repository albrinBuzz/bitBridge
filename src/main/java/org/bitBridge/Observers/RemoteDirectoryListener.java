package org.bitBridge.Observers;

import org.bitBridge.shared.core.comunication.NodoDirectorio;

import java.util.List;

public interface RemoteDirectoryListener {
    /**
     * Se activa cada vez que llega una respuesta del servidor.
     * @param nodo El directorio solicitado con sus hijos inmediatos cargados.
     */
    void onDirectoryDataReceived(NodoDirectorio nodo);
}
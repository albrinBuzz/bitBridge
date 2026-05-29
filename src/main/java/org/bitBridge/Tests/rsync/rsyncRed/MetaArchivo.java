package org.bitBridge.Tests.rsync.rsyncRed;

import java.io.Serializable;

public class MetaArchivo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String rutaRelativa;
    private final long tamano;
    private final long ultimaModificacion;

    public MetaArchivo(String rutaRelativa, long tamano, long ultimaModificacion) {
        this.rutaRelativa = rutaRelativa;
        this.tamano = tamano;
        this.ultimaModificacion = ultimaModificacion;
    }

    public String getRutaRelativa() { return rutaRelativa; }
    public long getTamano() { return tamano; }
    public long getUltimaModificacion() { return ultimaModificacion; }
}
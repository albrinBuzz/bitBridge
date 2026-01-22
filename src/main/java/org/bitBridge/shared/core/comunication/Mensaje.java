package org.bitBridge.shared;


public class Mensaje extends Communication {

    private String contenido;

    public Mensaje( String contenido,CommunicationType type) {
        super(type);
        this.contenido = contenido;
    }

    public Mensaje(CommunicationType type) {
        super(type);
    }


    public String getContenido() {
        return contenido;
    }

    @Override
    public String toString() {
        return " Mensaje: " + contenido;
    }
}

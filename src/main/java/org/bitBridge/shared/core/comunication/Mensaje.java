package org.bitBridge.shared.core.comunication;


public class Mensaje extends Communication {

    private String contenido;

    public Mensaje(String contenido, CommunicationType type) {
        super(type);
        this.contenido = contenido;
    }

    public Mensaje() {
    }

    public Mensaje(CommunicationType communicationType, String contenido) {
        super(communicationType);
        this.contenido = contenido;
    }

    public void setContenido(String contenido) {
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

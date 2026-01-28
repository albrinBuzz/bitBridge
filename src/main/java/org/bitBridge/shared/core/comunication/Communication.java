package org.bitBridge.shared.core.comunication;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class Communication implements Serializable {

    // Cambiamos a protegido o añadimos setter para que Jackson pueda escribirlo
    @JsonProperty("communicationType")
    private CommunicationType communicationType;

    // Constructor vacío obligatorio para Jackson
    public Communication() {
    }

    public Communication(CommunicationType communicationType) {
        this.communicationType = communicationType;
    }

    public CommunicationType getType() {
        return this.communicationType;
    }

    // Añadimos setter para que Jackson pueda inyectar el tipo al deserializar
    public void setType(CommunicationType communicationType) {
        this.communicationType = communicationType;
    }
}
package org.bitBridge.Client.handlers;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClientHandler {
    Class<?> value(); // Clase del objeto/mensaje que procesa (ej: Mensaje.class)
}
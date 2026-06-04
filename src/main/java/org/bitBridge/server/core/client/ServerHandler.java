package org.bitBridge.server.core.client;


import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.core.comunication.Communication;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServerHandler {
    Class<? extends Communication> value();
    String action() default ""; // <-- NUEVO: Para diferenciar sub-flujos de una misma clase
    ExecutionMode mode() default ExecutionMode.ASYNC;
}
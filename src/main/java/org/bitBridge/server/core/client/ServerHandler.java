package org.bitBridge.server.core.client;

import org.bitBridge.shared.ExecutionMode;
import org.bitBridge.shared.core.comunication.Communication;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServerHandler {
    Class<? extends Communication> value();
    String action() default "";
    ExecutionMode mode() default ExecutionMode.ASYNC;
    ThreadCarrier carrier() default ThreadCarrier.VIRTUAL; // <-- NUEVO
}
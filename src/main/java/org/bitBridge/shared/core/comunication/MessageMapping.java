package org.bitBridge.shared.core.comunication;


import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MessageMapping {
    Class<? extends Communication> value();
}
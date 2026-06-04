package org.bitBridge.shared.core.comunication;


import org.bitBridge.shared.memory.DirectBufferPool.BufferType;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BufferPoolMapping {
    BufferType value() default BufferType.MESSAGE;
}
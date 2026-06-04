package org.bitBridge.shared.core.comunication.model.basic;

import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

@BufferPoolMapping(DirectBufferPool.BufferType.MESSAGE)
public class Alert extends Communication {


    public enum Severity {
        INFO, WARNING, ERROR, CRITICAL
    }


    public Alert() {
    }
}

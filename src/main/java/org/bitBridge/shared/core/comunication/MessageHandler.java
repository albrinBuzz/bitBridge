package org.bitBridge.shared.core.comunication;



public interface MessageHandler<T extends Communication, ContextType> {
    void handle(T message, ContextType context) throws Exception;

    // Por defecto, las tareas ligeras corren en hilos virtuales
    default RoutingStrategy getRoutingStrategy() {
        return RoutingStrategy.VIRTUAL_THREAD;
    }
}
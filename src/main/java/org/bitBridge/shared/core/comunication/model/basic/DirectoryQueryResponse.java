package org.bitBridge.shared.core.comunication.model.basic;

import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

import java.util.List;

@BufferPoolMapping(DirectBufferPool.BufferType.DIRECTORY)
public class DirectoryQueryResponse extends Communication {
    private List<NodoDirectorio> nodos;
    private NodoDirectorio nodo;
    private String rutaBase;
    private String token;
    private String targetIp;
    private String sourceIp;

    public DirectoryQueryResponse() {}
    public DirectoryQueryResponse(NodoDirectorio nodo) { this.nodo = nodo; }
    public DirectoryQueryResponse(NodoDirectorio nodo, String sourceIp, String targetIp, String token) {
        this.nodo = nodo;
        this.sourceIp = sourceIp;
        this.targetIp = targetIp;
        this.token = token;
    }
    public DirectoryQueryResponse(NodoDirectorio nodo, List<NodoDirectorio> nodos) {
        this.nodo = nodo;
        this.nodos = nodos;
    }
    public DirectoryQueryResponse(String rutaBase, List<NodoDirectorio> nodos) {
        this();
        this.rutaBase = rutaBase;
        this.nodos = nodos;
    }

    public List<NodoDirectorio> getNodos() { return nodos; }
    public String getRutaBase() { return rutaBase; }
    public NodoDirectorio getNodo() { return nodo; }
    public String getTargetIp() { return targetIp; }
    public String getSourceIp() { return sourceIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public void setToken(String token) { this.token = token; }
    public void setNodos(List<NodoDirectorio> nodos) { this.nodos = nodos; }
    public void setNodo(NodoDirectorio nodo) { this.nodo = nodo; }
    public void setRutaBase(String rutaBase) { this.rutaBase = rutaBase; }
    public String getToken() { return token; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }
}
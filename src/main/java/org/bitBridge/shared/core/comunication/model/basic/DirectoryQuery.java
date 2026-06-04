package org.bitBridge.shared.core.comunication.model.basic;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bitBridge.shared.core.comunication.BufferPoolMapping;
import org.bitBridge.shared.core.comunication.Communication;
import org.bitBridge.shared.memory.DirectBufferPool;

@BufferPoolMapping(DirectBufferPool.BufferType.DIRECTORY)
public class DirectoryQuery extends Communication {
    @JsonProperty("targetPath") private String targetPath;
    @JsonProperty("token") private String token;
    private String targetIp;
    private String sourceIp;

    public DirectoryQuery() {}
    public DirectoryQuery(String targetIp, String sourceIp, String token) {
        this.targetIp = targetIp;
        this.sourceIp = sourceIp;
        this.token = token;
    }
    public DirectoryQuery(String targetIp, String sourceIp, String token, String targetPath) {
        this.sourceIp = sourceIp;
        this.targetIp = targetIp;
        this.token = token;
        this.targetPath = targetPath;
    }

    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public String getSourceIp() { return sourceIp; }
    public String getTargetIp() { return targetIp; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    @Override
    public String getCommunicationId() { return this.getClass().getSimpleName(); }
}
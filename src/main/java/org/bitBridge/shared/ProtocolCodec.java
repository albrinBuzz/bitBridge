package org.bitBridge.shared;

public interface ProtocolCodec {
    byte[] encode(Communication obj) throws Exception;
    Communication decode(byte[] data) throws Exception;
}
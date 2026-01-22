package org.bitBridge.shared;

import org.bitBridge.shared.core.comunication.Communication;

public interface ProtocolCodec {
    byte[] encode(Communication obj) throws Exception;
    Communication decode(byte[] data) throws Exception;
}
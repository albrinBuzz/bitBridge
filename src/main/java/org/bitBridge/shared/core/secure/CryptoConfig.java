package org.bitBridge.shared.core.secure;

import java.util.ArrayList;
import java.util.List;

public class CryptoConfig {
    private final String commonName;
    private final String organizacion;
    private final String pais;
    private final int diasValidez;
    private final List<String> dominiosAdicionales;

    public CryptoConfig(String commonName, String organizacion, String pais, int diasValidez) {
        this.commonName = commonName;
        this.organizacion = organizacion;
        this.pais = pais;
        this.diasValidez = diasValidez;
        this.dominiosAdicionales = new ArrayList<>();
    }

    public void agregarDominioODns(String dnsOrIp) {
        if (dnsOrIp != null && !dnsOrIp.trim().isEmpty()) {
            this.dominiosAdicionales.add(dnsOrIp.trim());
        }
    }

    public String getX500PrincipalString() {
        return String.format("CN=%s, O=%s, C=%s", commonName, organizacion, pais);
    }

    public int getDiasValidez() { return diasValidez; }
    public List<String> getDominiosAdicionales() { return dominiosAdicionales; }
}
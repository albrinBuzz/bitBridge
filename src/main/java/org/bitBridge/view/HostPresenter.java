package org.bitBridge.view;



import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.shared.Logger;

import java.util.ArrayList;
import java.util.List;

public class HostPresenter {
    private Client cliente;

    public HostPresenter(Client cliente) {
        this.cliente = cliente;
    }

    // Aquí centralizas la lógica que antes tenías repetida en la UI
    public List<ClientInfo> procesarLista(List<ClientInfo> listaCruda) {
        if (listaCruda == null) return new ArrayList<>();

        return listaCruda.stream()
                .filter(h -> !h.getNick().equalsIgnoreCase("enviando"))
                .filter(h -> !h.getNick().matches("\\d+")) // Filtra si son solo números
                .toList();
    }

    // Método para manejar la acción de enviar
    public void solicitarEnvio(ClientInfo target) {
        Logger.logInfo("Preparando envío para: " + target.getNick());
        // Aquí podrías abrir diálogos o validar estados antes de llamar al cliente
    }
}
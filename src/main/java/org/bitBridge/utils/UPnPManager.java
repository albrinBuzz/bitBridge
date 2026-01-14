package org.bitBridge.utils;

import org.bitBridge.shared.Logger;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.support.igd.PortMappingListener;
import org.jupnp.support.model.PortMapping;

public class UPnPManager {
    private UpnpService upnpService;

    public void openPort(int port) {
        try {
            // 1. Crear el servicio
            upnpService = new UpnpServiceImpl();

            // 2. PEQUEÑA ESPERA (Solución al error null)
            // Esperamos máximo 2 segundos a que el registro se inicialice
            int retries = 0;
            while (upnpService.getRegistry() == null && retries < 10) {
                Thread.sleep(200);
                retries++;
            }

            if (upnpService.getRegistry() == null) {
                Logger.logError("[UPnP] No se pudo inicializar el Registro de jUPnP (Timeout).");
                return;
            }

            // 3. Configurar el mapeo
            PortMapping mapping = new PortMapping(
                    port,
                    NetworkManager.getLocalIp(),
                    PortMapping.Protocol.TCP,
                    "BitBridge Transfer Service"
            );

            // 4. Añadir el Listener
            upnpService.getRegistry().addListener(new PortMappingListener(mapping));

            // 5. Buscar el Router
            upnpService.getControlPoint().search();

            Logger.logInfo("[UPnP] Buscando Router para mapear el puerto: " + port);

        } catch (Exception e) {
            Logger.logError("[UPnP] Error al intentar abrir puerto: " + e.getMessage());
        }
    }

    public void closePort() {
        if (upnpService != null) {
            try {
                // Esto elimina automáticamente las reglas creadas en el router
                upnpService.shutdown();
                Logger.logInfo("[UPnP] Servicio detenido y puerto liberado.");
            } catch (Exception e) {
                Logger.logError("[UPnP] Error al cerrar servicio: " + e.getMessage());
            }
        }
    }
}
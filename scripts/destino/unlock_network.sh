#!/bin/bash
echo "🚀 Optimizando Kernel para Stress Test Extremo..."

# 1. Aumentar el límite de archivos abiertos (Saturación de Sockets)
ulimit -n 100000

# 2. Aumentar la cola de conexiones pendientes (Backlog)
# Este es tu cuello de botella actual (estaba en 128 por defecto)
sysctl -w net.core.somaxconn=10000

# 3. Aumentar la cola de paquetes de entrada de la tarjeta de red
sysctl -w net.core.netdev_max_backlog=10000

# 4. Aumentar el límite de SYN Backlog (conexiones simultáneas en handshake)
sysctl -w net.ipv4.tcp_max_syn_backlog=10000

# 5. Reutilización rápida de sockets en TIME_WAIT
sysctl -w net.ipv4.tcp_tw_reuse=1

# 6. (Opcional) Desactivar el firewall temporalmente para eliminar inspección de paquetes
# Solo si después de los sysctl sigues viendo baja eficiencia
# systemctl stop firewalld

echo "✅ Sistema optimizado. ¡Lanza el servidor BitBridge ahora!"
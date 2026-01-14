# Abrir puertos 8080 y 8081 en protocolo TCP
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --permanent --add-port=8081/tcp

# (Opcional pero recomendado) Abrir servicio mDNS para el descubrimiento
sudo firewall-cmd --permanent --add-service=mdns

# 2. Permitir el tráfico Multicast (necesario para JmDNS)
sudo firewall-cmd --permanent --add-protocol=igmp

# Aplicar los cambios
sudo firewall-cmd --reload

# Verificar que se abrieron correctamente
sudo firewall-cmd --list-ports
#!/bin/bash

# 1. Configuración de Rutas
APP_NAME="bitbridge"
INSTALL_DIR="/opt/$APP_NAME"
BIN_LINK="/usr/local/bin/$APP_NAME"
SOURCE_DIR=$(dirname "$(dirname "$(realpath "$0")")")

echo "🛠️ Instalando BitBridge en el sistema..."

# 2. Verificar privilegios
if [ "$EUID" -ne 0 ]; then
  echo "❌ Por favor, ejecuta el instalador con sudo: sudo ./install.sh"
  exit 1
fi

# 3. Crear directorios de destino
mkdir -p "$INSTALL_DIR"

# 4. Sincronizar archivos (evitando logs y basura temporal)
echo "📂 Copiando archivos a $INSTALL_DIR..."
cp -r "$SOURCE_DIR/"* "$INSTALL_DIR/"
rm -rf "$INSTALL_DIR/logs/"*
rm -rf "$INSTALL_DIR/temp/"*

# 5. Configurar Permisos
echo "🔒 Ajustando permisos..."
chmod +x "$INSTALL_DIR/bin/bitbridge.sh"
# Aseguramos que las carpetas de trabajo sean escribibles
chmod -R 777 "$INSTALL_DIR/logs"
chmod -R 777 "$INSTALL_DIR/temp"

# 6. Crear Enlace Simbólico Global
# Esto permite que escribas 'bitbridge' en cualquier terminal
echo "🔗 Creando acceso directo global..."
ln -sf "$INSTALL_DIR/bin/bitbridge.sh" "$BIN_LINK"

# 7. Crear el Desinstalador (Opcional pero recomendado)
cat <<EOF > "$INSTALL_DIR/uninstall.sh"
#!/bin/bash
rm -rf "$INSTALL_DIR"
rm "$BIN_LINK"
echo "✅ BitBridge ha sido eliminado del sistema."
EOF
chmod +x "$INSTALL_DIR/uninstall.sh"

echo "------------------------------------------------"
echo "✅ INSTALACIÓN COMPLETADA"
echo "🚀 Ahora puedes iniciar el nodo desde cualquier parte con el comando: $APP_NAME"
echo "------------------------------------------------"
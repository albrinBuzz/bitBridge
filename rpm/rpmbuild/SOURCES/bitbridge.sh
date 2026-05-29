#!/bin/bash

# --- CONFIGURACIÓN DE RUTAS ---
APP_NAME="BitBridge"
INSTALL_DIR="/opt/bitbridge"
JAR_PATH="$INSTALL_DIR/bin/BitBridge.jar"
SYSTEM_VMOPTIONS="$INSTALL_DIR/bin/bitbridge64.vmoptions"
USER_VMOPTIONS="$HOME/.config/bitbridge/bitbridge64.vmoptions"
LOG_DIR="$HOME/.local/share/bitbridge/logs"
PID_FILE="/tmp/bitbridge.pid"

# Asegurar que el directorio de logs del usuario existe
mkdir -p "$LOG_DIR"

# --- 1. PRE-FLIGHT CHECKS (Evitar fallos silenciosos) ---

# Verificar si Java está instalado
if ! command -v java >/dev/null 2>&1; then
    echo "❌ Error: Java no está instalado en el sistema."
    exit 1
fi

# Verificar si ya hay una instancia corriendo
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null; then
        echo "⚠️ $APP_NAME ya se está ejecutando (PID: $PID)."
        exit 1
    fi
fi

# --- 2. LÓGICA DE CARGA DE JVM OPTIONS (Cascada) ---

if [ -f "$USER_VMOPTIONS" ]; then
    VM_OPTS=$(grep -E -v "^#.*" "$USER_VMOPTIONS" | tr '\n' ' ')
    SOURCE="Usuario"
elif [ -f "$SYSTEM_VMOPTIONS" ]; then
    VM_OPTS=$(grep -E -v "^#.*" "$SYSTEM_VMOPTIONS" | tr '\n' ' ')
    SOURCE="Sistema"
else
    # Fallback seguro pero optimizado para NIO
    VM_OPTS="-Xms512m -Xmx2g -XX:MaxDirectMemorySize=1g -XX:+UseZGC"
    SOURCE="Default (Fallback)"
fi

# --- 3. OPTIMIZACIONES DE TIEMPO REAL Y RED ---

# Flags críticas para baja latencia y alto rendimiento NIO
REALTIME_OPTS="
    -Djava.net.preferIPv4Stack=true
    -Dio.netty.leakDetection.level=disabled
    -Dfile.encoding=UTF-8
    -Duser.timezone=UTC
    -Dbitbridge.home=$INSTALL_DIR
    -Djava.io.tmpdir=$INSTALL_DIR/temp"

echo "------------------------------------------------"
echo "🚀 $APP_NAME - High Performance File Transfer"
echo "📅 Fecha: $(date)"
echo "⚙️  Configuración: $SOURCE"
echo "📂 Logs: $LOG_DIR/startup.log"
echo "------------------------------------------------"

# --- 4. EJECUCIÓN CON MANEJO DE PROCESO ---

# Redirigir la salida estándar y errores a un log de inicio para debugging
# El uso de 'nohup' y '&' es opcional, aquí lo dejamos en primer plano para el .desktop
# Guardamos el PID para control de instancias
echo $$ > "$PID_FILE"

exec java $VM_OPTS $REALTIME_OPTS -jar "$JAR_PATH" "$@" >> "$LOG_DIR/startup.log" 2>&1

# Al terminar, limpiar PID
rm -f "$PID_FILE"
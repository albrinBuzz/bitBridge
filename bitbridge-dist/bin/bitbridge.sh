#!/bin/bash

# ---------------------------------------------------------------------
# BitBridge Professional Startup Script (v3.0 - Fat JAR Edition)
# ---------------------------------------------------------------------

# 1. Rutas e Identificación
BOOTSTRAP_DIR=$(dirname "$(realpath "$0")")
BITBRIDGE_HOME=$(dirname "$BOOTSTRAP_DIR")
VM_OPTIONS_FILE="$BOOTSTRAP_DIR/bitbridge64.vmoptions"
PID_FILE="$BOOTSTRAP_DIR/bitbridge.pid"
LOG_DIR="$BITBRIDGE_HOME/logs"
LOG_FILE="$LOG_DIR/startup.log"
# Según tu POM, el nombre final es BitBridge-Desktop.jar
JAR_FILE="$BOOTSTRAP_DIR/BitBridge-Desktop.jar"

mkdir -p "$BITBRIDGE_HOME/temp"
mkdir -p "$LOG_DIR"

# 2. Control de Instancia Única (Lock)
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null; then
        echo "⚠️ BitBridge ya está corriendo (PID: $PID). Detenlo antes de iniciar otro."
        exit 1
    fi
    rm "$PID_FILE"
fi

# 3. Localización Inteligente de Java
if [ -x "$BITBRIDGE_HOME/jbr/bin/java" ]; then
    JAVA_BIN="$BITBRIDGE_HOME/jbr/bin/java"
elif [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
else
    JAVA_BIN=$(which java)
fi

if [ ! -x "$JAVA_BIN" ]; then
    echo "❌ Error: No se encontró un entorno Java válido."
    exit 1
fi

# 4. Gestión Dinámica de Memoria y VM Options
# Como usas NIO, forzamos límites para evitar que el OS mate el proceso
if [ -f "$VM_OPTIONS_FILE" ]; then
    VM_OPTIONS=$(grep -E -v "^#.*" "$VM_OPTIONS_FILE" | tr '\n' ' ')
else
    # Auto-cálculo si no existe el archivo
    TOTAL_RAM=$(free -m | awk '/^Mem:/{print $2}')
    DIRECT_MEM=$((TOTAL_RAM / 4)) # 25% para NIO
    HEAP_MEM=$((TOTAL_RAM / 4))   # 25% para el Heap
    VM_OPTIONS="-Xms256m -Xmx${HEAP_MEM}m -XX:MaxDirectMemorySize=${DIRECT_MEM}m -XX:+UseZGC"
fi

# 5. Validación de integridad
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Error crítico: No se encontró el binario en $JAR_FILE"
    exit 1
fi

# 6. Ejecución "Detached" (Estilo Servidor)
echo "🚀 Iniciando BitBridge Engine..."
echo "------------------------------------------------" >> "$LOG_FILE"
echo "📅 Lanzamiento: $(date)" >> "$LOG_FILE"
echo "☕ Java: $($JAVA_BIN -version 2>&1 | head -n 1)" >> "$LOG_FILE"
echo "⚙️ Flags: $VM_OPTIONS" >> "$LOG_FILE"

# Nota: Al ser Fat JAR, el Classpath se maneja internamente,
# pero pasamos -D loader.path para cargar plugins externos en /lib si existieran.
nohup "$JAVA_BIN" \
  $VM_OPTIONS \
  -Dfile.encoding=UTF-8 \
  -Dbitbridge.home="$BITBRIDGE_HOME" \
  -Djava.io.tmpdir="$BITBRIDGE_HOME/temp" \
  -Dloader.path="$BITBRIDGE_HOME/lib" \
  -jar "$JAR_FILE" "$@" >> "$LOG_FILE" 2>&1 &

NEW_PID=$!
echo $NEW_PID > "$PID_FILE"

echo "✅ Nodo activo con PID: $NEW_PID"
echo "📂 Directorio: $BITBRIDGE_HOME"
echo "📜 Monitorea la actividad con: tail -f $LOG_FILE"
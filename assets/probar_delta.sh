#!/bin/bash

# Configuración de rutas (Ajusta si es necesario)
CARPETA_LOCAL="/home/cris/BitBridge_Shared"
LOG_TEST="$CARPETA_LOCAL/log_grande_test.log"
CLASE_DEMO="org.bitBridge.Tests.rsync.rsyncRed.DemoRsyncCarpeta"

echo "========================================================="
echo "1. GENERANDO UN LOG GRANDE INICIAL (~5 MB)..."
echo "========================================================="
# Creamos un archivo con 100,000 líneas repetitivas para simular un log real
for i in {1..100000}; do
    echo "[$i] 2026-05-29 04:00:00 INFO org.bitBridge.Service - Procesando petición estándar en el sistema." >> "$LOG_TEST"
done

echo "Archivo creado: $LOG_TEST ($(du -sh "$LOG_TEST" | cut -f1))"
echo ""

echo "========================================================="
echo "2. PRIMERA SINCRONIZACIÓN (Envío completo del archivo nuevo)"
echo "========================================================="
# Ejecutamos tu clase Demo usando Maven (asumiendo que estás en la raíz del proyecto)
# Si no usas Maven, puedes ejecutarlo con el comando largo de java -classpath que usas en el IDE
mvn exec:java -Dexec.mainClass="$CLASE_DEMO"

echo ""
echo "Presiona [ENTER] para modificar el archivo e iniciar la prueba delta..."
read

echo "========================================================="
echo "3. MODIFICANDO SOLO UNA LÍNEA EN EL MEDIO DEL LOG..."
echo "========================================================="
# Usamos 'sed' para reemplazar la línea 50,000 con un mensaje de error crítico personalizado
sed -i '50000s/.*/[50000] 2026-05-29 04:05:12 ERROR org.bitBridge.Exception - ¡ALERTA! Línea modificada para testing de BitBridge./' "$LOG_TEST"

echo "Línea 50,000 modificada con éxito."
echo ""

echo "========================================================="
echo "4. SEGUNDA SINCRONIZACIÓN (¡Prueba de Fuego del Rolling Hash!)"
echo "========================================================="
mvn exec:java -Dexec.mainClass="$CLASE_DEMO"

echo ""
echo "========================================================="
echo "Prueba finalizada. Revisa las métricas de la segunda corrida."
echo "========================================================="
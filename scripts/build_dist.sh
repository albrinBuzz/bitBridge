#!/bin/bash

# Nombre del directorio de salida
DIST_DIR="bitbridge-dist"

echo "🚀 Iniciando creación de estructura BitBridge-Dist..."

# 1. Crear Estructura de Directorios
mkdir -p $DIST_DIR/bin
mkdir -p $DIST_DIR/lib
mkdir -p $DIST_DIR/jbr
mkdir -p $DIST_DIR/help/plugin-resources/images
mkdir -p $DIST_DIR/license

# 2. Crear bitbridge64.vmoptions (Configuración de Memoria NIO)
cat <<EOF > $DIST_DIR/bin/bitbridge64.vmoptions
# BitBridge VM Options
-Xms512m
-Xmx1536m
# Límite crítico para tus DirectBufferPool
-XX:MaxDirectMemorySize=1024m
-XX:+UseZGC
-Dfile.encoding=UTF-8
-Djava.net.preferIPv4Stack=true
EOF

# 3. Crear bitbridge.properties (Configuración de la App)
cat <<EOF > $DIST_DIR/bin/bitbridge.properties
# BitBridge Core Configuration
servidor.puerto=8080
nio.pool_capacity=100
transfer.max_active=5
# Ruta por defecto relativa a la base
server.shared.dir=../shared
EOF

# 4. Crear el Lanzador Inteligente (bitbridge.sh)
cat <<EOF > $DIST_DIR/bin/bitbridge.sh
#!/bin/sh
# ---------------------------------------------------------------------
# BitBridge Startup Script
# ---------------------------------------------------------------------

BOOTSTRAP_DIR=\$(dirname "\$(realpath "\$0")")
BITBRIDGE_HOME=\$(dirname "\$BOOTSTRAP_DIR")

# Buscar JRE interno primero
if [ -d "\$BITBRIDGE_HOME/jbr" ] && [ -x "\$BITBRIDGE_HOME/jbr/bin/java" ]; then
    JAVA_BIN="\$BITBRIDGE_HOME/jbr/bin/java"
else
    JAVA_BIN="java"
fi

# Cargar opciones de la JVM
VM_OPTIONS_FILE="\$BOOTSTRAP_DIR/bitbridge64.vmoptions"
VM_OPTIONS=\$(grep -v "^#" "\$VM_OPTIONS_FILE" | tr '\n' ' ')

# Classpath dinámico (lib/*)
CLASSPATH="\$BITBRIDGE_HOME/lib/*"

exec "\$JAVA_BIN" \\
  \$VM_OPTIONS \\
  -classpath "\$CLASSPATH" \\
  -Dbitbridge.home="\$BITBRIDGE_HOME" \\
  -Didea.properties.file="\$BOOTSTRAP_DIR/bitbridge.properties" \\
  org.bitBridge.Main "\$@"
EOF

# 5. Dar permisos de ejecución
chmod +x $DIST_DIR/bin/bitbridge.sh

# 6. Crear archivos informativos básicos
echo "BitBridge Build 2026.1" > $DIST_DIR/build.txt
echo "Guía rápida: Ejecuta bin/bitbridge.sh para iniciar el nodo." > $DIST_DIR/help/ReferenceCard.txt
touch $DIST_DIR/license/LICENSE.txt

echo "✅ Estructura creada con éxito en: $DIST_DIR"
echo "👉 Ahora copia tus archivos .jar a la carpeta $DIST_DIR/lib/"
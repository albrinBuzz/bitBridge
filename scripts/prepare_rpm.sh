#!/bin/bash

# Configuración de rutas
PROJECT_ROOT=$(pwd)
RPM_SOURCES="$PROJECT_ROOT/rpm/rpmbuild/SOURCES"
JAR_NAME="BitBridge-Desktop.jar"

echo "🧹 Limpiando y preparando fuentes para el RPM..."

# 1. Asegurar que el JAR más reciente esté listo
if [ -f "target/$JAR_NAME" ]; then
    cp "target/$JAR_NAME" "$RPM_SOURCES/"
else
    echo "❌ Error: No se encontró el JAR en target/. Ejecuta mvn package primero."
    exit 1
fi

# 2. Sincronizar Iconos y Desktop
cp assets/file-sharing.png "$RPM_SOURCES/bitbridge.png" 2>/dev/null || cp "$RPM_SOURCES/file-sharing.png" "$RPM_SOURCES/bitbridge.png"
# El archivo desktop ya lo tienes en SOURCES según tu tree

# 3. Copiar el archivo de opciones de memoria (VITAL para NIO)
cp bitbridge-dist/bin/bitbridge64.vmoptions "$RPM_SOURCES/"

echo "✅ Fuentes listas en $RPM_SOURCES"
echo "🚀 Ya puedes ejecutar: rpmbuild -ba rpm/SPECS/setup.spec"
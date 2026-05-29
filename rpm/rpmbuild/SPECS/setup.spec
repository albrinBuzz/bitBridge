Name:           bitbridge
Version:        1.0
Release:        1%{?dist}
Summary:        Professional High-Performance File Bridge with NIO
License:        Apache-2.0
URL:            https://github.com/tu-usuario/bitbridge

Source0:        BitBridge-Desktop.jar
Source1:        bitbridge.desktop
Source2:        bitbridge.png
Source3:        bitbridge64.vmoptions

Requires:       java-21-openjdk bash firewalld
BuildArch:      noarch

%description
BitBridge es un motor de transferencia de archivos de alto rendimiento
utilizando Java NIO y Spring Boot con gestión dinámica de memoria.

%prep
%setup -q -c -T

%install
# 1. Crear estructura de directorios
mkdir -p %{buildroot}/opt/bitbridge/{bin,logs,temp}
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/share/applications
mkdir -p %{buildroot}/usr/share/icons/hicolor/48x48/apps/

# 2. Instalar archivos base
install -p -m 644 %{SOURCE0} %{buildroot}/opt/bitbridge/bin/BitBridge.jar
install -p -m 644 %{SOURCE3} %{buildroot}/opt/bitbridge/bin/bitbridge64.vmoptions
install -p -m 644 %{SOURCE2} %{buildroot}/usr/share/icons/hicolor/48x48/apps/bitbridge.png
install -p -m 644 %{SOURCE1} %{buildroot}/usr/share/applications/bitbridge.desktop

# 3. CREACIÓN DEL LANZADOR PROFESIONAL CON CASCADA
cat <<'EOF' > %{buildroot}/usr/bin/bitbridge
#!/usr/bin/bash

# --- CONFIGURACIÓN DE RUTAS ---
APP_NAME="BitBridge"
INSTALL_DIR="/opt/bitbridge"
JAR_PATH="$INSTALL_DIR/bin/BitBridge.jar"
SYSTEM_VMOPTIONS="$INSTALL_DIR/bin/bitbridge64.vmoptions"
USER_VMOPTIONS="$HOME/.config/bitbridge/bitbridge64.vmoptions"
# Usamos el runtime directory de Linux para el PID si existe, si no /tmp
PID_FILE="${XDG_RUNTIME_DIR:-/tmp}/bitbridge.pid"

# --- 1. VALIDACIÓN DE ENTORNO ---

# Verificar si Java está presente
if ! command -v java >/dev/null 2>&1; then
    echo "❌ Error: Java no encontrado. Por favor instale java-21-openjdk."
    exit 1
fi

# Evitar múltiples instancias que colapsen los puertos/memoria
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if ps -p "$OLD_PID" > /dev/null; then
        echo "⚠️  $APP_NAME ya está en ejecución (PID: $OLD_PID)."
        exit 1
    fi
fi

# Asegurar que el directorio temporal del usuario existe y es escribible
mkdir -p "$INSTALL_DIR/temp" 2>/dev/null || mkdir -p "$HOME/.cache/bitbridge/temp"

# --- 2. LÓGICA DE CASCADA (Prioridad Usuario) ---

if [ -f "$USER_VMOPTIONS" ]; then
    # Cargamos la configuración que el usuario guardó desde la GUI
    VM_OPTS=$(grep -E -v "^#.*" "$USER_VMOPTIONS" | tr '\n' ' ')
    STRATEGY="User-Defined"
elif [ -f "$SYSTEM_VMOPTIONS" ]; then
    # Cargamos la configuración base del RPM
    VM_OPTS=$(grep -E -v "^#.*" "$SYSTEM_VMOPTIONS" | tr '\n' ' ')
    STRATEGY="System-Default"
else
    # Fallback de emergencia si alguien borró todo
    VM_OPTS="-Xms64m -Xmx2g -XX:MaxDirectMemorySize=1g -XX:+UseZGC"
    STRATEGY="Hardcoded-Fallback"
fi

# --- 3. FLAGS PARA TIEMPO REAL Y NIO ---

# Optimizaciones críticas para Netty y transferencia de archivos
# -Dsun.io.serialization.extendedDebugInfo=false (Mejora rendimiento)
# -Djava.net.preferIPv4Stack=true (Evita latencia en resolución dual-stack)
NETWORK_OPTS="-Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8"
APP_CONTEXT="-Dbitbridge.home=$INSTALL_DIR -Djava.io.tmpdir=$INSTALL_DIR/temp"

# --- 4. LANZAMIENTO ---

echo "🚀 Starting $APP_NAME [Strategy: $STRATEGY]"

# Guardar el PID de este proceso antes de hacer el exec
echo $$ > "$PID_FILE"

# 'exec' reemplaza el proceso de bash por el de java (ahorra RAM y recibe señales OS directamente)
exec /usr/bin/java $VM_OPTS $NETWORK_OPTS $APP_CONTEXT -jar "$JAR_PATH" "$@"

# Limpieza del PID (solo se alcanza si exec falla)
rm -f "$PID_FILE"
EOF

chmod 755 %{buildroot}/usr/bin/bitbridge

%post
# Configuración de Firewall automática
if [ -x "$(command -v firewall-cmd)" ]; then
    firewall-cmd --permanent --add-port={8080,9090}/tcp --zone=public >/dev/null 2>&1
    firewall-cmd --reload >/dev/null 2>&1
fi

%files
%defattr(-,root,root)
%dir /opt/bitbridge
/opt/bitbridge/bin/BitBridge.jar

# El archivo de sistema es la base, no se debe reemplazar si el admin lo toca
%config(noreplace) /opt/bitbridge/bin/bitbridge64.vmoptions

# Permisos para carpetas de trabajo
%attr(777,root,root) %dir /opt/bitbridge/logs
%attr(777,root,root) %dir /opt/bitbridge/temp

# Binario y Desktop
/usr/bin/bitbridge
/usr/share/applications/bitbridge.desktop
/usr/share/icons/hicolor/48x48/apps/bitbridge.png

%changelog
* Thu Feb 05 2026 cr <cris550@gmail.com> - 1.0-1
- Implementación de Lanzador con Cascada (User Config vs System Config).
- Optimización de flags de red para NIO.
- Inclusión de iconos y acceso directo .desktop.
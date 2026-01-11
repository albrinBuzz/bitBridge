Name:           FileTalk
Version:        1.0
Release:        1%{?dist}
Summary:        Organizador automático de archivos por extensión
License:        Apache-2.0
URL:            https://github.com/albrinBuzz/Jsorter

Source0:        FileTalk-Desktop.jar
Source1:        FileTalk.desktop
Source2:        file-sharing.png

# Ajustado para Fedora: java-17-openjdk-devel asegura soporte completo (incluido compilador)
Requires:       java-21-openjdk.x86_64 bash
BuildArch:      noarch

%description
FileTalk agiliza la gestión de carpetas organizando automáticamente los archivos
basándose en sus extensiones. Incluye servidor web integrado con PrimeFaces.

%prep
# No necesitamos desempaquetar nada
%setup -q -c -T

%install
# 1. Crear directorios necesarios
mkdir -p %{buildroot}/opt/FileTalk
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/share/applications
mkdir -p %{buildroot}/usr/share/icons/hicolor/48x48/apps/

# 2. Copiar archivos
install -p -m 644 %{SOURCE0} %{buildroot}/opt/FileTalk/FileTalk.jar
install -p -m 644 %{SOURCE2} %{buildroot}/usr/share/icons/hicolor/48x48/apps/FileTalk.png
install -p -m 644 %{SOURCE1} %{buildroot}/usr/share/applications/FileTalk.desktop

# 3. Crear el Wrapper de lanzamiento (esto sustituye al symlink problemático)
cat <<EOF > %{buildroot}/usr/bin/FileTalk
#!/bin/bash
# Script para lanzar FileTalk con soporte de JoinFaces
/usr/bin/java \
    -Djoinfaces.jsf.classes-scan-packages=org.primefaces,jakarta.faces,com.sun.faces \
    -jar /opt/FileTalk/FileTalk.jar "\$@"
EOF

# Asegurar que el script sea ejecutable
chmod 755 %{buildroot}/usr/bin/FileTalk

%post
# Configuración de Firewall (Puertos 8080 y 9090)
if [ -x "$(command -v firewall-cmd)" ]; then
    firewall-cmd --permanent --add-port=8080/tcp --zone=public >/dev/null 2>&1
    firewall-cmd --permanent --add-port=9090/tcp --zone=public >/dev/null 2>&1
    firewall-cmd --reload >/dev/null 2>&1
fi

%postun
# Limpieza de Firewall al desinstalar
if [ \$1 -eq 0 ]; then
    if [ -x "$(command -v firewall-cmd)" ]; then
        firewall-cmd --permanent --remove-port=8080/tcp --zone=public >/dev/null 2>&1
        firewall-cmd --permanent --remove-port=9090/tcp --zone=public >/dev/null 2>&1
        firewall-cmd --reload >/dev/null 2>&1
    fi
fi

%files
%defattr(-,root,root)
%dir /opt/FileTalk
/opt/FileTalk/FileTalk.jar
%attr(755,root,root) /usr/bin/FileTalk
/usr/share/applications/FileTalk.desktop
/usr/share/icons/hicolor/48x48/apps/FileTalk.png

%changelog
* Thu Jan 08 2026 cr <cris550@gmail.com> - 1.0-1
- Corrección de dependencia java-17 a java-17-openjdk-devel.
- Sustitución de symlink por script wrapper en /usr/bin.
- Añadida limpieza de firewall en post-uninstallation.
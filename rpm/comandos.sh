# Variables para evitar errores de escritura
APP_NAME="BitBridge"
VERSION="1.0"
SOURCE_PATH="/home/cris/java/javafx/proyectos/bitBrige"

# Crear un tarball limpio (excluyendo lo que no se debe distribuir)
tar -czvf "rpm/rpmbuild/SOURCES/${APP_NAME}.tar.gz" \
    --exclude='target' \
    --exclude='.git' \
    --exclude='logs' \
    --exclude='received_captures' \
    -C "$SOURCE_PATH" .

# Sincronizar con el directorio de construcción de RPM del sistema
cp -r rpm/rpmbuild ~/



rpmbuild -ba ~/rpmbuild/SPECS/setup.spec

#/home/cris/rpmbuild/RPMS/x86_64/FileTalk-1.0-1.x86_64.rpm
cd ~/rpmbuild/RPMS/x86_64/

sudo dnf install ./FileTalk-1.0-1.x86_64.rpm
sudo rpm -ivh --nodeps FileTalk-1.0-1.x86_64.rpm
sudo rpm -ivh --nodeps FileTalk-1.0-1.fc42.noarch.rpm

sudo firewall-cmd --list-ports

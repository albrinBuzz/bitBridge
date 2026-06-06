#!/bin/bash
# =========================================================================
# SCRIPT 2: ENTORNO, COMPILACIÓN Y DESPLIEGUE (CORRER DENTRO DE LA VM)
# =========================================================================

echo "[*] Configurando variables de entorno globales de Java..."
export JAVA_HOME=$(readlink -f /usr/bin/javac | sed "s:/bin/javac::")
export PATH=$JAVA_HOME/bin:$PATH

# Hacer que las variables de Java persistan si te deslogueas
if [ ! -f /etc/profile.d/java.sh ]; then
    sudo sh -c "echo 'export JAVA_HOME=$JAVA_HOME' > /etc/profile.d/java.sh"
    sudo sh -c "echo 'export PATH=\$JAVA_HOME/bin:\$PATH' >> /etc/profile.d/java.sh"
fi

echo "[*] Limpiando directorios previos de BitBridge..."
rm -rf bitBridge/

echo "[*] Clonando el repositorio desde GitHub..."
git clone https://github.com/albrinBuzz/bitBridge.git
cd bitBridge/

echo "[*] Saltando a la rama de desarrollo NIO..."
git switch feat/network-nio-abstraction

echo "[*] Iniciando compilación pesada con Maven Wrapper..."
chmod +x mvnw
./mvnw clean package -DskipTests

if [ -f target/BitBridge-CLI.jar ]; then
    echo "[+] Compilación exitosa. Levantando Hub en segundo plano..."
    cd target/

    # Lanzamos el JAR de forma agnóstica a la sesión SSH usando nohup
    nohup java -jar BitBridge-CLI.jar --headless > ../../server_runtime.log 2>&1 &

    echo "========================================================================="
    echo " ✅ BITBRIDGE EN EJECUCIÓN"
    echo " El servidor está corriendo de forma persistente en el puerto 8080."
    echo " Puedes ver los logs en tiempo real con: tail -f ../../server_runtime.log"
    echo "========================================================================="
else
    echo "🚨 [ERROR] No se pudo encontrar el archivo BitBridge-CLI.jar en target/."
    exit 1
fi
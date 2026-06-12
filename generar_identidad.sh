#!/bin/bash
# Uso: ./generar_identidad.sh <nombre_identidad> <password>

NOMBRE=bitDrige
PASS=bitDrige123

echo "Generando identidad para: $NOMBRE..."

# 1. Crear el Keystore (.p12)
keytool -genkeypair \
  -alias $NOMBRE \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -deststoretype PKCS12 \
  -keystore ${NOMBRE}_keystore.p12 \
  -ext SAN=dns:localhost,ip:127.0.0.1 \
  -dname "CN=$NOMBRE,O=BitBridgeTest,C=CL" \
  -storepass $PASS \
  -noprompt

# 2. Exportar el certificado (.crt)
keytool -exportcert \
  -alias $NOMBRE \
  -keystore ${NOMBRE}_keystore.p12 \
  -file ${NOMBRE}_certificado.crt \
  -storepass $PASS \
  -noprompt

echo "Listo: ${NOMBRE}_keystore.p12 y ${NOMBRE}_certificado.crt generados."
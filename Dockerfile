# Usar una imagen base de OpenJDK para ejecutar el archivo .jar
FROM openjdk:17-jdk-slim

# Establecer el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiar el archivo .jar desde tu máquina local al contenedor
COPY ./FileTalk.jar /app/FileTalk.jar

EXPOSE 8080

# Comando por defecto para ejecutar el archivo .jar con Java
CMD ["java", "-jar", "/app/FileTalk.jar"]

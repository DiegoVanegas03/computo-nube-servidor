FROM eclipse-temurin:11-jdk-jammy

WORKDIR /app

# Copiar librerías
COPY lib/ lib/

# Copiar código fuente
COPY src/ src/

# Crear directorio build
RUN mkdir build

# Compilar
RUN javac -cp "lib/mysql-connector-j-9.4.0.jar:lib/json-20240303.jar" -d build $(find src -name '*.java')

# Exponer puerto
EXPOSE 2558

# Comando para ejecutar
CMD ["java", "-cp", "lib/mysql-connector-j-9.4.0.jar:lib/json-20240303.jar:build", "server.Server"]
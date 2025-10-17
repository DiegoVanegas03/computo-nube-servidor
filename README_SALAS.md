# Sistema de Salas para Servidor Multihilo con Comunicación JSON

Este proyecto es una extensión del servidor multihilo básico, incorporando un **sistema de salas** donde cada sala corresponde a un archivo de nivel en `/src/levels`. El broadcast de mensajes se limita únicamente a los usuarios conectados en la misma sala.

## 🚀 Características principales

* **Autenticación de usuarios** con base de datos MySQL.
* **Sistema de salas**: Cada sala es un archivo `.txt` en `/src/levels`.
* **Broadcast por sala**: Los mensajes solo se envían a usuarios de la misma sala.
* **Comunicación 100% basada en JSON**.
* **Manejo de conexiones concurrentes** mediante hilos.

## 📡 Flujo de Conexión y Uso

### 1. Conexión Inicial
El cliente establece una conexión TCP al puerto del servidor (por defecto 2558).

### 2. Autenticación
El cliente envía como **primer mensaje** un JSON de autenticación:
```json
{
  "username": "alex_",
  "password": "1234"
}
```

**Respuesta de autenticación exitosa:**
- El servidor envía automáticamente la lista de salas disponibles.

**Respuesta de fallo:**
```json
{
  "user": "server",
  "time": "2025-10-16 12:00:00",
  "info": {
    "status": "authentication_failed",
    "message": "Error: Credenciales inválidas"
  }
}
```

### 3. Lista de Salas
Después de autenticación exitosa, el servidor envía:
```json
{
  "action": "room_list",
  "rooms": ["level00.txt"]
}
```

### 4. Unirse a una Sala
El cliente elige una sala enviando:
```json
{
  "action": "join_room",
  "room": "level00.txt"
}
```

**Respuesta del servidor con información de la sala:**
```json
{
  "action": "room_info",
  "lvn": "Nivel de prueba",
  "vh": 30.0,
  "gv": 9.0,
  "mapa": "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 \n..."
}
```

### 5. Mensajes en la Sala
Una vez en la sala, los mensajes se envían con:
```json
{
  "action": "message",
  "content": "Hola a todos en la sala",
  "type": "chat"
}
```

El servidor retransmite el mensaje solo a usuarios de la misma sala:
```json
{
  "user": "alex_",
  "time": "2025-10-16 12:00:00",
  "info": {
    "action": "message",
    "content": "Hola a todos en la sala",
    "type": "chat"
  }
}
```

### 6. Desconexión
Para salir:
```json
{
  "action": "exit"
}
```

## 📁 Estructura de Archivos de Sala

Los archivos de sala están en `/src/levels/` y tienen el formato:
```
lvn-{Nombre del nivel}
vh-{Velocidad horizontal float}
gv-{Gravedad float}
{Mapa como líneas de texto}
```

**Ejemplo (`level00.txt`):**
```
lvn-Nivel de prueba
vh-30.0
gv-9.0
0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
...
```

## ⚙️ Configuración

### Variables de Entorno
Crea un archivo `.env` con:
```properties
SERVER_PORT=2558
DB_HOST=localhost
DB_PORT=3306
DB_NAME=tu_base_datos
DB_USER=tu_usuario
DB_PASSWORD=tu_password
```

### Base de Datos
Tabla `users` requerida:
```sql
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);

-- Usuario de ejemplo
INSERT INTO users (username, password) VALUES ('alex_', '1234');
```

## 📦 Dependencias

* Java 8+
* MySQL 5.7+
* Librerías: `json-20240303.jar`, `mysql-connector-j-9.4.0.jar`

## 🛠️ Compilación y Ejecución

### Compilación:
```bash
javac -cp "lib/json-20240303.jar:lib/mysql-connector-j-9.4.0.jar:." -d build $(find src -name '*.java')
```

### Ejecución del Servidor:
```bash
java -cp "lib/json-20240303.jar:lib/mysql-connector-j-9.4.0.jar:build" server.Server
```

### Cliente de Prueba:
Hay un `TestClient.java` incluido para pruebas:
```bash
javac -cp "lib/json-20240303.jar:lib/mysql-connector-j-9.4.0.jar:." TestClient.java
java -cp "lib/json-20240303.jar:lib/mysql-connector-j-9.4.0.jar:." TestClient
```

## 🔧 Casos Límite y Validaciones

* **Sala no encontrada**: Si el cliente elige una sala inexistente, recibe error y se desconecta.
* **Usuario ya conectado**: No permite múltiples conexiones del mismo usuario.
* **JSON mal formado**: Mensajes inválidos se descartan con error.
* **Cambio de sala**: No implementado; requiere reconexión.
* **Desconexión**: Libera la conexión y notifica a la sala.

## 📝 Notas

- El broadcast es **exclusivo por sala**.
- Las salas se cargan dinámicamente desde archivos `.txt`.
- Compatible con el sistema de autenticación existente.

---
# Servidor de Conexiones con Hilos y Comunicación JSON

Este proyecto corresponde a un servidor multihilo diseñado para manejar múltiples conexiones de clientes mediante sockets con **comunicación basada completamente en JSON**.

Cada **nueva conexión de socket** se asigna a un **hilo independiente**, el cual invoca una clase de conexión que, a su vez, contiene sub-hilos dedicados para **lectura** y **escritura**.

El servidor cuenta con una **cola de mensajes interna** que evita bloqueos en la escritura y facilita un sistema de **broadcast**: cada mensaje enviado por un cliente puede ser retransmitido a todas las conexiones activas en ese momento.

---

## 🚀 Características principales

* Manejo de **conexiones concurrentes** mediante hilos.
* Sub-hilos para **lectura** y **escritura** en cada conexión.
* **Cola de mensajes en el servidor** para evitar bloqueos en la escritura.
* **Broadcast** de mensajes hacia todos los clientes conectados.
* **Comunicación 100% basada en JSON** - todos los mensajes deben ser JSON válido.
* **Autenticación de clientes** con respuesta JSON en caso de fallo.
* **Validación automática** de formato JSON en todos los mensajes entrantes.

---

## 📡 Protocolo de Comunicación

### Autenticación Inicial

El cliente debe enviar como **primer mensaje** un JSON con el formato:

```json
{
  "username": "nickname", 
  "password": "ultrasecret"
}
```

**Respuestas de autenticación:**

* **Éxito**: La conexión se establece y el cliente recibe mensajes de bienvenida en formato JSON.
* **Fallo**: El servidor envía un JSON de error antes de cerrar la conexión:

```json
{
  "user": "server",
  "time": "2025-08-25 20:30:15",
  "info": {
    "status": "authentication_failed",
    "message": "Error: Credenciales inválidas"
  }
}
```

### Mensajes Durante la Sesión

**Todos los mensajes deben ser JSON válido**. Ejemplos de mensajes del cliente:

```json
{
  "action": "message",
  "content": "Hola a todos",
  "type": "chat"
}
```

```json
{
  "action": "exit"
}
```

### Formato de Mensajes del Servidor

Todos los mensajes que envía el servidor tienen el siguiente formato estándar:

```json
{
  "user": "nombre_usuario_o_server",
  "time": "2025-08-25 20:30:15",
  "info": {
    // Contenido del mensaje original del cliente o información del servidor
  }
}
```

**Ejemplos:**

* **Mensaje broadcast de usuario:**
```json
{
  "user": "alex_",
  "time": "2025-08-25 20:30:15",
  "info": {
    "action": "message",
    "content": "Hola a todos",
    "type": "chat"
  }
}
```

* **Mensaje del servidor:**
```json
{
  "user": "server",
  "time": "2025-08-25 20:30:15",
  "info": "Usuarios conectados: 3"
}
```

### Validación de Mensajes

* **Mensajes inválidos**: Si un cliente envía algo que no es JSON válido, recibe un mensaje de error del servidor y el mensaje se descarta.
* **Desconexión**: Para desconectarse, el cliente debe enviar `{"action": "exit"}`.

---

## ⚙️ Configuración del proyecto

### Variables de entorno

El servidor utiliza **MySQL** como sistema de gestión de base de datos y se configura mediante variables de entorno.

1. **Copia el archivo de ejemplo:**
   ```bash
   cp .env.example .env
   ```

2. **Edita el archivo `.env`** con tus datos de configuración:
   ```properties
   # Puerto del servidor
   SERVER_PORT=2558

   # Configuración de la base de datos MySQL
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=tu_nombre_base_datos
   DB_USER=tu_usuario_mysql
   DB_PASSWORD=tu_contraseña_mysql
   ```

### Tabla requerida

Debe existir una tabla llamada **`users`** en tu base de datos con al menos los siguientes campos:

| Campo    | Tipo    | Restricciones        |
| -------- | ------- | -------------------- |
| username | VARCHAR | PRIMARY KEY o UNIQUE |
| password | VARCHAR | NOT NULL             |

**Ejemplo de creación de tabla:**
```sql
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);

-- Insertar usuario de prueba
INSERT INTO users (username, password) VALUES ('admin', 'admin123');
```

Esta configuración es suficiente para **entorno de desarrollo y pruebas** de los clientes.

---

## 📦 Requisitos y Dependencias

* **Java 8+**
* **MySQL 5.7+ / 8.0+**

### Librerías incluidas:
* `mysql-connector-j-9.4.0.jar` - Conector JDBC para MySQL
* `json-20240303.jar` - Biblioteca para manejo de JSON

### Compilación:
```bash
javac -cp "lib/mysql-connector-j-9.4.0.jar:lib/json-20240303.jar" -d build $(find src -name '*.java')
```

### Ejecución:
```bash
java -cp "lib/mysql-connector-j-9.4.0.jar:lib/json-20240303.jar:build" server.Server
```

---

## 🐳 Ejecución con Docker

Para facilitar el despliegue, el proyecto incluye configuración de Docker para ejecutar el servidor y la base de datos MySQL en contenedores.

### Prerrequisitos

* **Docker** y **Docker Compose** instalados en tu sistema.

### Pasos para ejecutar con Docker

1. **Asegúrate de tener Docker Compose:**
   ```bash
   docker --version
   docker-compose --version
   ```

2. **Construye y ejecuta los contenedores:**
   ```bash
   docker-compose up --build
   ```

   Esto iniciará:
   - Un contenedor MySQL con la base de datos `chatdb` y la tabla `users` creada automáticamente.
   - Un contenedor con el servidor Java escuchando en el puerto 2558.

3. **Accede al servidor:**
   - El servidor estará disponible en `localhost:2558`.
   - La base de datos MySQL en `localhost:3307` (usuario: `chatuser`, contraseña: `chatpass`).

4. **Detener los contenedores:**
   ```bash
   docker-compose down
   ```

### Notas sobre Docker

* La base de datos persiste en un volumen Docker llamado `mysql_data`.
* Si necesitas cambiar las credenciales de la base de datos, edita el archivo `docker-compose.yml`.
* El servidor se conecta automáticamente a la base de datos MySQL en el contenedor.

---

## 🚀 Deployment con GitHub Actions

El proyecto incluye un workflow de GitHub Actions para deployment automático al servidor.

### Configuración del Servidor

1. **Instala Docker y Docker Compose en el servidor.**

2. **Clona el repositorio en el servidor:**
   ```bash
   git clone https://github.com/DiegoVanegas03/computo-nube-servidor.git /path/to/your/project
   cd /path/to/your/project
   ```

3. **Configura Nginx como proxy reverso:**
   Crea un archivo de configuración en `/etc/nginx/sites-available/chat-server`:
   ```nginx
   server {
       listen 80;
       server_name your-domain.com;

       location / {
           proxy_pass http://localhost:2558;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```
   Habilita el sitio: `sudo ln -s /etc/nginx/sites-available/chat-server /etc/nginx/sites-enabled/`

4. **Ejecuta el script de deployment inicial:**
   ```bash
   ./deploy.sh
   ```

### Configuración de GitHub Secrets

En tu repositorio de GitHub, ve a Settings > Secrets and variables > Actions y agrega estos secrets:

* `SERVER_HOST`: IP o dominio de tu servidor.
* `SERVER_USER`: Usuario SSH para conectarte al servidor.
* `SERVER_SSH_KEY`: Clave privada SSH para autenticación (genera con `ssh-keygen` y agrega la pública a `~/.ssh/authorized_keys` en el servidor).

### Cómo funciona el Deployment

* Al hacer push a la rama `main`, el workflow:
  1. Construye la imagen Docker.
  2. La sube a GitHub Container Registry.
  3. Se conecta por SSH al servidor.
  4. Ejecuta `git pull` y `docker-compose up -d` para actualizar y reiniciar los servicios.

* Nginx actúa como proxy reverso, redirigiendo las peticiones al puerto 2558 del contenedor.

---

### Estructura del proyecto:
```
computo-nube-servidor/
├── .github/
│   └── workflows/
│       └── deploy.yml      # Workflow de GitHub Actions para deployment
├── src/
│   ├── server/           # Lógica del servidor y conexiones
│   ├── repositories/     # Acceso a datos (UserRepository)
│   ├── sql/             # Configuración de base de datos
│   └── util/            # Utilidades (EnvLoader)
├── lib/                 # Librerías JAR necesarias
├── build/               # Archivos compilados (.class)
├── .env.example         # Plantilla de configuración
├── .env                 # Tu configuración (ignorado por git)
├── Dockerfile           # Configuración para construir la imagen Docker del servidor
├── docker-compose.yml   # Configuración para ejecutar contenedores con Docker Compose
├── init.sql             # Script de inicialización de la base de datos
├── deploy.sh            # Script de deployment para el servidor
└── README.md
```
```
computo-nube-servidor/
├── src/
│   ├── server/           # Lógica del servidor y conexiones
│   ├── repositories/     # Acceso a datos (UserRepository)
│   ├── sql/             # Configuración de base de datos
│   └── util/            # Utilidades (EnvLoader)
├── lib/                 # Librerías JAR necesarias
├── build/               # Archivos compilados (.class)
├── .env.example         # Plantilla de configuración
├── .env                 # Tu configuración (ignorado por git)
├── Dockerfile           # Configuración para construir la imagen Docker del servidor
├── docker-compose.yml   # Configuración para ejecutar contenedores con Docker Compose
├── init.sql             # Script de inicialización de la base de datos
└── README.md
```

---

## 🛠️ Próximas mejoras (Roadmap)

* Implementación de salas de chat (rooms) para separar conversaciones por temas.
* Sistema de roles y permisos de usuario.
* Persistencia de historial de mensajes en base de datos.
* Implementación de heartbeat para detectar conexiones perdidas.
* API REST complementaria para administración del servidor.

---

## 📝 Nota

Este **README** fue construido con el apoyo de **ChatGPT**.


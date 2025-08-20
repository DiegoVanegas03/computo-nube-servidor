# Servidor de Conexiones con Hilos y Autenticación

Este proyecto corresponde a la **primera versión** de un servidor multihilo diseñado para manejar múltiples conexiones de clientes mediante sockets.

Cada **nueva conexión de socket** se asigna a un **hilo independiente**, el cual invoca una clase de conexión que, a su vez, contiene sub-hilos dedicados para **lectura** y **escritura**.

El servidor cuenta con una **cola de mensajes interna** que evita bloqueos en la escritura y facilita un sistema de **broadcast**: cada mensaje enviado por un cliente puede ser retransmitido a todas las conexiones activas en ese momento.

---

## 🚀 Características principales

* Manejo de **conexiones concurrentes** mediante hilos.
* Sub-hilos para **lectura** y **escritura** en cada conexión.
* **Cola de mensajes en el servidor** para evitar bloqueos en la escritura.
* **Broadcast** de mensajes hacia todos los clientes conectados.
* **Autenticación de clientes**:

  * El cliente debe enviar como **primer mensaje** un JSON con el formato:

    ```json
    {"username":"nickname", "password":"ultrasecret"}
    ```
  * Este JSON debe ser convertido a texto y enviado inmediatamente después de establecer la conexión.
  * El servidor transformará nuevamente el texto a JSON y validará las credenciales en la base de datos.

---

## ⚙️ Configuración de la base de datos

El servidor utiliza **MySQL** como sistema de gestión de base de datos.
Para ejecutar el proyecto en tu máquina, debes configurar las siguientes variables dentro del código:

```java
public static final String URL  = "jdbc:mysql://localhost:3306/supercomputo"; // Cambia "supercomputo" por el nombre de tu BD
public static final String USER = "root";                                    // Usuario de la BD
public static final String PSWD = "123456";                                  // Password (si no tiene, dejar como "")
```

### Tabla requerida

Debe existir una tabla llamada **`users`** en tu base de datos con al menos los siguientes campos:

| Campo    | Tipo    | Restricciones        |
| -------- | ------- | -------------------- |
| username | VARCHAR | PRIMARY KEY o UNIQUE |
| password | VARCHAR | NOT NULL             |

Esta configuración es suficiente para **entorno de desarrollo y pruebas** de los clientes.

---

## 📦 Requisitos

* **Java 8+**
* **MySQL 5.7+ / 8.0+**
* Dependencias JDBC configuradas en tu proyecto.

---

## 🛠️ Próximas mejoras (Roadmap)

* Validación previa de mensajes entrantes para verificar si el contenido puede ser parseado como JSON, evitando excepciones innecesarias.

---

## 📝 Nota

Este **README** fue construido con el apoyo de **ChatGPT**.


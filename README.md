# Picopark Server

## Descripción

Picopark Server es un servidor WebSocket desarrollado en Java que implementa un juego de plataformas multijugador cooperativo. Los jugadores deben trabajar juntos para recolectar una llave y alcanzar la meta en niveles diseñados con plataformas móviles y físicas realistas.

El proyecto incluye tanto el servidor como un cliente de ejemplo, y está completamente contenerizado con Docker para facilitar el despliegue.

## Tecnologías Utilizadas

- **Lenguaje**: Java 17
- **WebSockets**: Java-WebSocket library para comunicación en tiempo real
- **Serialización**: Gson para manejo de JSON
- **Base de Datos**: MySQL para autenticación de usuarios
- **Contenerización**: Docker y Docker Compose
- **Build Tool**: Maven

## Arquitectura

### Diagrama de Arquitectura General
El servidor se despliega en un VPS con:
- **Proxy WebSocket** (Nginx en puerto 2558) para manejar conexiones entrantes
- **Contenedor Java**: Servidor WebSocket principal
- **Contenedor MySQL**: Base de datos para usuarios

### Diagrama de Clases
Las clases principales incluyen:
- `GameWebSocketServer`: Servidor principal que maneja conexiones y lógica del juego
- `GameRoom`: Representa una sala de juego con su estado
- `Player`: Jugador con posición, velocidad y estado
- `Platform`: Plataformas móviles que requieren múltiples jugadores
- `Key`: Llave que puede ser robada entre jugadores
- `User`: Usuario autenticado

### Estados del Juego
1. **Esperando Conexiones**: Servidor acepta conexiones
2. **Autenticado**: Usuario inicia sesión
3. **Esperando Sala**: Usuario elige sala
4. **Sala Llena**: Suficientes jugadores conectados
5. **Juego Iniciado**: Comienza el game loop
6. **Jugando**: Físicas y lógica activa
7. **Victoria**: Todos completan la meta

## Mecánicas del Juego

### Física y Controles
- **Gravedad**: 0.5 unidades por frame
- **Salto**: Fuerza de -10 unidades
- **Movimiento**: Velocidad de 4.5 unidades
- **Tick Rate**: 60 FPS
- **Tamaño de tiles**: 48 píxeles (16 originales × 3 escala)
- **Tamaño de jugador**: 32×48 píxeles
- **Tamaño de llave**: 16×16 píxeles

### Elementos del Juego

#### Tiles del Mapa
- **Tiles Sólidos**: IDs 3, 4, 5 (suelo, paredes)
- **Meta/Victoria**: Tiles 12, 13, 14
- **Plataformas Móviles**: 
  - Orígenes: 31-39 (cada número requiere esa cantidad de jugadores - 30)
  - Destinos: 30
- **Llave**: Tile 50 (solo una por nivel)

#### Sistema de Plataformas Móviles
- **Activación**: Requieren múltiples jugadores parados encima
- **Tipo 31**: 1 jugador necesario
- **Tipo 32**: 2 jugadores necesarios
- **Tipo 39**: 9 jugadores necesarios
- **Movimiento**: 
  - Duración: 1.5 segundos
  - Delay de activación: 0.5 segundos
  - Easing cúbico para movimiento suave
  - Se resetean automáticamente a posición original
- **Lógica recursiva**: Cuenta jugadores encima de otros (pilas humanas)

#### Sistema de Llaves
- **Recolección**: Colisión con tile 50
- **Traslado**: Sigue al jugador con animación flotante (sin/cos)
- **Robo**: Jugadores pueden quitar la llave tocando al portador (margen de 20 píxeles)
- **Movimiento suave**: 
  - Y instantáneo
  - X con retraso (15% de suavizado)
  - Transición de 200ms entre jugadores
- **Victoria**: Llevar la llave a tiles de meta (12-14) abre la puerta

#### Multijugador Cooperativo
- **Sala de espera**: 20×20 tiles, diseño fijo
- **Mundo del juego**: 20×50 tiles, diseño por nivel
- **Inicio automático**: Cuando se alcanza el número requerido de jugadores
- **Victoria**: Todos los jugadores deben completar la meta
- **Reinicio**: Posible volver a sala de espera

### Lógica del Servidor

#### Game Loop
- **Frecuencia**: 60 ticks por segundo
- **Actualizaciones**:
  1. Posiciones de plataformas
  2. Movimiento de jugadores con plataformas
  3. Física de jugadores (gravedad, colisiones)
  4. Sistema de llaves (animación, robo, victoria)
  5. Verificación de victoria
  6. Envío de estado a clientes

#### Autenticación
- **Actual**: Hardcoded (siempre true para pruebas)
- **Futuro**: Base de datos MySQL con usuarios
- **Sesión**: UUID por conexión

#### Manejo de Conexiones
- **Mapa de usuarios**: ConcurrentHashMap para thread-safety
- **Mapa de salas**: Inicialización automática desde /maps/
- **Desconexión**: Limpieza automática de salas y usuarios

### Salas y Mapas
Los niveles se definen en archivos JSON en `/maps/`:
- `waiting-room`: Sala de espera 20x20
- `world`: Nivel principal 20x50
- Configuración incluye nombre y número mínimo de jugadores

## Instalación y Ejecución

### Prerrequisitos
- Docker y Docker Compose
- Puerto 2558 disponible (servidor)
- Puerto 3307 disponible (MySQL, mapeado desde 3306 interno)

### Despliegue con Docker

1. **Clonar el repositorio**:
   ```bash
   git clone https://github.com/DiegoVanegas03/computo-nube-servidor.git
   cd computo-nube-servidor
   ```

2. **Construir y ejecutar**:
   ```bash
   docker compose up --build
   ```

3. **O usar el script de despliegue**:
   ```bash
   chmod +x start_service.sh
   ./start_service.sh
   ```

### Variables de Entorno
- `SERVER_PORT`: Puerto del servidor (default: 2558)
- `DB_HOST`: Host de la base de datos (default: mysql)
- `DB_PORT`: Puerto de la base de datos (default: 3306)
- `DB_NAME`: Nombre de la base de datos (default: chatdb)
- `DB_USER`: Usuario de la base de datos (default: chatuser)
- `DB_PASSWORD`: Contraseña de la base de datos (default: chatpass)

## Estructura del Proyecto

```
├── src/main/java/org/
│   ├── server/
│   │   ├── Main.java                    # Punto de entrada - inicia servidor WebSocket
│   │   ├── GameWebSocketServer.java     # Servidor principal - maneja conexiones, game loop, mensajes
│   │   ├── GameRoom.java               # Lógica de salas - estado del juego, inicialización de entidades
│   │   ├── Player.java                 # Entidad jugador - posición, estado, serialización
│   │   ├── Platform.java               # Plataformas móviles - movimiento, activación por jugadores
│   │   ├── Key.java                    # Sistema de llaves - recolección, robo, animación
│   │   ├── User.java                   # Usuario autenticado - ID, username, conexión
│   │   └── RoomConfig.java             # Configuración de mapas - parseo JSON con Gson
│   └── client/
│       └── GameWebSocketClient.java    # Cliente Swing - GUI, manejo de mensajes, envío de comandos
├── maps/                               # Archivos de mapas JSON
│   ├── facil.json                      # Nivel simple (2 jugadores)
│   └── practica.json                   # Nivel de práctica (2 jugadores)
├── lib/                                # Librerías externas (si no usa Maven central)
├── pom.xml                             # Configuración Maven - dependencias, build
├── Dockerfile                          # Contenerización - imagen Maven para build y run
├── docker-compose.yml                  # Orquestación - servicios MySQL + servidor
├── init.sql                            # Inicialización BD - usuarios de prueba
├── start_service.sh                    # Script despliegue - git pull, rebuild, restart
└── diagrama_*.mmd                      # Diagramas Mermaid - arquitectura, clases, estados
```

### Arquitectura del Código

#### GameWebSocketServer
- **Herencia**: Extiende `WebSocketServer` de Java-WebSocket
- **Estado global**: 
  - `users`: Mapa de usuarios conectados
  - `rooms`: Mapa de salas activas
  - `scheduler`: Executor para game loop
- **Métodos principales**:
  - `onOpen/onClose/onMessage`: Callbacks WebSocket
  - `startGameLoop`: Inicia actualización a 60 FPS
  - `updateGame`: Lógica principal del game loop
  - `handleAuth/handleJoinRoom/handleMove/handleJump`: Procesamiento de mensajes

#### GameRoom
- **Estado dual**: `waitingRoom` (espera) y `gameWorld` (juego)
- **Inicialización**:
  - `initializePlatforms`: Escanea mapa para crear entidades Platform
  - `initializeKey`: Busca tile 50 para crear Key
- **Actualización**: Posiciones de plataformas, movimiento de jugadores con ellas

#### Platform
- **Activación**: `requiredPlayers = type - 30`
- **Movimiento**: Interpolación cúbica, duración configurable
- **Reset**: Vuelve a posición original tras movimiento

#### Key
- **Estados**: En mapa, recolectada, siendo robada, abriendo puerta
- **Animación**: Flotante sinusoidal, movimiento suave con retraso
- **Colisión**: Detección de robo y puerta de victoria

## Base de Datos

### Esquema
```sql
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);
```

### Usuarios de Prueba
La base de datos se inicializa con varios usuarios de prueba (matrículas) y un usuario admin.

## API WebSocket

### Protocolo de Mensajes

Todos los mensajes siguen el formato JSON:
```json
{
  "type": "tipo_mensaje",
  "data": { ... }
}
```

### Mensajes del Cliente al Servidor

#### Autenticación
```json
{
  "type": "auth",
  "data": {
    "username": "string",
    "password": "string"
  }
}
```

#### Unirse a Sala
```json
{
  "type": "joinRoom",
  "data": {
    "roomId": "string"
  }
}
```

#### Movimiento
```json
{
  "type": "move",
  "data": {
    "direction": "left|right|stop"
  }
}
```

#### Salto
```json
{
  "type": "jump",
  "data": {}
}
```

#### Chat
```json
{
  "type": "chat",
  "data": {
    "message": "string"
  }
}
```

#### Salir de Sala
```json
{
  "type": "leaveRoom",
  "data": {}
}
```

### Mensajes del Servidor al Cliente

#### Autenticación Exitosa
```json
{
  "type": "authSuccess",
  "data": {
    "userId": "uuid",
    "username": "string",
    "rooms": [
      {
        "id": "string",
        "name": "string",
        "players": "number",
        "maxPlayers": "number"
      }
    ]
  }
}
```

#### Autenticación Fallida
```json
{
  "type": "authFailed",
  "data": {
    "reason": "string"
  }
}
```

#### Unido a Sala
```json
{
  "type": "roomJoined",
  "data": {
    "roomId": "string",
    "roomName": "string",
    "isGameStarted": "boolean",
    "players": [...]
  }
}
```

#### Actualización de Juego
```json
{
  "type": "gameUpdate",
  "data": {
    "players": [
      {
        "id": "string",
        "username": "string",
        "x": "number",
        "y": "number",
        "direction": "string",
        "isVisible": "boolean",
        "hasKey": "boolean"
      }
    ],
    "platforms": [
      {
        "id": "string",
        "x": "number",
        "y": "number",
        "width": "number",
        "height": "number",
        "type": "number",
        "direction": "number",
        "isMoving": "boolean",
        "playersOnPlatform": "number",
        "requiredPlayers": "number",
        "playersNeeded": "number"
      }
    ],
    "key": {
      "x": "number",
      "y": "number",
      "isCollected": "boolean",
      "carriedByPlayerId": "string|null",
      "floatOffset": "number",
      "isOpeningDoor": "boolean"
    } | null
  }
}
```

#### Jugador se Unió
```json
{
  "type": "playerJoined",
  "data": {
    "player": { ... }
  }
}
```

#### Jugador se Fue
```json
{
  "type": "playerLeft",
  "data": {
    "playerId": "string"
  }
}
```

#### Mensaje de Chat
```json
{
  "type": "chat",
  "data": {
    "username": "string",
    "message": "string"
  }
}
```

#### Error
```json
{
  "type": "error",
  "data": {
    "message": "string"
  }
}
```

## Desarrollo

### Compilación Local
```bash
mvn clean compile
```

### Ejecución Local
```bash
mvn exec:java
```

### Agregar Nuevos Mapas
1. Crear archivo JSON en `/maps/` siguiendo el formato existente
2. El servidor lo detectará automáticamente al reiniciar

### Cliente de Prueba
El proyecto incluye un cliente Swing básico (`GameWebSocketClient`) para testing:
```bash
# Compilar y ejecutar cliente
javac -cp "$(mvn dependency:build-classpath):src/main/java" src/main/java/org/client/GameWebSocketClient.java
java -cp "$(mvn dependency:build-classpath):src/main/java" org.client.GameWebSocketClient
```

### Debugging
- **Logs del servidor**: Salida estándar con mensajes de conexión, autenticación y eventos del juego
- **Estado del juego**: Los clientes reciben actualizaciones completas cada frame
- **Colisiones**: Sistema de tiles para detección de colisiones suelo/techo

## Limitaciones y Mejoras Futuras

### Autenticación
- **Actual**: Hardcoded (siempre acepta)
- **Mejora**: Integrar completamente con MySQL, hash de contraseñas (BCrypt)

### Rendimiento
- **Actual**: Game loop único para todas las salas
- **Mejora**: Game loops por sala, optimización de colisiones

### Características del Juego
- **Falta**: Respawn de jugadores caídos
- **Falta**: Temporizadores por nivel
- **Falta**: Sistema de puntuaciones
- **Falta**: Más tipos de plataformas/interacciones

### Red
- **Actual**: WebSocket simple
- **Mejora**: Compresión de mensajes, rate limiting, reconexión automática

### Cliente
- **Actual**: Swing básico
- **Mejora**: Cliente web (HTML5 Canvas), móvil (Android/iOS)

### Despliegue
- **Mejora**: Kubernetes, balanceo de carga, monitoreo (Prometheus)

## Diagramas y Documentación

### Diagrama de Arquitectura (`diagrama_arquitectura.mmd`)
Muestra el flujo de datos desde el cliente hasta la base de datos:
- Cliente Java/Swing → WebSocket Proxy (Nginx) → Servidor Java → MySQL
- Contenedores Docker orquestados con Docker Compose
- Montaje de mapas en tiempo de ejecución

### Diagrama de Clases (`diagrama_clases.mmd`)
Relaciones entre componentes principales:
- `GameWebSocketServer` gestiona `GameRoom`s y `User`s
- `GameRoom` contiene `Player`s, `Platform`s y `Key`
- Sistema de dependencias para físicas y colisiones

### Diagrama de Estados (`diagrama_estados.mmd`)
Flujo del proceso de juego:
1. **Esperando Conexiones** → Autenticación → **Esperando Sala**
2. **Sala Llena** → **Juego Iniciado** → **Jugando**
3. **Victoria** o **Reinicio** → vuelta a **Esperando Sala**

### Flujo Típico de una Partida
1. **Conexión**: Múltiples clientes se conectan y autentican
2. **Espera**: Jugadores eligen sala y esperan suficientes participantes
3. **Inicio**: Servidor cambia a modo juego, inicializa entidades
4. **Gameplay**: 
   - Jugadores se mueven y saltan
   - Activación de plataformas cooperativas
   - Recolección y robo de llave
   - Colaboración para alcanzar meta
5. **Victoria**: Todos completan el nivel
6. **Reinicio**: Posibilidad de jugar otra partida

## Licencia

Este proyecto es parte de un trabajo académico para la Universidad Autónoma de San Luis Potosí.
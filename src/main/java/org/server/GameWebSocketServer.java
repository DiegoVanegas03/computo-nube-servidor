package org.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameWebSocketServer extends WebSocketServer {

    private final Gson gson = new Gson();
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<WebSocket, String> connectionToUserId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final float GRAVITY = 0.5f;
    private static final float JUMP_FORCE = -10f;

    private static final float MOVE_SPEED = 4.5f;
    private static final int GAME_TICK_RATE = 60; // 60 FPS

    private static final int ORIGINAL_SIZE_TILE = 16;
    private static final int SCALE = 3;
    public static final int SIZE_TILE = ORIGINAL_SIZE_TILE * SCALE; // 48 pixels

    public static final Set<Integer> SOLID_TILES = Set.of(3, 4, 5); // Solo tiles sólidos. 30 y 40 son marcadores (no sólidos), 31-39 son entidades

    public static final Set<Integer> WINNER_TILES = Set.of(12,13,14);

    public static final Set<Integer> MOVING_PLATFORM_ORIGINS = Set.of(31, 32, 33, 34, 35, 36, 37, 38, 39);

    public static final int PLATFORM_DESTINATION = 30;

    public GameWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        initializeRooms();
        startGameLoop();
    }

    private void initializeRooms() {
        File levelsDir = new File("maps");
        String[] salas = levelsDir.list((dir, name) -> name.endsWith(".json"));
        if (salas == null) return;

        for (String sala : salas) {
            File mapFile = new File(levelsDir, sala);
            RoomConfig config = GameRoom.loadRoomConfig(mapFile.getAbsolutePath());
            String roomId = sala.replace(".json", "");
            rooms.put(roomId, new GameRoom(
                    roomId,
                    config.getRoomName(),   // ← ahora le pones el nombre del txt
                    config.getUsersToStart(),
                    config.getWorld(),        // ← y su matriz del mapa
                    config.getWaitingRoom()
            ));
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Nueva conexión: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        this.handleLeaveRoom(conn);
        connectionToUserId.remove(conn);
        System.out.println("Conexión cerrada: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {

            System.out.println("Mensaje recibido: " + message);
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();

            JsonObject data = json.getAsJsonObject("data");

            switch (type) {
                case "auth":
                    handleAuth(conn, data);
                    break;
                case "joinRoom":
                    handleJoinRoom(conn, data);
                    break;
                case "leaveRoom":
                    handleLeaveRoom(conn);
                    break;
                case "move":
                    handleMove(conn, data);
                    break;
                case "jump":
                    handleJump(conn);
                    break;
                case "chat":
                    handleChat(conn, data);
                    break;
                default:
                    sendError(conn, "Tipo de mensaje desconocido");
            }
        } catch (Exception e) {
            sendError(conn, "Error procesando mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleLeaveRoom(WebSocket conn) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "No autenticado");
            return;
        }
        User user = users.get(userId);
        GameRoom oldRoom = rooms.get(user.currentRoom);
        if (oldRoom != null) {
            oldRoom.removePlayer(userId);
            broadcastToRoom(user.currentRoom, createMessage("playerLeft", Map.of(
                    "userId", userId,
                    "username", user.username
            )));

            oldRoom.completedPlayers = 0;

            if(oldRoom.players.isEmpty())
                oldRoom.world = oldRoom.waitingRoom;

            if(!oldRoom.players.isEmpty() && oldRoom.players.size() < oldRoom.needUsers)
                this.backToWaitingRoom(oldRoom);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Error: " + ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket iniciado en puerto " + getPort());
        System.out.println("Esperando conexiones...");
    }

    private void handleAuth(WebSocket conn, JsonObject data) {
        String username = data.get("username").getAsString();
        String password = data.get("password").getAsString();

        // Autenticación simple (en producción usar hash y base de datos)
        if (authenticateUser(username, password)) {
            String userId = UUID.randomUUID().toString();
            User user = new User(userId, username, conn);
            users.put(userId, user);
            connectionToUserId.put(conn, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("username", username);
            response.put("rooms", getRoomsList());

            sendToClient(conn, createMessage("authSuccess", response));
            System.out.println("Usuario autenticado: " + username);
        } else {
            sendToClient(conn, createMessage("authFailed", Map.of("reason", "Credenciales inválidas")));
        }
    }

    private boolean authenticateUser(String username, String password) {
        // Autenticación simple de ejemplo
        // En producción: verificar contra base de datos con hash bcrypt
        return username.length() >= 3 && password.length() >= 4;
    }

    private void handleJoinRoom(WebSocket conn, JsonObject data) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) {
            sendError(conn, "No autenticado");
            return;
        }

        User user = users.get(userId);
        String roomId = data.get("roomId").getAsString();
        GameRoom room = rooms.get(roomId);

        if (room == null) {
            sendError(conn, "Sala no encontrada");
            return;
        }

        // Salir de la sala anterior si existe
        if (user.currentRoom != null) {
            GameRoom oldRoom = rooms.get(user.currentRoom);
            if (oldRoom != null) {
                oldRoom.removePlayer(userId);
                broadcastToRoom(user.currentRoom, createMessage("playerLeft", Map.of(
                        "userId", userId,
                        "username", user.username
                )));
            }
        }

        // Unirse a la nueva sala
        user.currentRoom = roomId;
        Player player = new Player(userId, user.username);
        room.addPlayer(player);

        // Enviar estado actual de la sala al jugador
        sendToClient(conn, createMessage("roomJoined", Map.of(
                "roomId", roomId,
                "roomName", room.name,
                "players", room.getPlayersData(),
                "world" , room.world
        )));

        // Notificar a otros jugadores
        broadcastToRoomExcept(roomId, userId, createMessage("playerJoined", Map.of(
                "userId", userId,
                "username", user.username,
                "player", player.toMap()
        )));

        if(room.players.size() >= room.needUsers)
            this.startGame(room);
    }

    private void resetPlayers(GameRoom room, int offsetX) {
        int index = 0;
        for (Player player : room.players.values()) {
            player.x = offsetX + 20 + (index * 100);
            player.y = 20;
            index++;
        }
    }

    private void backToWaitingRoom(GameRoom room) {
        scheduler.schedule(() -> {
            room.world = room.waitingRoom;
            resetPlayers(room,200);

            broadcastToRoom(
                    room.id,
                    createMessage("startGame", Map.of("world", room.world))
            );
        }, 3, TimeUnit.SECONDS);
    }

    private void startGame(GameRoom room) {
        scheduler.schedule(() -> {
            room.world = new int[room.gameWorld.length][];
            for (int i = 0; i < room.gameWorld.length; i++) {
                room.world[i] = room.gameWorld[i].clone();
            }
            
            // IMPORTANTE: Solo inicializar plataformas cuando el juego REALMENTE comienza
            room.platforms.clear();
            room.initializePlatforms();
            
            resetPlayers(room,0);

            broadcastToRoom(
                    room.id,
                    createMessage("startGame", Map.of("world", room.world))
            );
        }, 3, TimeUnit.SECONDS);
    }

    private void restartGame(GameRoom room) {
        scheduler.schedule(() -> {
            resetPlayers(room,0);

            broadcastToRoom(
                    room.id,
                    createMessage("restartGame", Map.of())
            );
            room.canUpdate = true;
        }, 3, TimeUnit.SECONDS);
    }

    private void handleMove(WebSocket conn, JsonObject data) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) return;

        User user = users.get(userId);
        if (user.currentRoom == null) return;

        GameRoom room = rooms.get(user.currentRoom);
        Player player = room.getPlayer(userId);

        if (player != null) {
            String direction = data.get("direction").getAsString();
            player.direction = direction;
            player.moveDirection = direction.equals("left") ? -1 : (direction.equals("right") ? 1 : 0);
        }
    }

    private void handleJump(WebSocket conn) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) return;

        User user = users.get(userId);
        if (user.currentRoom == null) return;

        GameRoom room = rooms.get(user.currentRoom);
        Player player = room.getPlayer(userId);

        if (player != null) {
            System.out.println("Intento de salto - Usuario: " + user.username +
                    ", isOnGround: " + player.isOnGround +
                    ", playersOnTop: " + player.playersOnTop.size());

            // Solo puede saltar si está en el suelo Y no tiene a nadie encima
            if (player.isOnGround && player.playersOnTop.isEmpty()) {
                player.velocityY = JUMP_FORCE;
                player.isOnGround = false;
                System.out.println("✓ Salto permitido para: " + user.username);
            } else {
                if (!player.isOnGround) {
                    System.out.println("✗ Salto bloqueado: No está en el suelo");
                }
                if (!player.playersOnTop.isEmpty()) {
                    System.out.println("✗ Salto bloqueado: Tiene " + player.playersOnTop.size() + " jugador(es) encima");
                }
            }
        }
    }

    private void handleChat(WebSocket conn, JsonObject data) {
        String userId = connectionToUserId.get(conn);
        if (userId == null) return;

        User user = users.get(userId);
        String message = data.get("message").getAsString();

        if (user.currentRoom != null) {
            broadcastToRoom(user.currentRoom, createMessage("chat", Map.of(
                    "userId", userId,
                    "username", user.username,
                    "message", message
            )));
        }
    }

    private void startGameLoop() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateGame();
            }
        }, 0, 1000 / GAME_TICK_RATE);
    }
    private void updateGame() {
        for (GameRoom room : rooms.values()) {
            if (room.players.isEmpty() || !room.canUpdate) continue;

            // Primero, limpiar la lista de jugadores encima de cada uno
            for (Player player : room.players.values()) {
                if(!player.isVisible) continue;
                player.playersOnTop.clear();
            }

            // Guardar posiciones X anteriores para calcular delta de movimiento
            Map<String, Float> previousX = new HashMap<>();
            for (Player player : room.players.values()) {
                if(!player.isVisible) continue;
                previousX.put(player.id, player.x);
            }

            for (Player player : room.players.values()) {
                if(!player.isVisible) continue;
                // ========== MOVIMIENTO HORIZONTAL ==========
                float oldX = player.x;
                player.x += player.moveDirection * MOVE_SPEED;

                // Verificar colisión horizontal con tiles
                if (checkCollisionHorizontal(player, room.world)) {
                    player.x -= player.moveDirection * MOVE_SPEED; // Revertir
                }

                // Verificar colisión horizontal con otros jugadores
                Player collidedPlayer = checkPlayerCollisionHorizontal(player, room);
                if (collidedPlayer != null) {
                    player.x = oldX; // Revertir movimiento
                }

                // Verificar colisión horizontal con plataformas
                Platform collidedPlatform = checkPlatformCollisionHorizontal(player, room);
                if (collidedPlatform != null) {
                    player.x = oldX; // Revertir movimiento
                }

                // Limitar a los límites del mundo
                player.x = Math.max(0, Math.min(room.world[0].length * SIZE_TILE - player.width, player.x));

                // ========== MOVIMIENTO VERTICAL ==========
                // Aplicar gravedad
                player.velocityY += GRAVITY;

                // Limitar velocidad máxima de caída
                if (player.velocityY > 15) {
                    player.velocityY = 15;
                }

                player.y += player.velocityY;

                // Verificar colisión hacia abajo (suelo)
                if (player.velocityY > 0) {
                    boolean tileCollision = checkCollisionDown(player, room.world, room);
                    Player playerBelow = checkPlayerCollisionDown(player, room);
                    Platform platformBelow = checkPlatformCollisionDown(player, room);

                    if (tileCollision || playerBelow != null || platformBelow != null) {
                        // Ajustar posición
                        int bottomPixel = (int)(player.y + player.height);
                        int tileY = bottomPixel / SIZE_TILE;

                        if (playerBelow != null) {
                            // Ajustar encima del otro jugador
                            player.y = playerBelow.y - player.height;
                            // Registrar que este jugador está encima del otro
                            playerBelow.playersOnTop.add(player.id);
                        } else if (platformBelow != null) {
                            // Ajustar encima de la plataforma
                            player.y = platformBelow.y - player.height;
                        } else {
                            // Ajustar encima del tile
                            player.y = (tileY * SIZE_TILE) - player.height;
                        }

                        player.velocityY = 0;
                        player.isOnGround = true;
                    } else {
                        player.isOnGround = false;
                    }
                }
                // Verificar colisión hacia arriba (techo)
                else if (player.velocityY < 0 && checkCollisionUp(player, room.world)) {
                    int topPixel = (int)player.y;
                    int tileY = (topPixel / SIZE_TILE) + 1;
                    player.y = tileY * SIZE_TILE;

                    player.velocityY = 0;
                    player.isOnGround = false;
                }
                else {
                    // No hay colisión, está en el aire
                    player.isOnGround = false;
                }

                // ========== VERIFICAR TILES DE VICTORIA ==========
                checkWinnerTiles(player, room);
            }

            // DESPUÉS de actualizar todas las posiciones, mover jugadores encima
            for (Player player : room.players.values()) {
                if (!player.playersOnTop.isEmpty()) {
                    // Calcular cuánto se movió realmente este jugador (delta real, no intención)
                    float deltaX = player.x - previousX.get(player.id);
                    
                    // Este jugador tiene gente encima, moverlos
                    for (String playerOnTopId : player.playersOnTop) {
                        Player playerOnTop = room.getPlayer(playerOnTopId);
                        if (playerOnTop != null) {
                            // Mantener encima (ajustar Y si es necesario)
                            playerOnTop.y = player.y - playerOnTop.height;

                            // Arrastrar horizontalmente: usar el DELTA REAL de movimiento
                            playerOnTop.x += deltaX;

                            // Limitar para que no salga del mundo
                            playerOnTop.x = Math.max(0, Math.min(room.world[0].length * SIZE_TILE - playerOnTop.width, playerOnTop.x));
                        }
                    }
                }
            }


            // DESPUÉS de resolver colisiones: Actualizar posiciones de plataformas y mover jugadores con ellas
            room.updatePlatformPositions();

            room.updatePlatformLogic();

            // Enviar actualización a todos los jugadores en la sala
            broadcastToRoom(room.id, createMessage("gameUpdate", Map.of(
                    "players", room.getPlayersData(),
                    "platforms", room.getPlatformsData()
            )));
        }
    }

    /**
     Verifica si el jugador está atravesando tiles de victoria
     **/
    private void checkWinnerTiles(Player player, GameRoom room) {
        if(!player.isVisible) return;
        // Calcular los tiles que ocupa el jugador
        int leftTile = (int)(player.x / SIZE_TILE);
        int rightTile = (int)((player.x + player.width - 1) / SIZE_TILE);
        int topTile = (int)(player.y / SIZE_TILE);
        int bottomTile = (int)((player.y + player.height - 1) / SIZE_TILE);

        // Verificar límites
        if (leftTile < 0 || rightTile >= room.world[0].length ||
                topTile < 0 || bottomTile >= room.world.length) {
            return;
        }

        // Verificar cada tile que ocupa el jugador
        for (int y = topTile; y <= bottomTile; y++) {
            for (int x = leftTile; x <= rightTile; x++) {
                if (WINNER_TILES.contains(room.world[y][x])) {
                    player.isVisible = false;
                    room.completedPlayers++;
                    if (room.completedPlayers >= room.players.size()) {
                        broadcastToRoom(room.id, createMessage("gameWin", Map.of()));
                    }
                    return;
                }
            }
        }
    }

    /**
     * Verifica colisión horizontal con plataformas
     */
    private Platform checkPlatformCollisionHorizontal(Player player, GameRoom room) {
        for (Platform platform : room.platforms.values()) {
            // Verificar si los bounding boxes se superponen
            if (player.x < platform.x + platform.width &&
                    player.x + player.width > platform.x &&
                    player.y < platform.y + platform.height &&
                    player.y + player.height > platform.y) {
                return platform;
            }
        }
        return null;
    }

    /**
     * Verifica colisión horizontal con otros jugadores
     */
    private Player checkPlayerCollisionHorizontal(Player player, GameRoom room) {
        for (Player other : room.players.values()) {
            if (other.id.equals(player.id) || !other.isVisible) continue;

            // Verificar si los bounding boxes se superponen
            if (player.x < other.x + other.width &&
                    player.x + player.width > other.x &&
                    player.y < other.y + other.height &&
                    player.y + player.height > other.y) {
                return other;
            }
        }
        return null;
    }

    /**
     * Verifica colisión vertical con plataformas (cuando cae encima)
     * Detecta basado en la posición ACTUAL real de la plataforma
     */
    private Platform checkPlatformCollisionDown(Player player, GameRoom room) {
        Platform closestPlatform = null;
        float closestDistance = Float.MAX_VALUE;
        
        for (Platform platform : room.platforms.values()) {
            float playerBottom = player.y + player.height;
            float platformTop = platform.y;

            // Verificar si hay superposición horizontal
            boolean horizontalOverlap = player.x + 2 < platform.x + platform.width &&
                    player.x + player.width - 2 > platform.x;

            // Verificar si el jugador está cerca verticalmente
            // Solo si viene desde arriba o está justo encima
            if (horizontalOverlap && playerBottom >= platformTop && playerBottom <= platformTop + 20) {
                float distance = Math.abs(playerBottom - platformTop);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlatform = platform;
                }
            }
        }
        return closestPlatform;
    }

    /**
     * Verifica colisión vertical con otros jugadores (cuando cae encima)
     */
    private Player checkPlayerCollisionDown(Player player, GameRoom room) {
        for (Player other : room.players.values()) {
            if (other.id.equals(player.id)) continue;

            // Verificar si está cayendo encima de otro jugador
            float playerBottom = player.y + player.height;
            float otherTop = other.y;

            // Verificar superposición horizontal (debe estar bien alineado)
            boolean horizontalOverlap = player.x + 2 < other.x + other.width &&
                    player.x + player.width - 2 > other.x;

            // Verificar si está justo encima (margen más amplio para detectar mejor)
            boolean verticalNear = playerBottom >= otherTop - 5 &&
                    playerBottom <= otherTop + 15;

            if (horizontalOverlap && verticalNear && player.velocityY >= 0) {
                return other;
            }
        }
        return null;
    }

    private boolean checkCollisionHorizontal(Player player, int[][] world) {
        int topTile = (int)(player.y / SIZE_TILE);
        int bottomTile = (int)((player.y + player.height - 1) / SIZE_TILE);
        int leftTile = (int)(player.x / SIZE_TILE);
        int rightTile = (int)((player.x + player.width - 1) / SIZE_TILE);

        // Verificar límites
        if (leftTile < 0 || rightTile >= world[0].length) {
            return true;
        }
        if (topTile < 0 || bottomTile >= world.length) {
            return false;
        }

        // Verificar tiles en los bordes izquierdo y derecho del jugador
        for (int y = topTile; y <= bottomTile; y++) {
            if (SOLID_TILES.contains(world[y][leftTile]) ||
                    SOLID_TILES.contains(world[y][rightTile])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica colisión hacia abajo (con el suelo)
     */
    private boolean checkCollisionDown(Player player, int[][] world, GameRoom room) {
        // Calcular el pixel exacto de la parte inferior del jugador
        int bottomPixel = (int)(player.y + player.height);
        int bottomTile = bottomPixel / SIZE_TILE;

        int leftTile = (int)(player.x + 1) / SIZE_TILE; // +1 para evitar esquinas
        int rightTile = (int)((player.x + player.width - 2) / SIZE_TILE); // -2 para evitar esquinas

        // Verificar límites
        if (bottomTile >= world.length) {
            player.velocityY = 0;
            player.isOnGround = false;
            room.canUpdate = false;
            this.broadcastToRoom(room.id,
                    createMessage("gameOver", Map.of("userName",player.username)));
            this.restartGame(room);
            return false; // No hay colisión porque lo relocalizamos
        }
        if (leftTile < 0 || rightTile >= world[0].length) {
            return false;
        }

        // Verificar tiles en la parte inferior del jugador
        for (int x = leftTile; x <= rightTile; x++) {
            if (SOLID_TILES.contains(world[bottomTile][x])) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica colisión hacia arriba (con el techo)
     */
    private boolean checkCollisionUp(Player player, int[][] world) {
        int topTile = (int)(player.y / SIZE_TILE);
        int leftTile = (int)(player.x / SIZE_TILE);
        int rightTile = (int)((player.x + player.width - 1) / SIZE_TILE);

        // Verificar límites
        if (topTile < 0) {
            return true;
        }
        if (leftTile < 0 || rightTile >= world[0].length) {
            return false;
        }

        // Verificar tiles en la parte superior del jugador
        for (int x = leftTile; x <= rightTile; x++) {
            if (SOLID_TILES.contains(world[topTile][x])) {
                return true;
            }
        }

        return false;
    }

    private List<Map<String, String>> getRoomsList() {
        List<Map<String, String>> roomsList = new ArrayList<>();
        for (GameRoom room : rooms.values()) {
            roomsList.add(Map.of(
                    "id", room.id,
                    "name", room.name,
                    "players", String.valueOf(room.players.size())
            ));
        }
        return roomsList;
    }

    private void broadcastToRoom(String roomId, String message) {
        GameRoom room = rooms.get(roomId);
        if (room != null) {
            for (String userId : room.players.keySet()) {
                User user = users.get(userId);
                if (user != null) {
                    sendToClient(user.connection, message);
                }
            }
        }
    }

    private void broadcastToRoomExcept(String roomId, String exceptUserId, String message) {
        GameRoom room = rooms.get(roomId);
        if (room != null) {
            for (String userId : room.players.keySet()) {
                if (!userId.equals(exceptUserId)) {
                    User user = users.get(userId);
                    if (user != null) {
                        sendToClient(user.connection, message);
                    }
                }
            }
        }
    }

    private void sendToClient(WebSocket conn, String message) {
        if (conn.isOpen()) {
            conn.send(message);
        }
    }

    private void sendError(WebSocket conn, String error) {
        sendToClient(conn, createMessage("error", Map.of("message", error)));
    }

    private String createMessage(String type, Map<String, ?> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());
        return gson.toJson(message);
    }
}

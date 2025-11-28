package org.server;

import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

public class GameRoom {
    public boolean canUpdate = true;
    public boolean gameStarted = false;
    public String id;
    public String name;
    public int[][] world;
    public int[][] gameWorld;
    public int[][] waitingRoom;
    public int[][] originalGameWorld; // Para resetear plataformas
    public Map<String, Player> players = new ConcurrentHashMap<>();
    public Map<String, Platform> platforms = new ConcurrentHashMap<>(); // Plataformas móviles como entidades

    public Key key = null; // Llave del nivel (puede ser null si no hay)
    public boolean doorOpen = false; // Estado de la puerta (true = abierta)

    public int completedPlayers = 0;

    public int needUsers;

    GameRoom(String id, String name, int needUsers, int[][] world, int[][] waitingRoom) {
        this.id = id;
        this.name = name;
        this.world = waitingRoom;
        this.gameWorld = deepCopyWorld(world);
        this.originalGameWorld = deepCopyWorld(world);
        this.waitingRoom = waitingRoom;
        this.needUsers = needUsers;
        // Las plataformas se inicializarán cuando el juego comience realmente
    }

    public void initializeKey() {
        // Escanear el mapa para encontrar la llave (tile 50)
        for (int y = 0; y < gameWorld.length; y++) {
            for (int x = 0; x < gameWorld[y].length; x++) {
                if (gameWorld[y][x] == 50) { // Tile de llave
                    this.key = new Key(x * GameWebSocketServer.SIZE_TILE, y * GameWebSocketServer.SIZE_TILE);
                    System.out.println("[Llave] Encontrada en X=" + (x * GameWebSocketServer.SIZE_TILE) + ", Y=" + (y * GameWebSocketServer.SIZE_TILE));
                    return; // Solo una llave por nivel
                }
            }
        }
        System.out.println("[Llave] No se encontró llave en este nivel");
    }

    public void initializePlatforms() {
        // Escanear el mapa para encontrar plataformas (grupos continuos de tiles 31-39)
        boolean[][] visited = new boolean[gameWorld.length][gameWorld[0].length];
        
        for (int y = 0; y < gameWorld.length; y++) {
            for (int x = 0; x < gameWorld[y].length; x++) {
                int tileType = gameWorld[y][x];
                
                if (!visited[y][x] && GameWebSocketServer.MOVING_PLATFORM_ORIGINS.contains(tileType)) {
                    // Encontrar los límites de esta plataforma
                    int minX = x;
                    int maxX = x;
                    int minY = y;
                    int maxY = y;
                    
                    // Expandir hacia la derecha
                    while (maxX + 1 < gameWorld[y].length && 
                           GameWebSocketServer.MOVING_PLATFORM_ORIGINS.contains(gameWorld[y][maxX + 1])) {
                        maxX++;
                    }
                    
                    // Expandir hacia abajo (solo si es la misma fila de plataforma)
                    while (maxY + 1 < gameWorld.length && 
                           GameWebSocketServer.MOVING_PLATFORM_ORIGINS.contains(gameWorld[maxY + 1][x])) {
                        maxY++;
                    }
                    
                    // Marcar como visitados
                    for (int py = minY; py <= maxY; py++) {
                        for (int px = minX; px <= maxX; px++) {
                            if (GameWebSocketServer.MOVING_PLATFORM_ORIGINS.contains(gameWorld[py][px])) {
                                visited[py][px] = true;
                            }
                        }
                    }
                    
                    // Crear UNA plataforma por grupo continuo
                    String platformId = "platform_" + y + "_" + x;
                    float platformX = minX * GameWebSocketServer.SIZE_TILE;
                    float platformY = minY * GameWebSocketServer.SIZE_TILE;
                    float platformWidth = (maxX - minX + 1) * GameWebSocketServer.SIZE_TILE;
                    float platformHeight = (maxY - minY + 1) * GameWebSocketServer.SIZE_TILE;
                    
                    Platform platform = new Platform(platformId, platformX, platformY, tileType);
                    platform.width = platformWidth;
                    platform.height = platformHeight;
                    platforms.put(platformId, platform);
                    
                    System.out.println("[GameRoom] Plataforma detectada: " + platformId + " en (" + minX + "," + minY + ") tamaño: " + platformWidth + "x" + platformHeight);
                }
            }
        }
        System.out.println("[GameRoom] Inicializadas " + platforms.size() + " plataforma(s)");
    }

    private static int[][] deepCopyWorld(int[][] world) {
        int[][] copy = new int[world.length][];
        for (int i = 0; i < world.length; i++) {
            copy[i] = world[i].clone();
        }
        return copy;
    }

    void addPlayer(Player player) {
        players.put(player.id, player);
    }

    void removePlayer(String playerId) {
        players.remove(playerId);
    }

    Player getPlayer(String playerId) {
        return players.get(playerId);
    }

    List<Map<String, Object>> getPlayersData() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (Player player : players.values()) {
            data.add(player.toMap());
        }
        return data;
    }

    List<Map<String, Object>> getPlatformsData() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (Platform platform : platforms.values()) {
            data.add(platform.toMap());
        }
        return data;
    }

    Map<String, Object> getKeyData() {
        if (key == null) {
            return null;
        }
        return key.toMap();
    }

    /**
     * Cuenta recursivamente a los jugadores que están encima de un jugador dado
     */
    private void countPlayersOnTop(Player basePlayer, Platform platform, Map<String, Integer> platformPlayerCount) {
        // Buscar jugadores que están encima de este jugador
        for (Player other : players.values()) {
            if (other.id.equals(basePlayer.id) || !other.isVisible) continue;
            
            // Verificar si el otro jugador está encima de este (rango amplio)
            float otherBottomY = other.y + other.height;
            float baseTopY = basePlayer.y;
            
            boolean isOnTopOfBase = other.x + other.width > basePlayer.x &&
                other.x < basePlayer.x + basePlayer.width &&
                otherBottomY >= baseTopY - 10 &&
                otherBottomY <= baseTopY + 20;
            
            if (isOnTopOfBase) {
                platformPlayerCount.put(platform.id, platformPlayerCount.get(platform.id) + 1);
                // Recursivamente contar jugadores encima de este jugador
                countPlayersOnTop(other, platform, platformPlayerCount);
            }
        }
    }

    /**
     * Actualiza posiciones de plataformas y mueve jugadores con ellas
     * DEBE ser llamado ANTES de la física del jugador
     */
    void updatePlatformPositions() {
        // Actualizar posiciones de plataformas en movimiento
        for (Platform platform : platforms.values()) {
            platform.updatePosition();
            
            // Cuando la plataforma termina de moverse de vuelta a origen, marcar isAtOrigin
            if (!platform.isMoving && Math.abs(platform.y - platform.originalY) < 0.1f) {
                platform.isAtOrigin = true;
            }
        }

        // Mover jugadores CON las plataformas
        for (Player player : players.values()) {
            if (!player.isVisible) continue;

            for (Platform platform : platforms.values()) {
                float playerBottomY = player.y + player.height;
                
                // Rango más amplio para detectar jugadores sobre la plataforma
                boolean isOnPlatform = player.x + player.width > platform.x &&
                    player.x < platform.x + platform.width &&
                    playerBottomY >= platform.y - 10 &&
                    playerBottomY <= platform.y + 20 &&
                    platform.isMoving;
                
                if (isOnPlatform) {
                    // El jugador se mueve junto con el delta de la plataforma
                    // Calcular cuánto se movió la plataforma este frame
                    long elapsed = System.currentTimeMillis() - platform.startMoveTime;
                    long previousElapsed = elapsed - (1000 / 60); // Un frame anterior (60 FPS)
                    
                    float previousProgress = Math.max(0, Math.min(1.0f, previousElapsed / (float) Platform.MOVE_DURATION));
                    float currentProgress = Math.min(1.0f, elapsed / (float) Platform.MOVE_DURATION);
                    
                    // Easing para ambos frames
                    float previousEased = previousProgress < 0.5f ? 
                        4 * previousProgress * previousProgress * previousProgress : 
                        1 - (float)Math.pow(-2 * previousProgress + 2, 3) / 2;
                    float currentEased = currentProgress < 0.5f ? 
                        4 * currentProgress * currentProgress * currentProgress : 
                        1 - (float)Math.pow(-2 * currentProgress + 2, 3) / 2;
                    
                    float startY = platform.y;
                    float previousPlatformY = startY + (platform.destY - startY) * previousEased;
                    float currentPlatformY = startY + (platform.destY - startY) * currentEased;
                    
                    float platformDelta = currentPlatformY - previousPlatformY;
                    player.y += platformDelta;
                    break; // Un jugador solo se mueve con una plataforma
                }
            }
        }
    }

    /**
     * Actualiza la lógica de detección y movimiento de plataformas
     * DEBE ser llamado DESPUÉS de la física del jugador
     */
    void updatePlatformLogic() {
        // Detectar qué jugadores están en cada plataforma
        Map<String, Integer> platformPlayerCount = new HashMap<>();
        for (Platform platform : platforms.values()) {
            platformPlayerCount.put(platform.id, 0);
        }

        for (Player player : players.values()) {
            if (!player.isVisible) continue;

            // Verificar en qué plataforma está el jugador (sin importar si está en el suelo)
            for (Platform platform : platforms.values()) {
                float playerBottomY = player.y + player.height;
                
                // Verificar si el jugador está dentro del rango de la plataforma (rango amplio)
                boolean isNearPlatform = player.x + player.width > platform.x &&
                    player.x < platform.x + platform.width &&
                    playerBottomY >= platform.y - 10 &&
                    playerBottomY <= platform.y + 20;
                
                if (isNearPlatform) {
                    platformPlayerCount.put(platform.id, platformPlayerCount.get(platform.id) + 1);
                    
                    // Contar también a los jugadores que están ENCIMA de este jugador
                    countPlayersOnTop(player, platform, platformPlayerCount);
                    break; // Un jugador solo puede estar en una plataforma
                }
            }
        }

        // Actualizar plataformas basadas en el número de jugadores
        for (Platform platform : platforms.values()) {
            int playersOnPlatform = platformPlayerCount.get(platform.id);
            platform.playersOnPlatform = playersOnPlatform;

            //System.out.println("[Plataforma " + platform.id + "] Jugadores: " + playersOnPlatform + "/" + platform.requiredPlayers + ", moving: " + platform.isMoving);

            // Si NO hay suficientes jugadores Y la plataforma NO está en movimiento, resetear
            if (playersOnPlatform < platform.requiredPlayers && !platform.isMoving) {
                if (platform.y != platform.originalY) {
                    //System.out.println("[Plataforma] " + platform.id + " reseteando a posición original");
                    platform.resetToOriginal();
                }
                platform.detectedPlayersTime = 0;
                continue;
            }

            // Si está parada Y hay suficientes jugadores Y está en su posición original
            if (!platform.isMoving && playersOnPlatform >= platform.requiredPlayers && platform.isAtOrigin) {
                // Inicializar el contador de detección si es la primera vez
                if (platform.detectedPlayersTime == 0) {
                    platform.detectedPlayersTime = System.currentTimeMillis();
                }

                // Esperar el delay antes de iniciar movimiento
                long detectionElapsed = System.currentTimeMillis() - platform.detectedPlayersTime;
                if (detectionElapsed >= Platform.PLAYER_DETECTION_DELAY) {
                    // Encontrar dirección hacia tile 30 (destino)
                    int platformTileY = (int)(platform.y / GameWebSocketServer.SIZE_TILE);
                    int platformTileX = (int)(platform.x / GameWebSocketServer.SIZE_TILE);
                    int platformTileWidth = (int)(platform.width / GameWebSocketServer.SIZE_TILE);
                    
                    int direction = 0;
                    float destY = platform.y;
                    boolean foundDest = false;

                    //System.out.println("[Plataforma] Buscando destino desde Y=" + platformTileY + ", X=" + platformTileX + "-" + (platformTileX + platformTileWidth));

                    // Buscar destino ARRIBA
                    for (int checkY = platformTileY - 1; checkY >= 0 && !foundDest; checkY--) {
                        boolean hasDestInRange = false;
                        for (int checkX = platformTileX; checkX < platformTileX + platformTileWidth; checkX++) {
                            if (checkX >= 0 && checkX < gameWorld[checkY].length) {
                                if (gameWorld[checkY][checkX] == GameWebSocketServer.PLATFORM_DESTINATION) {
                                    hasDestInRange = true;
                                    break;
                                }
                            }
                        }
                        if (hasDestInRange) {
                            direction = -1;
                            destY = checkY * GameWebSocketServer.SIZE_TILE;
                            foundDest = true;
                            //System.out.println("[Plataforma] Destino encontrado ARRIBA en Y=" + checkY);
                        }
                    }
                    
                    // Buscar destino ABAJO si no lo encontró arriba
                    if (!foundDest) {
                        for (int checkY = platformTileY + 1; checkY < gameWorld.length && !foundDest; checkY++) {
                            boolean hasDestInRange = false;
                            for (int checkX = platformTileX; checkX < platformTileX + platformTileWidth; checkX++) {
                                if (checkX >= 0 && checkX < gameWorld[checkY].length) {
                                    if (gameWorld[checkY][checkX] == GameWebSocketServer.PLATFORM_DESTINATION) {
                                        hasDestInRange = true;
                                        break;
                                    }
                                }
                            }
                            if (hasDestInRange) {
                                direction = 1;
                                destY = checkY * GameWebSocketServer.SIZE_TILE;
                                foundDest = true;
                                //System.out.println("[Plataforma] Destino encontrado ABAJO en Y=" + checkY);
                            }
                        }
                    }

                    if (direction != 0) {
                        //System.out.println("[Plataforma] " + platform.id + " ¡¡MOVIMIENTO!! hacia Y=" + destY);
                        platform.startMovement(direction, destY);
                        platform.detectedPlayersTime = 0; // Reset para siguiente movimiento
                    } else {
                        //System.out.println("[Plataforma] NO se encontró destino para " + platform.id);
                    }
                }
            } else if (playersOnPlatform < platform.requiredPlayers) {
                // Jugador se fue mientras se esperaba el delay
                platform.detectedPlayersTime = 0;
            }
        }
    }

    public static RoomConfig loadRoomConfig(String path) {
        try (Reader reader = new FileReader(path)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, RoomConfig.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

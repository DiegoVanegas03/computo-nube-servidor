package org.server;

import java.util.HashMap;
import java.util.Map;

public class Platform {
    public String id;
    public float x;
    public float y;
    public float width;
    public float height;
    public int type; // 31-39: tipo de plataforma
    public int direction; // -1: arriba, 1: abajo, 0: parado
    public int requiredPlayers; // Cuántos jugadores se necesitan para moverla
    public int playersOnPlatform = 0;
    public float destY; // Y hacia donde se mueve
    public boolean isMoving = false;
    public long startMoveTime = 0;
    public long detectedPlayersTime = 0; // Cuándo se detectó que hay suficientes jugadores
    
    // Almacenar posiciones originales para resetear
    public float originalX;
    public float originalY;
    
    // Flag para saber si la plataforma está en su posición "activa" (origen)
    public boolean isAtOrigin = true;
    
    public static final long MOVE_DURATION = 1500; // 1 segundo para mover 1 tile (más lento)
    public static final long PLAYER_DETECTION_DELAY = 50; // 0.5 segundos de delay antes de mover

    Platform(String id, float x, float y, int type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.originalX = x;
        this.originalY = y;
        this.type = type;
        this.width = GameWebSocketServer.SIZE_TILE;
        this.height = GameWebSocketServer.SIZE_TILE;
        this.requiredPlayers = type - 30; // 31 requiere 1, 32 requiere 2, etc.
    }

    public void startMovement(int direction, float destY) {
        this.direction = direction;
        this.destY = destY;
        this.isMoving = true;
        this.startMoveTime = System.currentTimeMillis();
        this.isAtOrigin = false;
    }

    public void updatePosition() {
        if (!isMoving) return;

        long elapsed = System.currentTimeMillis() - startMoveTime;
        float progress = Math.min(1.0f, elapsed / (float) MOVE_DURATION);

        // Usar interpolación suave easing (ease-in-out cubic) para movimiento más natural
        float easeProgress = progress < 0.5f ? 
            4 * progress * progress * progress : 
            1 - (float)Math.pow(-2 * progress + 2, 3) / 2;
        
        float startY = y;
        y = startY + (destY - startY) * easeProgress;

        if (progress >= 1.0f) {
            y = destY;
            isMoving = false;
            direction = 0;
        }
    }

    public void resetToOriginal() {
        // Usar startMovement para animar el regreso a la posición original
        // con la misma velocidad que el movimiento hacia el destino
        this.startMovement(this.y < this.originalY ? -1 : 1, this.originalY);
        this.isAtOrigin = false; // Mientras se mueve, no está en origen
    }

    public int getPlayersNeeded() {
        // Retorna cuántos jugadores aún faltan para activar el movimiento
        return Math.max(0, requiredPlayers - playersOnPlatform);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("x", x);
        data.put("y", y);
        data.put("width", width);
        data.put("height", height);
        data.put("type", type);
        data.put("direction", direction);
        data.put("isMoving", isMoving);
        data.put("playersOnPlatform", playersOnPlatform);
        data.put("requiredPlayers", requiredPlayers);
        data.put("playersNeeded", getPlayersNeeded());
        return data;
    }
}

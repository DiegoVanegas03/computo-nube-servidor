package org.server;

import java.util.HashMap;
import java.util.Map;

public class Key {
    public float x;
    public float y;
    public boolean isCollected = false;
    public String carriedByPlayerId = null; // ID del jugador que la lleva, null si está en el mapa
    
    // Animación flotante
    public float floatOffset = 0;
    public long lastFloatUpdate = 0;
    
    // Movimiento suave al transferirse
    public float targetX = 0;
    public float targetY = 0;
    public boolean isMovingToTarget = false;
    public long transferStartTime = 0;
    
    // Animación de apertura de puerta
    public boolean isOpeningDoor = false;
    public long doorOpenStartTime = 0;

    public static final float WIDTH = 16;
    public static final float HEIGHT = 16;

    Key(float x, float y) {
        this.x = x;
        this.y = y;
    }

    // Verificar si un jugador toca la llave
    public boolean checkCollision(Player player) {
        if (isCollected) return false;

        return player.x < x + WIDTH &&
               player.x + player.width > x &&
               player.y < y + HEIGHT &&
               player.y + player.height > y;
    }

    // Verificar si un jugador toca a otro que tiene la llave
    public boolean checkPlayerSteal(Player stealer, Player carrier) {
        if (carriedByPlayerId == null || !carrier.id.equals(carriedByPlayerId)) return false;

        // Margen adicional para hacer el robo más fácil (20 píxeles alrededor del portador)
        float stealMargin = 20.0f;
        
        return stealer.x < carrier.x + carrier.width + stealMargin &&
               stealer.x + stealer.width > carrier.x - stealMargin &&
               stealer.y < carrier.y + carrier.height + stealMargin &&
               stealer.y + stealer.height > carrier.y - stealMargin;
    }

    // Actualizar posición cuando sigue a un jugador
    public void updatePosition(Player carrier) {
        if (carrier != null && carriedByPlayerId != null) {
            // Si está moviéndose a un objetivo, interpolar suavemente
            if (isMovingToTarget) {
                long elapsed = System.currentTimeMillis() - transferStartTime;
                float progress = Math.min(1.0f, elapsed / 200.0f); // 200ms de transición
                
                // Interpolación suave
                float easeProgress = progress < 0.5f ? 
                    4 * progress * progress * progress : 
                    1 - (float)Math.pow(-2 * progress + 2, 3) / 2;
                
                this.x = this.x + (targetX - this.x) * easeProgress;
                this.y = this.y + (targetY - this.y) * easeProgress;
                
                if (progress >= 1.0f) {
                    isMovingToTarget = false;
                    this.x = targetX;
                    this.y = targetY;
                }
            } else {
                // Movimiento normal con retraso horizontal
                float targetX = carrier.x + (carrier.width - WIDTH) / 2;
                float targetY = carrier.y - HEIGHT - 25 + floatOffset; // Subir más la llave
                
                // Aplicar retraso horizontal (la llave se mueve más lentamente)
                this.x = this.x + (targetX - this.x) * 0.15f; // Suavizado horizontal
                this.y = targetY; // Y instantáneo
            }
        }
    }
    
    // Actualizar animación flotante
    public void updateFloatAnimation() {
        long now = System.currentTimeMillis();
        if (now - lastFloatUpdate > 50) { // Actualizar cada 50ms
            floatOffset = (float) Math.sin(now * 0.005) * 3; // Movimiento suave arriba/abajo
            lastFloatUpdate = now;
        }
    }
    
    // Verificar si la llave está tocando la puerta
    public boolean checkDoorCollision(GameRoom room) {
        if (carriedByPlayerId == null) return false;
        
        Player carrier = room.getPlayer(carriedByPlayerId);
        if (carrier == null) return false;
        
        // Calcular los tiles que ocupa el jugador con la llave
        int leftTile = (int)(carrier.x / GameWebSocketServer.SIZE_TILE);
        int rightTile = (int)((carrier.x + carrier.width - 1) / GameWebSocketServer.SIZE_TILE);
        int topTile = (int)(carrier.y / GameWebSocketServer.SIZE_TILE);
        int bottomTile = (int)((carrier.y + carrier.height - 1) / GameWebSocketServer.SIZE_TILE);

        // Verificar límites
        if (leftTile < 0 || rightTile >= room.world[0].length ||
                topTile < 0 || bottomTile >= room.world.length) {
            return false;
        }

        // Verificar si está tocando tiles de puerta
        for (int y = topTile; y <= bottomTile; y++) {
            for (int x = leftTile; x <= rightTile; x++) {
                if (GameWebSocketServer.WINNER_TILES.contains(room.world[y][x])) {
                    return true;
                }
            }
        }
        return false;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("x", x);
        data.put("y", y);
        data.put("isCollected", isCollected);
        data.put("carriedByPlayerId", carriedByPlayerId);
        data.put("floatOffset", floatOffset);
        data.put("isOpeningDoor", isOpeningDoor);
        return data;
    }
}
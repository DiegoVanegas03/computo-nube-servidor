package org.client;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.util.*;
import java.util.List;

public class GameWebSocketClient extends WebSocketClient {

    private final Gson gson = new Gson();
    private GameClientGUI gui;
    private String userId;
    private String username;
    private String currentRoom;
    private Map<String, PlayerData> players = new HashMap<>();

    public GameWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Conectado al servidor");
        if (gui != null) {
            gui.updateStatus("Conectado al servidor", Color.GREEN);
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            System.out.println("Mensaje del servidor: "+message);
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();
            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : new JsonObject();

            switch (type) {
                case "authSuccess":
                    handleAuthSuccess(data);
                    break;
                case "authFailed":
                    handleAuthFailed(data);
                    break;
                case "roomJoined":
                    handleRoomJoined(data);
                    break;
                case "playerJoined":
                    handlePlayerJoined(data);
                    break;
                case "playerLeft":
                    handlePlayerLeft(data);
                    break;
                case "gameUpdate":
                    handleGameUpdate(data);
                    break;
                case "chat":
                    handleChat(data);
                    break;
                case "error":
                    handleError(data);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error procesando mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Desconectado del servidor: " + reason);
        if (gui != null) {
            gui.updateStatus("Desconectado: " + reason, Color.RED);
        }
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("Error: " + ex.getMessage());
        ex.printStackTrace();
    }

    // Métodos para enviar mensajes al servidor
    public void authenticate(String username, String password) {
        Map<String, String> data = new HashMap<>();
        data.put("username", username);
        data.put("password", password);
        sendMessage("auth", data);
    }

    public void joinRoom(String roomId) {
        Map<String, String> data = new HashMap<>();
        data.put("roomId", roomId);
        sendMessage("joinRoom", data);
    }

    public void move(String direction) {
        Map<String, String> data = new HashMap<>();
        data.put("direction", direction);
        sendMessage("move", data);
    }

    public void jump() {
        sendMessage("jump", new HashMap<>());
    }

    public void sendChat(String message) {
        Map<String, String> data = new HashMap<>();
        data.put("message", message);
        sendMessage("chat", data);
    }

    private void sendMessage(String type, Map<String, ?> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        if (data != null && !data.isEmpty()) {
            message.put("data", data);
        }
        send(gson.toJson(message));
    }

    // Handlers para mensajes del servidor
    private void handleAuthSuccess(JsonObject data) {
        this.userId = data.get("userId").getAsString();
        this.username = data.get("username").getAsString();

        List<RoomInfo> rooms = new ArrayList<>();
        data.getAsJsonArray("rooms").forEach(room -> {
            JsonObject roomObj = room.getAsJsonObject();
            rooms.add(new RoomInfo(
                    roomObj.get("id").getAsString(),
                    roomObj.get("name").getAsString(),
                    roomObj.get("players").getAsString()
            ));
        });

        if (gui != null) {
            gui.showRoomSelection(rooms);
        }
    }

    private void handleAuthFailed(JsonObject data) {
        String reason = data.get("reason").getAsString();
        if (gui != null) {
            gui.updateStatus("Autenticación fallida: " + reason, Color.RED);
        }
    }

    private void handleRoomJoined(JsonObject data) {
        this.currentRoom = data.get("roomId").getAsString();
        String roomName = data.get("roomName").getAsString();

        players.clear();
        data.getAsJsonArray("players").forEach(p -> {
            JsonObject playerObj = p.getAsJsonObject();
            PlayerData player = new PlayerData(
                    playerObj.get("id").getAsString(),
                    playerObj.get("username").getAsString(),
                    playerObj.get("x").getAsFloat(),
                    playerObj.get("y").getAsFloat()
            );
            players.put(player.id, player);
        });

        if (gui != null) {
            gui.showGame(roomName);
            gui.addChatMessage("Sistema", "Te uniste a " + roomName);
        }
    }

    private void handlePlayerJoined(JsonObject data) {
        String playerId = data.get("userId").getAsString();
        String playerUsername = data.get("username").getAsString();
        JsonObject playerData = data.getAsJsonObject("player");

        PlayerData player = new PlayerData(
                playerId,
                playerUsername,
                playerData.get("x").getAsFloat(),
                playerData.get("y").getAsFloat()
        );
        players.put(playerId, player);

        if (gui != null) {
            gui.addChatMessage("Sistema", playerUsername + " se unió al juego");
        }
    }

    private void handlePlayerLeft(JsonObject data) {
        String playerId = data.get("userId").getAsString();
        String playerUsername = data.get("username").getAsString();

        players.remove(playerId);

        if (gui != null) {
            gui.addChatMessage("Sistema", playerUsername + " salió del juego");
        }
    }

    private void handleGameUpdate(JsonObject data) {
        data.getAsJsonArray("players").forEach(p -> {
            JsonObject playerObj = p.getAsJsonObject();
            String id = playerObj.get("id").getAsString();
            PlayerData player = players.get(id);
            if (player != null) {
                player.x = playerObj.get("x").getAsFloat();
                player.y = playerObj.get("y").getAsFloat();
            }
        });

        if (gui != null) {
            gui.repaintGame();
        }
    }

    private void handleChat(JsonObject data) {
        String playerUsername = data.get("username").getAsString();
        String message = data.get("message").getAsString();

        if (gui != null) {
            gui.addChatMessage(playerUsername, message);
        }
    }

    private void handleError(JsonObject data) {
        String error = data.get("message").getAsString();
        if (gui != null) {
            gui.updateStatus("Error: " + error, Color.RED);
        }
    }

    public void setGUI(GameClientGUI gui) {
        this.gui = gui;
    }

    public Map<String, PlayerData> getPlayers() {
        return players;
    }

    public String getUserId() {
        return userId;
    }

    // Clases de datos
    static class PlayerData {
        String id;
        String username;
        float x;
        float y;

        PlayerData(String id, String username, float x, float y) {
            this.id = id;
            this.username = username;
            this.x = x;
            this.y = y;
        }
    }

    static class RoomInfo {
        String id;
        String name;
        String playerCount;

        RoomInfo(String id, String name, String playerCount) {
            this.id = id;
            this.name = name;
            this.playerCount = playerCount;
        }
    }

    // GUI del cliente
    public static class GameClientGUI extends JFrame {
        private GameWebSocketClient client;

        // Login
        private JPanel loginPanel;
        private JTextField usernameField;
        private JPasswordField passwordField;
        private JButton loginButton;
        private JLabel statusLabel;

        // Selección de sala
        private JPanel roomPanel;
        private JComboBox<String> roomComboBox;
        private JButton joinRoomButton;
        private List<RoomInfo> availableRooms;

        // Juego
        private JPanel gamePanel;
        private GameCanvas gameCanvas;
        private JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
        private JLabel roomLabel;

        public GameClientGUI() {
            setTitle("Juego 2D Multijugador");
            setSize(1000, 700);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            createLoginPanel();
            setContentPane(loginPanel);
        }

        private void createLoginPanel() {
            loginPanel = new JPanel(new GridBagLayout());
            loginPanel.setBackground(new Color(102, 126, 234));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JLabel titleLabel = new JLabel("Juego 2D Multijugador");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            loginPanel.add(titleLabel, gbc);

            JLabel userLabel = new JLabel("Usuario:");
            userLabel.setForeground(Color.WHITE);
            gbc.gridy = 1;
            gbc.gridwidth = 1;
            loginPanel.add(userLabel, gbc);

            usernameField = new JTextField(20);
            gbc.gridx = 1;
            loginPanel.add(usernameField, gbc);

            JLabel passLabel = new JLabel("Contraseña:");
            passLabel.setForeground(Color.WHITE);
            gbc.gridx = 0;
            gbc.gridy = 2;
            loginPanel.add(passLabel, gbc);

            passwordField = new JPasswordField(20);
            gbc.gridx = 1;
            loginPanel.add(passwordField, gbc);

            loginButton = new JButton("Conectar");
            loginButton.setBackground(new Color(76, 175, 80));
            loginButton.setForeground(Color.WHITE);
            loginButton.setFont(new Font("Arial", Font.BOLD, 14));
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            loginPanel.add(loginButton, gbc);

            statusLabel = new JLabel("");
            statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
            gbc.gridy = 4;
            loginPanel.add(statusLabel, gbc);

            loginButton.addActionListener(e -> connectToServer());

            passwordField.addActionListener(e -> connectToServer());
        }

        private void connectToServer() {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                updateStatus("Por favor completa todos los campos", Color.RED);
                return;
            }

            try {
                updateStatus("Conectando...", Color.YELLOW);
                loginButton.setEnabled(false);

                URI serverUri = new URI("ws://localhost:8887");
                client = new GameWebSocketClient(serverUri);
                client.setGUI(this);
                client.connect();

                // Esperar conexión y autenticar
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        client.authenticate(username, password);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }).start();

            } catch (Exception ex) {
                updateStatus("Error de conexión: " + ex.getMessage(), Color.RED);
                loginButton.setEnabled(true);
                ex.printStackTrace();
            }
        }

        public void showRoomSelection(List<RoomInfo> rooms) {
            SwingUtilities.invokeLater(() -> {
                this.availableRooms = rooms;
                createRoomPanel();
                setContentPane(roomPanel);
                revalidate();
                repaint();
            });
        }

        private void createRoomPanel() {
            roomPanel = new JPanel(new GridBagLayout());
            roomPanel.setBackground(new Color(102, 126, 234));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JLabel titleLabel = new JLabel("Selecciona una Sala");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            roomPanel.add(titleLabel, gbc);

            String[] roomNames = availableRooms.stream()
                    .map(r -> r.name + " (" + r.playerCount + " jugadores)")
                    .toArray(String[]::new);

            roomComboBox = new JComboBox<>(roomNames);
            gbc.gridy = 1;
            roomPanel.add(roomComboBox, gbc);

            joinRoomButton = new JButton("Unirse a la Sala");
            joinRoomButton.setBackground(new Color(76, 175, 80));
            joinRoomButton.setForeground(Color.WHITE);
            joinRoomButton.setFont(new Font("Arial", Font.BOLD, 14));
            gbc.gridy = 2;
            roomPanel.add(joinRoomButton, gbc);

            joinRoomButton.addActionListener(e -> {
                int selectedIndex = roomComboBox.getSelectedIndex();
                if (selectedIndex >= 0) {
                    RoomInfo room = availableRooms.get(selectedIndex);
                    client.joinRoom(room.id);
                }
            });
        }

        public void showGame(String roomName) {
            SwingUtilities.invokeLater(() -> {
                createGamePanel(roomName);
                setContentPane(gamePanel);
                revalidate();
                repaint();
                gameCanvas.requestFocus();
            });
        }

        private void createGamePanel(String roomName) {
            gamePanel = new JPanel(new BorderLayout());

            // Panel superior con información
            JPanel topPanel = new JPanel();
            topPanel.setBackground(new Color(102, 126, 234));
            roomLabel = new JLabel("Sala: " + roomName);
            roomLabel.setForeground(Color.WHITE);
            roomLabel.setFont(new Font("Arial", Font.BOLD, 16));
            topPanel.add(roomLabel);
            gamePanel.add(topPanel, BorderLayout.NORTH);

            // Canvas del juego
            gameCanvas = new GameCanvas(client);
            gamePanel.add(gameCanvas, BorderLayout.CENTER);

            // Panel de chat
            JPanel chatPanel = new JPanel(new BorderLayout());
            chatPanel.setPreferredSize(new Dimension(250, 0));
            chatPanel.setBorder(BorderFactory.createTitledBorder("Chat"));

            chatArea = new JTextArea();
            chatArea.setEditable(false);
            chatArea.setLineWrap(true);
            chatArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(chatArea);
            chatPanel.add(scrollPane, BorderLayout.CENTER);

            JPanel chatInputPanel = new JPanel(new BorderLayout());
            chatInput = new JTextField();
            sendChatButton = new JButton("Enviar");
            chatInputPanel.add(chatInput, BorderLayout.CENTER);
            chatInputPanel.add(sendChatButton, BorderLayout.EAST);
            chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

            gamePanel.add(chatPanel, BorderLayout.EAST);

            // Eventos
            sendChatButton.addActionListener(e -> sendChatMessage());
            chatInput.addActionListener(e -> sendChatMessage());

            // Controles del teclado
            gameCanvas.addKeyListener(new KeyAdapter() {
                private boolean leftPressed = false;
                private boolean rightPressed = false;

                @Override
                public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT:
                        case KeyEvent.VK_A:
                            if (!leftPressed) {
                                client.move("left");
                                leftPressed = true;
                            }
                            break;
                        case KeyEvent.VK_RIGHT:
                        case KeyEvent.VK_D:
                            if (!rightPressed) {
                                client.move("right");
                                rightPressed = true;
                            }
                            break;
                        case KeyEvent.VK_SPACE:
                        case KeyEvent.VK_UP:
                        case KeyEvent.VK_W:
                            client.jump();
                            break;
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT:
                        case KeyEvent.VK_A:
                            leftPressed = false;
                            client.move("stop");
                            break;
                        case KeyEvent.VK_RIGHT:
                        case KeyEvent.VK_D:
                            rightPressed = false;
                            client.move("stop");
                            break;
                    }
                }
            });
        }

        private void sendChatMessage() {
            String message = chatInput.getText().trim();
            if (!message.isEmpty()) {
                client.sendChat(message);
                chatInput.setText("");
            }
        }

        public void addChatMessage(String username, String message) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append(username + ": " + message + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            });
        }

        public void updateStatus(String message, Color color) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(message);
                statusLabel.setForeground(color);
            });
        }

        public void repaintGame() {
            if (gameCanvas != null) {
                gameCanvas.repaint();
            }
        }
    }

    // Canvas para dibujar el juego
    static class GameCanvas extends JPanel {
        private GameWebSocketClient client;
        private static final int PLAYER_SIZE = 30;

        public GameCanvas(GameWebSocketClient client) {
            this.client = client;
            setBackground(new Color(135, 206, 235)); // Cielo azul
            setFocusable(true);
            setPreferredSize(new Dimension(800, 600));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Dibujar suelo
            g2d.setColor(new Color(139, 69, 19));
            g2d.fillRect(0, 500, getWidth(), getHeight() - 500);

            // Dibujar jugadores
            Map<String, PlayerData> players = client.getPlayers();
            String myUserId = client.getUserId();

            for (PlayerData player : players.values()) {
                // Color diferente para el jugador local
                if (player.id.equals(myUserId)) {
                    g2d.setColor(new Color(76, 175, 80)); // Verde
                } else {
                    g2d.setColor(new Color(244, 67, 54)); // Rojo
                }

                // Dibujar jugador como rectángulo
                g2d.fillRect((int)player.x, (int)player.y, PLAYER_SIZE, PLAYER_SIZE);

                // Dibujar nombre
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.BOLD, 12));
                g2d.drawString(player.username, (int)player.x, (int)player.y - 5);
            }

            // Instrucciones
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString("Controles: Flechas o A/D para mover, ESPACIO o W para saltar", 10, 20);
        }
    }

    // Main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameClientGUI gui = new GameClientGUI();
            gui.setVisible(true);
        });
    }
}
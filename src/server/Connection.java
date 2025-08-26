package server;

import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Connection {
    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private final String username;
    private Thread readerThread;
    private Thread writerThread;
    private volatile boolean isActive;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static final List<Connection> activeConnections = Collections.synchronizedList(new ArrayList<>());
    
    public Connection(Socket socket, String username) throws IOException {
        this.socket = socket;
        this.username = username;
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());
        this.isActive = true;
        
        System.out.println("Nueva conexión establecida desde: " + socket.getInetAddress() + " Usuario: " + username);
    }
    
    public void start() {
        activeConnections.add(this);
        System.out.println("Total de conexiones activas: " + activeConnections.size());
        this.readerThread = new Thread(this::readMessages);
        this.writerThread = new Thread(this::writeMessages);
        readerThread.start();
        writerThread.start();
    }
    
    private void readMessages() {
        try {
            String message;
            while (isActive && (message = dis.readUTF()) != null) {
                // Verificar si es JSON válido
                JSONObject messageJson;
                try {
                    messageJson = new JSONObject(message);
                } catch (Exception e) {
                    System.out.println("Cliente " + socket.getInetAddress() + " (" + username + ") envió un mensaje que no es JSON válido: " + message);
                    sendMessage(createServerMessage("Error: Los mensajes deben ser JSON válido"));
                    continue;
                }
                
                // Verificar si es comando de salida
                if (messageJson.has("action") && "exit".equals(messageJson.getString("action"))) {
                    System.out.println("Cliente " + socket.getInetAddress() + " (" + username + ") solicita desconexión");
                    close();
                    break;
                }
                
                broadcastMessage(message, this);
            }
        } catch (IOException e) {
            if (isActive) {
                System.out.println("Error leyendo mensaje del cliente " + socket.getInetAddress() + " (" + username + "): " + e.getMessage());
            }
            close();
        }
    }
    
    private void writeMessages() {
        try {
            while (isActive) {
                String message = messageQueue.take();
                if (!isActive) break;
                dos.writeUTF(message);
                dos.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Hilo de escritura interrumpido para: " + socket.getInetAddress());
        } catch (IOException e) {
            if (isActive) {
                System.out.println("Error escribiendo mensaje al cliente " + socket.getInetAddress() + ": " + e.getMessage());
            }
            close();
        }
    }
    
    public void sendMessage(String message) {
        if (!isActive) return;
        try {
            messageQueue.offer(message);
        } catch (Exception e) {
            System.out.println("Error agregando mensaje a la cola para " + socket.getInetAddress() + ": " + e.getMessage());
            close();
        }
    }
    
    public String createServerMessage(String info) {
        JSONObject serverMessage = new JSONObject();
        serverMessage.put("user", "server");
        serverMessage.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        serverMessage.put("info", info);
        return serverMessage.toString();
    }
    
    public static void broadcastMessage(String clientJsonMessage, Connection sender) {
        // Crear el mensaje del servidor con el formato requerido
        JSONObject serverMessage = new JSONObject();
        serverMessage.put("user", sender.username);
        serverMessage.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        // Parsear el JSON del cliente para incluirlo en info
        try {
            JSONObject clientMessage = new JSONObject(clientJsonMessage);
            serverMessage.put("info", clientMessage);
        } catch (Exception e) {
            // Si no es JSON válido, incluir como string
            serverMessage.put("info", clientJsonMessage);
        }
        
        String formattedMessage = serverMessage.toString();
        
        synchronized (activeConnections) {
            for (Connection connection : activeConnections) {
                if (connection != sender && connection.isActive) {
                    connection.sendMessage(formattedMessage);
                }
            }
        }
        
        System.out.println("Broadcast desde " + sender.username + ": " + formattedMessage);
    }
    
    public String getClientAddress() {
        return socket.getInetAddress().toString();
    }
    
    public void close() {
        if (!isActive) return;
        isActive = false;
        try {
            activeConnections.remove(this);
            
            // Agregar mensaje especial para despertar el hilo de escritura
            messageQueue.offer("__CLOSE__");
            
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
            }
            if (writerThread != null && writerThread.isAlive()) {
                writerThread.interrupt();
            }
            
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) socket.close();
            
            System.out.println("Conexión cerrada para: " + socket.getInetAddress());
            System.out.println("Conexiones activas restantes: " + activeConnections.size());
            
        } catch (IOException e) {
            System.out.println("Error cerrando conexión: " + e.getMessage());
        }
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public Thread getReaderThread() {
        return readerThread;
    }
    
    public Thread getWriterThread() {
        return writerThread;
    }
    
    public static int getActiveConnectionsCount() {
        return activeConnections.size();
    }
    
    public static void closeAllConnections() {
        synchronized (activeConnections) {
            for (Connection connection : new ArrayList<>(activeConnections)) {
                connection.close();
            }
        }
    }
}

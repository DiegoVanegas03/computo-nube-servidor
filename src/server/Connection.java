package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Connection {
    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private Thread readerThread;
    private Thread writerThread;
    private volatile boolean isActive;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static final List<Connection> activeConnections = Collections.synchronizedList(new ArrayList<>());
    
    public Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());
        this.isActive = true;
        
        System.out.println("Nueva conexión establecida desde: " + socket.getInetAddress());
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
                if ("Exit".equals(message)) {
                    System.out.println("Cliente " + socket.getInetAddress() + " solicita desconexión");
                    close();
                    break;
                }
                broadcastMessage(message, this);
            }
        } catch (IOException e) {
            if (isActive) {
                System.out.println("Error leyendo mensaje del cliente " + socket.getInetAddress() + ": " + e.getMessage());
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
    
    public static void broadcastMessage(String message, Connection sender) {
        String formattedMessage = "Cliente " + sender.getClientAddress() + ": " + message;
        
        synchronized (activeConnections) {
            for (Connection connection : activeConnections) {
                if (connection != sender && connection.isActive) {
                    connection.sendMessage(formattedMessage);
                }
            }
        }
        
        System.out.println("Broadcast: " + formattedMessage);
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

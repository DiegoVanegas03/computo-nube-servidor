package server;

import org.json.JSONObject;
import repositories.UserRepository;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class MultiClientHandler extends Thread{
    private final Socket socket;
    private Connection connection;
    private String authenticatedUsername;
    
    public MultiClientHandler(Socket socket){
        this.socket = socket;
    }
    
    @Override
    public void run(){
        /*
            Aquí se hace el proceso de esperar que el cliente envié 1 primer mensaje por lo cual se bloquea el
            proceso hasta que esto se complete, se está a la espera de un JSON parseado a texto con la estructura
            {
                "username": string,
                "password": string,
            }
        */
        try {
            if(!authenticate()){
                System.out.println("Autenticación fallida para cliente: " + socket.getInetAddress());
                socket.close();
                return;
            };

            connection = new Connection(socket, authenticatedUsername);
            connection.start();
            
            // Enviar mensajes de bienvenida en formato JSON
            connection.sendMessage(connection.createServerMessage("Escribe JSON con 'action': 'exit' para salir."));
            connection.sendMessage(connection.createServerMessage("Usuarios conectados: " + Connection.getActiveConnectionsCount()));
      
            try {
                // Esperar a que ambos hilos terminen
                connection.getReaderThread().join();
                connection.getWriterThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Handler interrumpido para cliente: " + socket.getInetAddress());
            }
        } catch (IOException e) {
            System.err.println("Error creando conexión para cliente " + socket.getInetAddress() + ": " + e.getMessage());
        } finally {
            if (connection != null) connection.close();
            this.interrupt();
        }
    }

    public boolean authenticate() throws IOException {
        DataInputStream reader = new DataInputStream(socket.getInputStream());
        String plainTextAuth = reader.readUTF();

        JSONObject auth;
        try {
            auth = new JSONObject(plainTextAuth);
        } catch (Exception e) {
            System.out.println("Error: El cliente envió un JSON mal formado.");
            sendAuthErrorResponse("Error: El JSON de autenticación está mal formado");
            return false;
        }

        if (!auth.has("username") || !auth.has("password")) {
            System.out.println("Error: El JSON no contiene los campos 'username' y 'password'.");
            sendAuthErrorResponse("Error: El JSON debe contener los campos 'username' y 'password'");
            return false;
        }

        UserRepository userRepo = new UserRepository();
        String username = auth.getString("username").trim();
        boolean isAuthenticated = userRepo.CheckCredentials(username, auth.getString("password").trim());
        
        if (isAuthenticated) {
            this.authenticatedUsername = username;
        } else {
            sendAuthErrorResponse("Error: Credenciales inválidas");
        }
        
        return isAuthenticated;
    }
    
    private void sendAuthErrorResponse(String errorMessage) throws IOException {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("user", "server");
        errorResponse.put("time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        errorResponse.put("info", new JSONObject().put("status", "authentication_failed").put("message", errorMessage));
        
        DataOutputStream writer = new DataOutputStream(socket.getOutputStream());
        writer.writeUTF(errorResponse.toString());
        writer.flush();
    }
}

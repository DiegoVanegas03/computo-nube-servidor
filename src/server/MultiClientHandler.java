package server;

import org.json.JSONObject;
import repositories.UserRepository;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class MultiClientHandler extends Thread{
    private final Socket socket;
    private Connection connection;
    
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

            connection = new Connection(socket);
            connection.start();
            
            connection.sendMessage("Escribe 'Exit' para salir.");
            connection.sendMessage("Usuarios conectados: " + Connection.getActiveConnectionsCount());
      
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
            // Leer datos del cliente y procesarlos para terminar teniendo un JSON
            DataInputStream reader = new DataInputStream(socket.getInputStream());
            String plainTextAuth = reader.readUTF();

            JSONObject auth = new JSONObject(plainTextAuth);
            // Procesar autenticación
            UserRepository userRepo = new UserRepository();
            return userRepo.checkCredentials(auth.getString("username").trim(),
                    auth.getString("password").trim());
    }
}

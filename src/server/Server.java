
package server;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import util.EnvLoader;

public class Server {

    public static void main(String[] args) {
        int port = 2558;
        try {
            String portStr = EnvLoader.get("SERVER_PORT");
            if (portStr != null) {
                port = Integer.parseInt(portStr);
            }
        } catch (Exception e) {
            System.out.println("No se pudo leer el puerto del .env, usando 2558 por defecto.");
        }
        try{
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server Socket " + serverSocket);
            while(true){
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                    System.out.println("A new client has connected " + socket);
                    System.out.println("Assigning new Thread to this client...");
                    Thread t = new MultiClientHandler(socket);
                    t.start();
                } catch(Exception e) {
                    if (socket != null) socket.close();
                    System.out.println("Server Error! " + e.getMessage());
                }
            }
        }catch(IOException e){
            System.out.println("I/O Error! " + e.getMessage());
        }
    }
}

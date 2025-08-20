package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) {
        try{
            ServerSocket serverSocket = new ServerSocket(2558);
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

package org.server;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        String portEnv = System.getenv("SERVER_PORT");
        int port = portEnv != null ? Integer.parseInt(portEnv) : 8887;
        GameWebSocketServer server = new GameWebSocketServer(port);
        server.start();
    }
}
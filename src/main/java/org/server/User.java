package org.server;

import org.java_websocket.WebSocket;

public class User {
    String id;
    String username;
    WebSocket connection;
    String currentRoom;

    User(String id, String username, WebSocket connection) {
        this.id = id;
        this.username = username;
        this.connection = connection;
    }
}
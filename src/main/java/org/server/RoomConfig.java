package org.server;

import com.google.gson.annotations.SerializedName;

public class RoomConfig {
    @SerializedName("room-name")
    private String roomName;

    @SerializedName("users-to-start")
    private int usersToStart;

    @SerializedName("waiting-room")
    private int[][] waitingRoom;

    private int[][] world;

    // Getters
    public String getRoomName() { return roomName; }
    public int getUsersToStart() { return usersToStart; }
    public int[][] getWaitingRoom() { return waitingRoom; }
    public int[][] getWorld() { return world; }
}
package com.example.videoroom.signaling;

public class SessionInfo {
    private final String sessionId;
    private final String clientId;
    private final String roomId;

    public SessionInfo(String sessionId, String clientId, String roomId) {
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.roomId = roomId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRoomId() {
        return roomId;
    }
}

package com.example.videoroom.signaling;

public class SessionInfo {
    // WebSocket 세션 ID(연결마다 고유).
    private final String sessionId;
    // 시그널링 식별용으로 서버가 부여한 클라이언트 ID.
    private final String clientId;
    // 이 세션이 참가한 룸 ID.
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

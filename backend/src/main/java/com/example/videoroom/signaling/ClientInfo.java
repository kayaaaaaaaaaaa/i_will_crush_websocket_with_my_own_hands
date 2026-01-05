package com.example.videoroom.signaling;

import org.springframework.web.socket.WebSocketSession;

// 룸에 연결된 클라이언트의 정보를 담는 불변 객체.
public class ClientInfo {
    // 서버가 부여한 고유 클라이언트 ID.
    private final String id;
    // 입장 시 클라이언트가 보낸 표시 이름.
    private final String name;
    // 해당 클라이언트로 메시지를 보내기 위한 WebSocket 세션.
    private final WebSocketSession session;

    public ClientInfo(String id, String name, WebSocketSession session) {
        this.id = id;
        this.name = name;
        this.session = session;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WebSocketSession getSession() {
        return session;
    }
}

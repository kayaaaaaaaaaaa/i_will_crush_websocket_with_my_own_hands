package com.example.videoroom.signaling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SignalingWebSocketHandler extends TextWebSocketHandler {
    private final RoomService roomService;
    private final ObjectMapper objectMapper;

    public SignalingWebSocketHandler(RoomService roomService, ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // 들어온 JSON을 파싱하고 type에 따라 라우팅한다.
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        switch (type) {
            case "join" -> handleJoin(session, root);
            case "signal" -> handleSignal(session, root);
            case "leave" -> handleLeave(session);
            default -> sendError(session, "Unknown message type");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 소켓이 닫히면 룸 상태에서 제거한다.
        handleLeave(session);
        super.afterConnectionClosed(session, status);
    }

    private void handleJoin(WebSocketSession session, JsonNode root) throws IOException {
        // 필수 필드 검증.
        String roomId = root.path("roomId").asText();
        String name = root.path("name").asText();
        if (roomId.isBlank() || name.isBlank()) {
            sendError(session, "roomId and name are required");
            return;
        }

        RoomService.JoinResult join;
        try {
            join = roomService.join(roomId, name, session);
        } catch (IllegalStateException ex) {
            // 인원 초과 등 잘못된 상태.
            sendError(session, ex.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 먼저 환영 메시지(할당된 클라이언트 ID 포함)를 보낸다.
        Map<String, Object> welcome = new HashMap<>();
        welcome.put("type", "welcome");
        welcome.put("clientId", join.clientId());
        welcome.put("roomId", join.roomId());
        send(session, welcome);

        // 그 다음 기존 피어 목록을 보내 새 클라이언트가 offer를 시작할 수 있게 한다.
        Map<String, Object> peers = new HashMap<>();
        peers.put("type", "peers");
        peers.put("peers", join.peers());
        send(session, peers);

        Optional<Room> room = roomService.getRoom(roomId);
        if (room.isEmpty()) {
            return;
        }
        // 기존 클라이언트들에게 새 피어 입장을 알린다.
        for (ClientInfo client : room.get().getClients()) {
            if (client.getId().equals(join.clientId())) {
                continue;
            }
            Map<String, Object> peerJoined = new HashMap<>();
            peerJoined.put("type", "peer-joined");
            peerJoined.put("peer", new RoomService.ClientSummary(join.clientId(), name));
            send(client.getSession(), peerJoined);
        }
    }

    private void handleSignal(WebSocketSession session, JsonNode root) throws IOException {
        // 시그널은 1:1로 전달된다. to=대상, data=SDP/ICE.
        String to = root.path("to").asText();
        JsonNode data = root.path("data");
        if (to.isBlank() || data.isMissingNode()) {
            sendError(session, "to and data are required for signal");
            return;
        }

        Optional<SessionInfo> sessionInfo = roomService.getSessionInfo(session.getId());
        if (sessionInfo.isEmpty()) {
            // 룸에 참가하지 않은 상태에서 시그널을 보냄.
            sendError(session, "Not joined");
            return;
        }

        String roomId = sessionInfo.get().getRoomId();
        Optional<Room> room = roomService.getRoom(roomId);
        if (room.isEmpty()) {
            sendError(session, "Room not found");
            return;
        }

        // 같은 룸 안에서 대상 클라이언트를 찾는다.
        ClientInfo target = room.get().getClient(to);
        if (target == null) {
            sendError(session, "Target not found");
            return;
        }

        // 보낸 사람 ID를 포함해 시그널을 전달한다.
        Map<String, Object> signal = new HashMap<>();
        signal.put("type", "signal");
        signal.put("from", sessionInfo.get().getClientId());
        signal.put("data", data);
        send(target.getSession(), signal);
    }

    private void handleLeave(WebSocketSession session) throws IOException {
        // 룸 상태에서 제거하고 남은 피어에게 알린다.
        Optional<RoomService.LeaveResult> leave = roomService.leave(session.getId());
        if (leave.isEmpty()) {
            return;
        }
        Optional<Room> room = roomService.getRoom(leave.get().roomId());
        if (room.isEmpty()) {
            return;
        }
        // 남은 클라이언트들에게 peer-left를 브로드캐스트한다.
        for (ClientInfo client : room.get().getClients()) {
            Map<String, Object> peerLeft = new HashMap<>();
            peerLeft.put("type", "peer-left");
            peerLeft.put("peerId", leave.get().clientId());
            send(client.getSession(), peerLeft);
        }
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws IOException {
        // JSON으로 직렬화해 전송한다.
        String json = toJson(payload);
        session.sendMessage(new TextMessage(json));
    }

    private String toJson(Map<String, Object> payload) throws JsonProcessingException {
        // 일관된 포맷을 위해 한곳에서 JSON 변환.
        return objectMapper.writeValueAsString(payload);
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        // 클라이언트에서 처리 가능한 표준 에러 포맷.
        Map<String, Object> error = new HashMap<>();
        error.put("type", "error");
        error.put("message", message);
        send(session, error);
    }
}

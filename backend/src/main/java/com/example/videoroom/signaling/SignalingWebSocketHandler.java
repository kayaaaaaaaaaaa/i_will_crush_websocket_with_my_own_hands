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
        handleLeave(session);
        super.afterConnectionClosed(session, status);
    }

    private void handleJoin(WebSocketSession session, JsonNode root) throws IOException {
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
            sendError(session, ex.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Map<String, Object> welcome = new HashMap<>();
        welcome.put("type", "welcome");
        welcome.put("clientId", join.clientId());
        welcome.put("roomId", join.roomId());
        send(session, welcome);

        Map<String, Object> peers = new HashMap<>();
        peers.put("type", "peers");
        peers.put("peers", join.peers());
        send(session, peers);

        Optional<Room> room = roomService.getRoom(roomId);
        if (room.isEmpty()) {
            return;
        }
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
        String to = root.path("to").asText();
        JsonNode data = root.path("data");
        if (to.isBlank() || data.isMissingNode()) {
            sendError(session, "to and data are required for signal");
            return;
        }

        Optional<SessionInfo> sessionInfo = roomService.getSessionInfo(session.getId());
        if (sessionInfo.isEmpty()) {
            sendError(session, "Not joined");
            return;
        }

        String roomId = sessionInfo.get().getRoomId();
        Optional<Room> room = roomService.getRoom(roomId);
        if (room.isEmpty()) {
            sendError(session, "Room not found");
            return;
        }

        ClientInfo target = room.get().getClient(to);
        if (target == null) {
            sendError(session, "Target not found");
            return;
        }

        Map<String, Object> signal = new HashMap<>();
        signal.put("type", "signal");
        signal.put("from", sessionInfo.get().getClientId());
        signal.put("data", data);
        send(target.getSession(), signal);
    }

    private void handleLeave(WebSocketSession session) throws IOException {
        Optional<RoomService.LeaveResult> leave = roomService.leave(session.getId());
        if (leave.isEmpty()) {
            return;
        }
        Optional<Room> room = roomService.getRoom(leave.get().roomId());
        if (room.isEmpty()) {
            return;
        }
        for (ClientInfo client : room.get().getClients()) {
            Map<String, Object> peerLeft = new HashMap<>();
            peerLeft.put("type", "peer-left");
            peerLeft.put("peerId", leave.get().clientId());
            send(client.getSession(), peerLeft);
        }
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws IOException {
        String json = toJson(payload);
        session.sendMessage(new TextMessage(json));
    }

    private String toJson(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("type", "error");
        error.put("message", message);
        send(session, error);
    }
}

package com.example.videoroom.signaling;

import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RoomService {
    public static final int MAX_PARTICIPANTS = 6;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public JoinResult join(String roomId, String name, WebSocketSession session) {
        Room room = rooms.computeIfAbsent(roomId, Room::new);
        synchronized (room) {
            if (room.size() >= MAX_PARTICIPANTS) {
                throw new IllegalStateException("Room is full");
            }
            String clientId = UUID.randomUUID().toString();
            List<ClientSummary> peers = new ArrayList<>();
            for (ClientInfo client : room.getClients()) {
                peers.add(new ClientSummary(client.getId(), client.getName()));
            }
            room.addClient(new ClientInfo(clientId, name, session));
            sessions.put(session.getId(), new SessionInfo(session.getId(), clientId, roomId));
            return new JoinResult(roomId, clientId, peers);
        }
    }

    public Optional<LeaveResult> leave(String sessionId) {
        SessionInfo sessionInfo = sessions.remove(sessionId);
        if (sessionInfo == null) {
            return Optional.empty();
        }
        Room room = rooms.get(sessionInfo.getRoomId());
        if (room == null) {
            return Optional.empty();
        }
        ClientInfo removed;
        synchronized (room) {
            removed = room.removeClient(sessionInfo.getClientId());
            if (room.isEmpty()) {
                rooms.remove(room.getId());
            }
        }
        if (removed == null) {
            return Optional.empty();
        }
        return Optional.of(new LeaveResult(sessionInfo.getRoomId(), sessionInfo.getClientId()));
    }

    public Optional<SessionInfo> getSessionInfo(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Optional<Room> getRoom(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public record ClientSummary(String id, String name) {}

    public record JoinResult(String roomId, String clientId, List<ClientSummary> peers) {}

    public record LeaveResult(String roomId, String clientId) {}
}

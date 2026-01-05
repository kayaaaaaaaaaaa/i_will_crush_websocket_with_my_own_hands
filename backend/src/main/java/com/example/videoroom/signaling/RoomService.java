package com.example.videoroom.signaling;

import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RoomService {
    // 룸 최대 인원 제한.
    public static final int MAX_PARTICIPANTS = 6;

    // roomId로 조회하는 활성 룸 목록.
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    // sessionId에서 client/room 정보를 찾기 위한 맵.
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public JoinResult join(String roomId, String name, WebSocketSession session) {
        // 룸이 없으면 생성한다.
        Room room = rooms.computeIfAbsent(roomId, Room::new);
        // 인원 제한과 피어 목록 생성 시 경합을 막기 위해 룸 단위로 동기화.
        synchronized (room) {
            if (room.size() >= MAX_PARTICIPANTS) {
                throw new IllegalStateException("Room is full");
            }
            // 입장할 때마다 새로운 클라이언트 ID를 발급한다.
            String clientId = UUID.randomUUID().toString();
            // 기존 피어 목록을 스냅샷으로 전달한다.
            List<ClientSummary> peers = new ArrayList<>();
            for (ClientInfo client : room.getClients()) {
                peers.add(new ClientSummary(client.getId(), client.getName()));
            }
            // 룸에 새 클라이언트를 등록한다.
            room.addClient(new ClientInfo(clientId, name, session));
            // 해당 WebSocket 세션의 소속 룸을 기록한다.
            sessions.put(session.getId(), new SessionInfo(session.getId(), clientId, roomId));
            return new JoinResult(roomId, clientId, peers);
        }
    }

    public Optional<LeaveResult> leave(String sessionId) {
        // 중복 leave를 안전하게 처리하기 위해 먼저 세션 매핑을 제거한다.
        SessionInfo sessionInfo = sessions.remove(sessionId);
        if (sessionInfo == null) {
            return Optional.empty();
        }
        Room room = rooms.get(sessionInfo.getRoomId());
        if (room == null) {
            return Optional.empty();
        }
        ClientInfo removed;
        // 동시 join/leave 간 경합 방지용 동기화.
        synchronized (room) {
            removed = room.removeClient(sessionInfo.getClientId());
            if (room.isEmpty()) {
                // 빈 룸은 정리한다.
                rooms.remove(room.getId());
            }
        }
        if (removed == null) {
            return Optional.empty();
        }
        return Optional.of(new LeaveResult(sessionInfo.getRoomId(), sessionInfo.getClientId()));
    }

    public Optional<SessionInfo> getSessionInfo(String sessionId) {
        // WebSocket 세션에서 클라이언트 ID를 찾을 때 사용한다.
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Optional<Room> getRoom(String roomId) {
        // 룸 조회(이미 삭제됐으면 비어있을 수 있음).
        return Optional.ofNullable(rooms.get(roomId));
    }

    // 클라이언트에게 보내는 피어 목록용 최소 정보.
    public record ClientSummary(String id, String name) {}

    // 입장 응답(클라이언트 ID + 기존 피어 목록).
    public record JoinResult(String roomId, String clientId, List<ClientSummary> peers) {}

    // 퇴장 알림에 사용되는 결과.
    public record LeaveResult(String roomId, String clientId) {}
}

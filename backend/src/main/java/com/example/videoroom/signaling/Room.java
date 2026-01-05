package com.example.videoroom.signaling;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 현재 연결된 클라이언트를 담는 메모리 기반 룸.
public class Room {
    private final String id;
    // clientId를 키로 하는 맵(빠른 조회/삭제 목적).
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    public Room(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Collection<ClientInfo> getClients() {
        // 현재 클라이언트 목록의 뷰를 반환한다.
        return clients.values();
    }

    public ClientInfo getClient(String clientId) {
        // 없으면 null을 반환한다.
        return clients.get(clientId);
    }

    public int size() {
        // 현재 룸에 있는 총 클라이언트 수.
        return clients.size();
    }

    public void addClient(ClientInfo client) {
        // id 기준으로 추가/갱신한다.
        clients.put(client.getId(), client);
    }

    public ClientInfo removeClient(String clientId) {
        // 해당 클라이언트를 제거하고 반환한다.
        return clients.remove(clientId);
    }

    public boolean isEmpty() {
        // 빈 룸 정리용 체크.
        return clients.isEmpty();
    }
}

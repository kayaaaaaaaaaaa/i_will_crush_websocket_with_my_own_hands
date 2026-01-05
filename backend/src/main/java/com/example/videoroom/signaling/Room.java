package com.example.videoroom.signaling;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Room {
    private final String id;
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    public Room(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Collection<ClientInfo> getClients() {
        return clients.values();
    }

    public ClientInfo getClient(String clientId) {
        return clients.get(clientId);
    }

    public int size() {
        return clients.size();
    }

    public void addClient(ClientInfo client) {
        clients.put(client.getId(), client);
    }

    public ClientInfo removeClient(String clientId) {
        return clients.remove(clientId);
    }

    public boolean isEmpty() {
        return clients.isEmpty();
    }
}

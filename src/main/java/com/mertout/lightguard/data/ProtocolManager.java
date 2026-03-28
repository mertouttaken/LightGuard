package com.mertout.lightguard.data;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolManager {

    private final Map<UUID, ProtocolPlayer> players = new ConcurrentHashMap<>();

    public void add(Player player) {
        players.put(player.getUniqueId(), new ProtocolPlayer(player));
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public ProtocolPlayer get(UUID uuid) {
        return players.get(uuid);
    }

    public Map<UUID, ProtocolPlayer> getPlayers() {
        return players;
    }
}
package com.mertout.lightguard.data;

import com.mertout.lightguard.LightGuard;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolManager {

    private final LightGuard plugin;
    private final Map<UUID, ProtocolPlayer> players = new ConcurrentHashMap<>();

    public ProtocolManager(LightGuard plugin) {
        this.plugin = plugin;
    }

    public void add(Player player) {
        players.put(player.getUniqueId(), new ProtocolPlayer(player, plugin));
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
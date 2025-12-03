package com.mertout.lightguard.data;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();

    public PlayerData getData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public void createData(Player player) {
        dataMap.put(player.getUniqueId(), new PlayerData(player));
    }

    public void removeData(UUID uuid) {
        dataMap.remove(uuid);
    }
}
package com.mertout.lightguard.data;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolManager {

    // Kilit-siz (Lock-free) ConcurrentHashMap: Netty ve Bukkit aynı anda erişebilir.
    private final Map<UUID, ProtocolPlayer> players = new ConcurrentHashMap<>();

    /**
     * Oyuncu katıldığında yeni bir veri objesi oluşturur.
     */
    public void add(Player player) {
        players.put(player.getUniqueId(), new ProtocolPlayer(player));
    }

    /**
     * Oyuncu çıktığında veriyi temizler (Bellek sızıntısını önler).
     */
    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    /**
     * Veriyi getirir.
     */
    public ProtocolPlayer get(UUID uuid) {
        return players.get(uuid);
    }

    public Map<UUID, ProtocolPlayer> getPlayers() {
        return players;
    }
}
package com.mertout.lightguard.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldChunkManager implements Listener {

    // Dünya Adı -> Yüklü Chunk'ların (Long formatında) Kilit-siz (Lock-Free) Set'i
    private final Map<String, Set<Long>> loadedChunks = new ConcurrentHashMap<>();

    // X ve Z koordinatlarını tek bir Long primitive'e sıkıştırır (Sıfır obje alokasyonu)
    public static long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        String worldName = event.getWorld().getName();
        long key = getChunkKey(event.getChunk().getX(), event.getChunk().getZ());

        loadedChunks.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        String worldName = event.getWorld().getName();
        long key = getChunkKey(event.getChunk().getX(), event.getChunk().getZ());

        Set<Long> chunks = loadedChunks.get(worldName);
        if (chunks != null) {
            chunks.remove(key);
        }
    }

    /**
     * Netty Thread'i tarafından GÜVENLE çağrılabilir. (Lock-Free O(1) sorgu)
     */
    public boolean isChunkLoadedAsynchronously(String worldName, int chunkX, int chunkZ) {
        Set<Long> chunks = loadedChunks.get(worldName);
        if (chunks == null) return false;
        return chunks.contains(getChunkKey(chunkX, chunkZ));
    }
}
package com.mertout.lightguard.listeners;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldChunkManager implements Listener {

    private final Map<String, Set<Long>> loadedChunks = new ConcurrentHashMap<>();

    public WorldChunkManager(LightGuard plugin) {
        bootstrap();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void bootstrap() {
        int cachedCount = 0;
        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            Set<Long> worldChunks = loadedChunks.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());

            for (Chunk chunk : world.getLoadedChunks()) {
                worldChunks.add(getChunkKey(chunk.getX(), chunk.getZ()));
                cachedCount++;
            }
        }
        Bukkit.getLogger().info("[LightGuard] Sentinel: " + cachedCount + " pre-loaded chunks cached for async inspection.");
    }

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

    public boolean isChunkLoadedAsynchronously(String worldName, int chunkX, int chunkZ) {
        Set<Long> chunks = loadedChunks.get(worldName);
        if (chunks == null) return false;
        return chunks.contains(getChunkKey(chunkX, chunkZ));
    }

    public void clearCache() {
        loadedChunks.clear();
    }
}
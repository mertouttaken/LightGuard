package com.mertout.lightguard.listeners;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldChunkManager implements Listener {

    private final LightGuard plugin;
    private final Map<String, Set<Long>> loadedChunks = new ConcurrentHashMap<>();

    public WorldChunkManager(LightGuard plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskLater(plugin, this::bootstrap, 20L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void bootstrap() {
        int cachedCount = 0;
        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();

            loadedChunks.putIfAbsent(worldName, ConcurrentHashMap.newKeySet());
            Set<Long> worldChunks = loadedChunks.get(worldName);

            for (Chunk chunk : world.getLoadedChunks()) {
                worldChunks.add(getChunkKey(chunk.getX(), chunk.getZ()));
                cachedCount++;
            }
        }
        plugin.getLogger().info(cachedCount + " loaded chunks cached for async inspection.");
    }

    public static long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        String worldName = event.getWorld().getName();
        long key = getChunkKey(event.getChunk().getX(), event.getChunk().getZ());

        loadedChunks.putIfAbsent(worldName, ConcurrentHashMap.newKeySet());
        loadedChunks.get(worldName).add(key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
        return chunks != null && chunks.contains(getChunkKey(chunkX, chunkZ));
    }

    /**
     * Rebuilds the cache from the currently loaded chunks. Clearing without rebuilding would make
     * every chunk look unloaded, which cancels all movement and kicks players on position packets.
     */
    public void clearCache() {
        if (Bukkit.isPrimaryThread()) {
            rebuild();
        } else {
            Bukkit.getScheduler().runTask(plugin, this::rebuild);
        }
    }

    private void rebuild() {
        loadedChunks.clear();
        bootstrap();
    }
}
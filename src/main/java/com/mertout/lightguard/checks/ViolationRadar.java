package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ViolationRadar {

    private final LightGuard plugin;

    private volatile int burstThreshold;
    private volatile long windowMillis;

    private final ConcurrentHashMap<UUID, PlayerRadar> radars = new ConcurrentHashMap<>();

    public ViolationRadar(LightGuard plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.burstThreshold = Math.max(1, plugin.getConfig().getInt("settings.radar.burst-threshold", 12));
        this.windowMillis = Math.max(250L, plugin.getConfig().getLong("settings.radar.window-ms", 4000L));
    }

    public void recordViolation(ProtocolPlayer player, String checkName) {
        PlayerRadar radar = radars.computeIfAbsent(player.getUuid(), k -> new PlayerRadar());
        long now = System.currentTimeMillis();

        synchronized (radar) {
            long windowStart = radar.windowStart.get();
            if (now - windowStart > windowMillis) {
                radar.windowStart.set(now);
                radar.count.set(0);
            }

            int count = radar.count.incrementAndGet();

            if (count >= burstThreshold) {
                radar.count.set(0);
                radar.windowStart.set(now);

                plugin.getActionQueue().pushKick(player.getPlayer(),
                        "Sustained Exploit Pattern (" + count + " blocked packets / " + (windowMillis / 1000.0) + "s)");
            }
        }
    }

    public void clearViolations(UUID uuid) {
        radars.remove(uuid);
    }

    private static final class PlayerRadar {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong windowStart = new AtomicLong(0);
    }
}
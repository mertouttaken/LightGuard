package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public class FloodCheck extends Check {

    private static final Map<Class<?>, String> PACKET_NAME_CACHE = new ConcurrentHashMap<>();

    private final int maxGlobalPPS;
    private final int burstLimit;
    private final int maxBytesPerSec;
    private final boolean matrixEnabled;
    private final double maxMatrixWeight;

    private final Map<String, Double> weights = new HashMap<>();
    private final Map<String, LimitConfig> limits = new HashMap<>();

    private final AtomicLong lastCheck = new AtomicLong(System.nanoTime() / 1000000L);
    private final AtomicLong lastBurstCheck = new AtomicLong(System.nanoTime() / 1000000L);
    private final AtomicLong lastByteCheck = new AtomicLong(System.nanoTime() / 1000000L);
    private final AtomicLong lastMicroCheck = new AtomicLong(System.nanoTime() / 1000000L);

    private final AtomicInteger globalPacketCount = new AtomicInteger(0);
    private final AtomicInteger burstCount = new AtomicInteger(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final DoubleAdder currentWeight = new DoubleAdder();
    private final Map<String, PacketTracker> packetTrackers = new ConcurrentHashMap<>();
    private final AtomicInteger microPacketCount = new AtomicInteger(0);

    public FloodCheck(PlayerData data) {
        super(data, "Flood", "flood");
        this.maxGlobalPPS = plugin.getConfig().getInt("checks.flood.max-global-pps", 600);
        this.burstLimit = plugin.getConfig().getInt("checks.flood.burst-limit", 400);
        this.maxBytesPerSec = plugin.getConfig().getInt("checks.flood.max-bytes-per-sec", 40000);
        this.matrixEnabled = plugin.getConfig().getBoolean("checks.flood.matrix.enabled");
        this.maxMatrixWeight = plugin.getConfig().getDouble("checks.flood.matrix.max-weight-per-sec", 1000.0);
        loadWeights();
        loadLimits();
    }

    private void loadWeights() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("checks.flood.matrix.weights");
        if (sec != null) for (String key : sec.getKeys(false)) weights.put(key, sec.getDouble(key));
    }

    private void loadLimits() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("checks.flood.limits");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    String val = sec.getString(key);
                    String[] parts = val.split("/");
                    limits.put(key, new LimitConfig(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                } catch (Exception e) {}
            }
        }
    }

    private String getPacketName(Object packet) {
        return PACKET_NAME_CACHE.computeIfAbsent(packet.getClass(), Class::getSimpleName);
    }

    public void cleanup() {
        long now = System.nanoTime() / 1000000L;
        packetTrackers.entrySet().removeIf(entry -> (now - entry.getValue().lastTime.get()) > 60000);
    }

    private boolean checkAndReset(AtomicLong lastTimeAtom, long now, long interval) {
        long lastTime = lastTimeAtom.get();
        if (now - lastTime > interval) {
            return lastTimeAtom.compareAndSet(lastTime, now);
        }
        return false;
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        long now = System.nanoTime() / 1000000L;
        String packetName = getPacketName(packet);

        double multiplier = 1.0;
        double tps = plugin.getTPS();
        if (tps < 18.0) multiplier = 1.5; else if (tps < 19.5) multiplier = 1.2;

        if (checkAndReset(lastCheck, now, 1000)) {
            data.setPPS(globalPacketCount.get());
            globalPacketCount.set(0);
            currentWeight.reset();
            cleanup();
        }

        if (checkAndReset(lastMicroCheck, now, 100)) {
            microPacketCount.set(0);
        }

        int currentMicro = microPacketCount.incrementAndGet();
        int microLimit = Math.max(20, (int)((maxGlobalPPS * multiplier) / 5));

        if (currentMicro > microLimit) {
            flag("Micro-Burst Flood (" + currentMicro + "/100ms)", packetName);
            return false;
        }

        int currentGlobal = globalPacketCount.incrementAndGet();
        if (currentGlobal > (maxGlobalPPS * multiplier)) return false;

        if (matrixEnabled) {
            double weight = weights.getOrDefault(packetName, weights.getOrDefault("default", 5.0));
            currentWeight.add(weight);
            if (currentWeight.sum() > (maxMatrixWeight * multiplier)) return false;
        }

        if (checkAndReset(lastBurstCheck, now, 500)) {
            burstCount.set(0);
        }
        int currentBurst = burstCount.incrementAndGet();
        if (currentBurst > (burstLimit * multiplier)) {
            flag("Packet Burst (" + currentBurst + ")", packetName);
            return false;
        }

        if (checkAndReset(lastByteCheck, now, 1000)) {
            totalBytes.set(0);
        }
        long size = estimatePacketSize(packet);
        long currentBytes = totalBytes.addAndGet(size);
        if (currentBytes > (maxBytesPerSec * multiplier)) {
            flag("High Traffic (" + currentBytes/1024 + " KB/s)", packetName);
            return false;
        }

        LimitConfig limitConfig = limits.get(packetName);
        if (limitConfig != null) {
            PacketTracker tracker = packetTrackers.computeIfAbsent(packetName, k -> new PacketTracker());
            long lastTime = tracker.lastTime.get();
            if (now - lastTime > limitConfig.interval) {
                if (tracker.lastTime.compareAndSet(lastTime, now)) {
                    tracker.count.set(0);
                }
            }
            int count = tracker.count.incrementAndGet();
            if (count > (int)(limitConfig.max * multiplier)) {
                if (packetName.contains("KeepAlive") || packetName.contains("Transaction")) {
                    flag("Disabler Attempt", packetName);
                } else {
                    flag("Rate Limit: " + count + "/" + limitConfig.interval + "ms", packetName);
                }
                return false;
            }
        }
        return true;
    }

    private int estimatePacketSize(Object packet) {
        if (packet instanceof PacketPlayInCustomPayload) {
            try { return ((PacketPlayInCustomPayload) packet).data.readableBytes(); } catch (Exception e) { return 0; }
        }
        if (packet instanceof PacketPlayInWindowClick) return 32;
        if (packet instanceof PacketPlayInFlying) return 24;
        return 10;
    }

    private static class PacketTracker {
        final AtomicLong lastTime = new AtomicLong(System.nanoTime() / 1000000L);
        final AtomicInteger count = new AtomicInteger(0);
    }
    private static class LimitConfig {
        final int max;
        final int interval;
        LimitConfig(int max, int interval) { this.max = max; this.interval = interval; }
    }
}
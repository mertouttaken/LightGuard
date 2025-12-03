package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class FloodCheck extends Check {

    // Zamanlayıcılar
    private long lastCheck;       // Matrix & Global için
    private long lastBurstCheck;  // Burst için
    private long lastByteCheck;   // Bandwidth için

    // Sayaçlar
    private double currentWeight; // Matrix puanı
    private int globalPacketCount; // Toplam paket sayısı
    private int burstCount;        // Anlık paket sayısı
    private long totalBytes;       // Toplam veri boyutu

    // Ağırlık ve Takip Listeleri
    private final Map<String, Double> packetWeights = new HashMap<>();
    private final Map<String, PacketTracker> packetTrackers = new HashMap<>();

    public FloodCheck(PlayerData data) {
        super(data, "Flood");
        long now = System.currentTimeMillis();
        this.lastCheck = now;
        this.lastBurstCheck = now;
        this.lastByteCheck = now;

        loadWeights();
    }

    private void loadWeights() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("checks.flood.matrix.weights");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                packetWeights.put(key, sec.getDouble(key));
            }
        }
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.flood.enabled")) return true;

        long now = System.currentTimeMillis();
        String packetName = packet.getClass().getSimpleName(); // Paket ismini alıyoruz

        // ➤ 1. ADAPTIVE CONTROL
        double multiplier = 1.0;
        if (plugin.getConfig().getBoolean("checks.flood.adaptive.enabled")) {
            double tps = plugin.getTPS();
            double lowTps = plugin.getConfig().getDouble("checks.flood.adaptive.low-tps-threshold", 18.0);

            if (tps < lowTps) {
                multiplier = plugin.getConfig().getDouble("checks.flood.adaptive.low-tps-multiplier", 0.7);
            } else if (tps > 19.8) {
                multiplier = plugin.getConfig().getDouble("checks.flood.adaptive.high-tps-multiplier", 1.2);
            }

            if (Bukkit.getOnlinePlayers().size() > plugin.getConfig().getInt("checks.flood.adaptive.high-player-threshold", 50)) {
                multiplier *= plugin.getConfig().getDouble("checks.flood.adaptive.high-player-multiplier", 1.1);
            }
        }

        // ➤ 2. GLOBAL FLOOD (Flood-G)
        if (now - lastCheck > 1000) {
            data.setPPS(globalPacketCount);
            globalPacketCount = 0;
            currentWeight = 0;
            lastCheck = now;
        }
        globalPacketCount++;

        int globalLimit = (int) (plugin.getConfig().getInt("checks.flood.max-global-pps", 800) * multiplier);
        if (globalPacketCount > globalLimit) {
            // Global limit sessizce iptal eder (Kick atmaz)
            return false;
        }

        // ➤ 3. INTERACTION MATRIX
        if (plugin.getConfig().getBoolean("checks.flood.matrix.enabled")) {
            double weight = packetWeights.getOrDefault(packetName, packetWeights.getOrDefault("default", 5.0));
            currentWeight += weight;

            double maxWeight = plugin.getConfig().getDouble("checks.flood.matrix.max-weight-per-sec", 1000.0) * multiplier;
            if (currentWeight > maxWeight) {
                // Matrix aşımı sessiz iptal (Lag spike önlemi)
                return false;
            }
        }

        // ➤ 4. BURST DETECTOR (FIX: Paket İsmi Eklendi)
        if (now - lastBurstCheck > 500) {
            burstCount = 0;
            lastBurstCheck = now;
        }
        burstCount++;

        int burstLimit = (int) (plugin.getConfig().getInt("checks.flood.burst-limit", 500) * multiplier);
        if (burstCount > burstLimit) {
            // DÜZELTME: packetName parametresi eklendi!
            flag("Packet Burst Detected (" + burstCount + ")", packetName);
            return false;
        }

        // ➤ 5. BYTE/BANDWIDTH LIMITER (FIX: Paket İsmi Eklendi)
        if (now - lastByteCheck > 1000) {
            totalBytes = 0;
            lastByteCheck = now;
        }
        totalBytes += estimatePacketSize(packet);

        int byteLimit = (int) (plugin.getConfig().getInt("checks.flood.max-bytes-per-sec", 35000) * multiplier);
        if (totalBytes > byteLimit) {
            // DÜZELTME: packetName parametresi eklendi!
            flag("High Traffic Flood (" + totalBytes + " bytes/s)", packetName);
            return false;
        }

        // ➤ 6. PER-TYPE LIMITS (FIX: Paket İsmi Eklendi)
        String configKey = "checks.flood.limits." + packetName;
        if (plugin.getConfig().contains(configKey)) {
            String limitStr = plugin.getConfig().getString(configKey);
            try {
                String[] parts = limitStr.split("/");
                int maxPackets = (int) (Integer.parseInt(parts[0]) * multiplier);
                int timeWindow = Integer.parseInt(parts[1]);

                PacketTracker tracker = packetTrackers.computeIfAbsent(packetName, k -> new PacketTracker());

                if (now - tracker.lastTime > timeWindow) {
                    tracker.count = 0;
                    tracker.lastTime = now;
                }
                tracker.count++;

                if (tracker.count > maxPackets) {
                    if (packetName.contains("KeepAlive") || packetName.contains("Transaction")) {
                        // DÜZELTME: packetName parametresi eklendi!
                        flag("Anticheat Disabler Attempt", packetName);
                    } else {
                        // DÜZELTME: packetName parametresi eklendi!
                        flag("Rate Limit: " + tracker.count + "/" + timeWindow + "ms", packetName);
                    }
                    return false;
                }
            } catch (Exception ignored) {}
        }

        return true;
    }

    private int estimatePacketSize(Object packet) {
        if (packet instanceof PacketPlayInCustomPayload) {
            return ((PacketPlayInCustomPayload) packet).data.readableBytes();
        }
        if (packet instanceof PacketPlayInWindowClick) return 32;
        if (packet instanceof PacketPlayInFlying) return 24;
        return 10;
    }

    private static class PacketTracker {
        long lastTime = System.currentTimeMillis();
        int count = 0;
    }
}
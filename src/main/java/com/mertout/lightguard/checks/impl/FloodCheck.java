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

    // ➤ CONFIG CACHE (Performans için final değişkenler)
    private final boolean enabled;
    private final int maxGlobalPPS;
    private final int burstLimit;
    private final int maxBytesPerSec;
    private final boolean matrixEnabled;
    private final double maxMatrixWeight;

    // ➤ DATA CACHE (Ağırlıklar ve Limitler RAM'de tutulur)
    private final Map<String, Double> weights = new HashMap<>();
    private final Map<String, LimitConfig> limits = new HashMap<>();

    // ➤ ATOMIC TRACKERS (Thread-Safe Sayaçlar)
    private final AtomicLong lastCheck = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastBurstCheck = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastByteCheck = new AtomicLong(System.currentTimeMillis());

    private final AtomicInteger globalPacketCount = new AtomicInteger(0);
    private final AtomicInteger burstCount = new AtomicInteger(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final DoubleAdder currentWeight = new DoubleAdder();

    // ➤ PAKET BAZLI TAKİP (ConcurrentMap)
    private final Map<String, PacketTracker> packetTrackers = new ConcurrentHashMap<>();

    // Temizlik zamanlayıcısı (RAM şişmesini önler)
    private long lastCleanupTime = System.currentTimeMillis();

    public FloodCheck(PlayerData data) {
        super(data, "Flood");

        // 1. Temel Ayarları Cache'le (Her pakette okumamak için)
        this.enabled = plugin.getConfig().getBoolean("checks.flood.enabled");
        this.maxGlobalPPS = plugin.getConfig().getInt("checks.flood.max-global-pps", 600);
        this.burstLimit = plugin.getConfig().getInt("checks.flood.burst-limit", 400);
        this.maxBytesPerSec = plugin.getConfig().getInt("checks.flood.max-bytes-per-sec", 40000);

        this.matrixEnabled = plugin.getConfig().getBoolean("checks.flood.matrix.enabled");
        this.maxMatrixWeight = plugin.getConfig().getDouble("checks.flood.matrix.max-weight-per-sec", 1000.0);

        // 2. Ağırlıkları Yükle
        loadWeights();

        // 3. Özel Limitleri Yükle (Parse işlemi burada yapılır, pakette değil)
        loadLimits();
    }

    private void loadWeights() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("checks.flood.matrix.weights");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                weights.put(key, sec.getDouble(key));
            }
        }
    }

    private void loadLimits() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("checks.flood.limits");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    String val = sec.getString(key);
                    String[] parts = val.split("/");
                    int max = Integer.parseInt(parts[0]);
                    int interval = Integer.parseInt(parts[1]);
                    limits.put(key, new LimitConfig(max, interval));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid limit format for " + key + ": " + sec.getString(key));
                }
            }
        }
    }

    @Override
    public boolean check(Object packet) {
        if (!enabled) return true;

        long now = System.currentTimeMillis();
        String packetName = packet.getClass().getSimpleName();

        // ➤ 0. MEMORY CLEANUP (Dakikada bir)
        if (now - lastCleanupTime > 60000) {
            packetTrackers.entrySet().removeIf(entry -> (now - entry.getValue().lastTime.get()) > 60000);
            lastCleanupTime = now;
        }

        // ➤ 1. ADAPTIVE TOLERANCE (Lag/TPS Koruması)
        // Eğer TPS düşükse limitleri gevşet (False Positive önle)
        double multiplier = 1.0;
        double tps = plugin.getTPS(); // LightGuard.java'daki volatile değişkenden okur

        if (tps < 18.0) {
            multiplier = 1.5; // %50 daha fazla paket izni
        } else if (tps < 19.5) {
            multiplier = 1.2; // %20 daha fazla paket izni
        }

        // ➤ 2. GLOBAL PPS CHECK
        if (now - lastCheck.get() > 1000) {
            data.setPPS(globalPacketCount.get()); // İstatistik için
            globalPacketCount.set(0);
            currentWeight.reset();
            lastCheck.set(now);
        }

        int currentGlobal = globalPacketCount.incrementAndGet();
        if (currentGlobal > (maxGlobalPPS * multiplier)) {
            // Global limit genelde lag spike yüzünden de tetiklenebilir,
            // direkt kicklemek yerine paketi iptal etmek daha güvenlidir.
            return false;
        }

        // ➤ 3. MATRIX (WEIGHT) CHECK
        if (matrixEnabled) {
            double weight = weights.getOrDefault(packetName, weights.getOrDefault("default", 5.0));
            currentWeight.add(weight);

            if (currentWeight.sum() > (maxMatrixWeight * multiplier)) {
                return false;
            }
        }

        // ➤ 4. BURST CHECK (Anlık Patlama)
        if (now - lastBurstCheck.get() > 500) { // Yarım saniye
            burstCount.set(0);
            lastBurstCheck.set(now);
        }

        int currentBurst = burstCount.incrementAndGet();
        if (currentBurst > (burstLimit * multiplier)) {
            flag("Packet Burst (" + currentBurst + ")", packetName);
            return false;
        }

        // ➤ 5. BANDWIDTH (BYTE) CHECK
        if (now - lastByteCheck.get() > 1000) {
            totalBytes.set(0);
            lastByteCheck.set(now);
        }

        long size = estimatePacketSize(packet);
        long currentBytes = totalBytes.addAndGet(size);

        if (currentBytes > (maxBytesPerSec * multiplier)) {
            flag("High Traffic (" + currentBytes/1024 + " KB/s)", packetName);
            return false;
        }

        // ➤ 6. PER-TYPE LIMITS (Özel Paket Limitleri)
        // Cache'lenmiş "LimitConfig" kullanıyoruz, her seferinde parse etmiyoruz.
        LimitConfig limitConfig = limits.get(packetName);
        if (limitConfig != null) {
            PacketTracker tracker = packetTrackers.computeIfAbsent(packetName, k -> new PacketTracker());

            long lastTime = tracker.lastTime.get();
            if (now - lastTime > limitConfig.interval) {
                tracker.count.set(0);
                tracker.lastTime.set(now);
            }

            int count = tracker.count.incrementAndGet();
            int maxAllowed = (int) (limitConfig.max * multiplier);

            if (count > maxAllowed) {
                // Anti-Disabler ve Crash paketleri için özel uyarı
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
            try {
                return ((PacketPlayInCustomPayload) packet).data.readableBytes();
            } catch (Exception e) { return 0; }
        }
        if (packet instanceof PacketPlayInWindowClick) return 32;
        if (packet instanceof PacketPlayInFlying) return 24;
        return 10;
    }

    // --- YARDIMCI SINIFLAR ---

    // Thread-Safe Takipçi
    private static class PacketTracker {
        final AtomicLong lastTime = new AtomicLong(System.currentTimeMillis());
        final AtomicInteger count = new AtomicInteger(0);
    }

    // Limit Ayarları Cache Yapısı
    private static class LimitConfig {
        final int max;
        final int interval;

        LimitConfig(int max, int interval) {
            this.max = max;
            this.interval = interval;
        }
    }
}
package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public class FloodCheck extends Check {

    // --- ZAMANLAYICILAR (Thread-Safe AtomicLong) ---
    // Son kontrol zamanlarını milisaniye cinsinden tutar
    private final AtomicLong lastCheck = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastBurstCheck = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastByteCheck = new AtomicLong(System.currentTimeMillis());

    // --- SAYAÇLAR (Thread-Safe Atomic Variables) ---
    // DoubleAdder, çok sık yazılan double değerler için AtomicDouble'dan daha hızlıdır
    private final DoubleAdder currentWeight = new DoubleAdder();

    // Global paket sayacı
    private final AtomicInteger globalPacketCount = new AtomicInteger(0);

    // Anlık (Burst) paket sayacı
    private final AtomicInteger burstCount = new AtomicInteger(0);

    // Byte (Bandwidth) sayacı
    private final AtomicLong totalBytes = new AtomicLong(0);

    // --- THREAD-SAFE HARİTALAR ---
    // Paket ağırlıklarını okumak için
    private final Map<String, Double> packetWeights = new ConcurrentHashMap<>();

    // Her paket tipi için ayrı rate-limit takibi
    private final Map<String, PacketTracker> packetTrackers = new ConcurrentHashMap<>();

    // --- TEMİZLİK ---
    // RAM sızıntısını önlemek için uzun süre işlem görmeyen trackerları temizleriz
    private static final long CLEANUP_THRESHOLD = 60000; // 60 saniye
    private long lastCleanupTime = System.currentTimeMillis(); // Bu main thread'de veya nadiren çalışacağı için long kalabilir

    public FloodCheck(PlayerData data) {
        super(data, "Flood");
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
        String packetName = packet.getClass().getSimpleName();

        // ➤ 0. MEMORY CLEANUP (Basit RAM Koruması)
        // Çok sık çalışmaz (dakikada bir), thread-safe map üzerinde removeIf güvenlidir.
        if (now - lastCleanupTime > CLEANUP_THRESHOLD) {
            packetTrackers.entrySet().removeIf(entry -> (now - entry.getValue().lastTime.get()) > CLEANUP_THRESHOLD);
            lastCleanupTime = now;
        }

        // ➤ 1. ADAPTIVE CONTROL (TPS Duyarlı Çarpan)
        double multiplier = calculateAdaptiveMultiplier();

        // ➤ 2. GLOBAL FLOOD (Saniyelik Reset)
        // Atomic compare işlemine gerek yok, basitçe zaman farkına bakıyoruz.
        // Race condition olsa bile (iki thread aynı anda girse) en fazla bir kez fazladan sıfırlanır, güvenlik açığı yaratmaz.
        if (now - lastCheck.get() > 1000) {
            data.setPPS(globalPacketCount.get()); // İstatistik için kaydet

            // Atomik Resetleme İşlemleri
            globalPacketCount.set(0);
            currentWeight.reset(); // DoubleAdder sıfırlama
            lastCheck.set(now);
        }

        // Atomik Artırma (Lock-free)
        int currentGlobal = globalPacketCount.incrementAndGet();
        int globalLimit = (int) (plugin.getConfig().getInt("checks.flood.max-global-pps", 800) * multiplier);

        if (currentGlobal > globalLimit) {
            // Global limit genelde lag spike yaratır, sessizce iptal ediyoruz.
            return false;
        }

        // ➤ 3. INTERACTION MATRIX (Ağırlıklı Kontrol)
        if (plugin.getConfig().getBoolean("checks.flood.matrix.enabled")) {
            double weight = packetWeights.getOrDefault(packetName, packetWeights.getOrDefault("default", 5.0));
            currentWeight.add(weight);

            double maxWeight = plugin.getConfig().getDouble("checks.flood.matrix.max-weight-per-sec", 1000.0) * multiplier;
            if (currentWeight.sum() > maxWeight) {
                return false;
            }
        }

        // ➤ 4. BURST DETECTOR (Anlık Patlama)
        if (now - lastBurstCheck.get() > 500) { // Yarım saniyelik pencereler
            burstCount.set(0);
            lastBurstCheck.set(now);
        }

        int currentBurst = burstCount.incrementAndGet();
        int burstLimit = (int) (plugin.getConfig().getInt("checks.flood.burst-limit", 500) * multiplier);

        if (currentBurst > burstLimit) {
            flag("Packet Burst Detected (" + currentBurst + ")", packetName);
            return false;
        }

        // ➤ 5. BYTE/BANDWIDTH LIMITER (Veri Boyutu)
        if (now - lastByteCheck.get() > 1000) {
            totalBytes.set(0);
            lastByteCheck.set(now);
        }

        // Paket boyutunu tahmin et
        long packetSize = estimatePacketSize(packet);
        long currentBytes = totalBytes.addAndGet(packetSize); // Atomik ekle ve oku

        int byteLimit = (int) (plugin.getConfig().getInt("checks.flood.max-bytes-per-sec", 35000) * multiplier);
        if (currentBytes > byteLimit) {
            flag("High Traffic Flood (" + currentBytes + " bytes/s)", packetName);
            return false;
        }

        // ➤ 6. PER-TYPE LIMITS (Paket Bazlı Tracker)
        if (!checkPerTypeLimit(packetName, now, multiplier)) {
            return false;
        }

        return true;
    }

    // Paket bazlı limit kontrolü (Ayrı metoda alındı)
    private boolean checkPerTypeLimit(String packetName, long now, double multiplier) {
        String configKey = "checks.flood.limits." + packetName;

        // Config kontrolü her seferinde yapmak maliyetli olabilir,
        // production'da bu map cache'lenmelidir. Şimdilik güvenli yolu seçiyoruz.
        if (plugin.getConfig().contains(configKey)) {
            try {
                String limitStr = plugin.getConfig().getString(configKey);
                String[] parts = limitStr.split("/");
                int maxPackets = (int) (Integer.parseInt(parts[0]) * multiplier);
                int timeWindow = Integer.parseInt(parts[1]);

                // Atomik Tracker Oluşturma/Alma
                PacketTracker tracker = packetTrackers.computeIfAbsent(packetName, k -> new PacketTracker());

                // Zaman penceresi kontrolü
                long lastTime = tracker.lastTime.get();
                if (now - lastTime > timeWindow) {
                    // Pencere doldu, sıfırla
                    tracker.count.set(0);
                    tracker.lastTime.set(now);
                }

                int typeCount = tracker.count.incrementAndGet();

                if (typeCount > maxPackets) {
                    // Özel mesajlar
                    if (packetName.contains("KeepAlive") || packetName.contains("Transaction")) {
                        flag("Anticheat Disabler Attempt", packetName);
                    } else {
                        flag("Rate Limit: " + typeCount + "/" + timeWindow + "ms", packetName);
                    }
                    return false;
                }
            } catch (Exception ignored) {}
        }
        return true;
    }

    private double calculateAdaptiveMultiplier() {
        if (!plugin.getConfig().getBoolean("checks.flood.adaptive.enabled")) return 1.0;

        double tps = plugin.getTPS();
        double multiplier = 1.0;

        double lowTps = plugin.getConfig().getDouble("checks.flood.adaptive.low-tps-threshold", 18.0);

        if (tps < lowTps) {
            multiplier = plugin.getConfig().getDouble("checks.flood.adaptive.low-tps-multiplier", 0.7);
        } else if (tps > 19.8) {
            multiplier = plugin.getConfig().getDouble("checks.flood.adaptive.high-tps-multiplier", 1.2);
        }

        if (Bukkit.getOnlinePlayers().size() > plugin.getConfig().getInt("checks.flood.adaptive.high-player-threshold", 50)) {
            multiplier *= plugin.getConfig().getDouble("checks.flood.adaptive.high-player-multiplier", 1.1);
        }
        return multiplier;
    }

    private int estimatePacketSize(Object packet) {
        if (packet instanceof PacketPlayInCustomPayload) {
            try {
                // Readable bytes Netty metodudur
                return ((PacketPlayInCustomPayload) packet).data.readableBytes();
            } catch (Exception e) {
                return 0;
            }
        }
        if (packet instanceof PacketPlayInWindowClick) return 32;
        if (packet instanceof PacketPlayInFlying) return 24;
        return 10; // Varsayılan küçük boyut
    }

    // Thread-Safe İç İçe Sınıf
    private static class PacketTracker {
        final AtomicLong lastTime = new AtomicLong(System.currentTimeMillis());
        final AtomicInteger count = new AtomicInteger(0);
    }
}
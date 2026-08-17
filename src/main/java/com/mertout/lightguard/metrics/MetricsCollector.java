package com.mertout.lightguard.metrics;

import com.mertout.lightguard.LightGuard;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {
    private final long startTime;
    private final AtomicLong totalPackets = new AtomicLong(0);
    private final AtomicLong blockedPackets = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeNs = new AtomicLong(0);
    private final Map<String, AtomicLong> violationsByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> violationsByPlayer = new ConcurrentHashMap<>();

    public MetricsCollector(LightGuard plugin) {
        this.startTime = System.currentTimeMillis();
    }

    public void recordPacket(boolean blocked, long timeNs) {
        totalPackets.incrementAndGet();
        if (blocked) blockedPackets.incrementAndGet();
        totalProcessingTimeNs.addAndGet(timeNs);
    }

    public void recordViolation(String playerName, String checkName) {
        violationsByType.computeIfAbsent(checkName, k -> new AtomicLong(0)).incrementAndGet();
        violationsByPlayer.computeIfAbsent(playerName, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void reset() {
        totalPackets.set(0);
        blockedPackets.set(0);
        totalProcessingTimeNs.set(0);
        violationsByType.clear();
        violationsByPlayer.clear();
    }

    public String getFormattedReport() {
        double uptimeHours = (System.currentTimeMillis() - startTime) / 3600000.0;
        long total = totalPackets.get();
        long blocked = blockedPackets.get();
        double blockRate = total > 0 ? (blocked * 100.0 / total) : 0;
        double avgTimeUs = total > 0 ? (totalProcessingTimeNs.get() / (double) total) / 1000.0 : 0;

        StringBuilder sb = new StringBuilder(1024);
        sb.append("§8════════════════════════════════════════════════\n")
                .append("        §cLightGuard Statistics Report\n")
                .append("§b════════════════════════════════════════════════\n")
                .append("§cUptime: §4").append(String.format("%.2f", uptimeHours)).append(" hours\n")
                .append("§cPackets Processed: §4").append(total).append("\n")
                .append("§cPackets Blocked: §4").append(blocked).append(" §8(§e").append(String.format("%.2f", blockRate)).append("%§8)\n")
                .append("§cAvg Processing Time: §4").append(String.format("%.2f", avgTimeUs)).append(" μs\n\n")
                .append("§c--- Violation Breakdown ---\n");

        violationsByType.forEach((type, count) -> sb.append("  §7- §f").append(type).append(": §e").append(count.get()).append("\n"));

        sb.append("\n§c--- Top 5 Violators ---\n");
        violationsByPlayer.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(5)
                .forEach(e -> sb.append("  §7- §c").append(e.getKey()).append(": §f").append(e.getValue().get()).append(" violations\n"));
        sb.append("§8════════════════════════════════════════════════");
        return sb.toString();
    }
}
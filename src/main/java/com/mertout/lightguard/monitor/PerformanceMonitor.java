package com.mertout.lightguard.monitor;

import com.mertout.lightguard.LightGuard;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class PerformanceMonitor {

    private final LightGuard plugin;
    private final Map<String, LongAdder> checkDurations = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> checkCounts = new ConcurrentHashMap<>();

    public PerformanceMonitor(LightGuard plugin) {
        this.plugin = plugin;
    }

    public void recordCheck(String checkName, long durationNs) {
        checkDurations.computeIfAbsent(checkName, k -> new LongAdder()).add(durationNs);
        checkCounts.computeIfAbsent(checkName, k -> new LongAdder()).increment();
    }

    public void printStats() {
        plugin.getLogger().info("§e=== LightGuard Performance Benchmark ===");

        if (checkDurations.isEmpty()) {
            plugin.getLogger().info("§4[!] §cNo data has been collected yet. Please wait for some processing from the players.");
            return;
        }

        checkDurations.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum())) // En yavaştan hızlıya
                .limit(15) // İlk 15 check
                .forEach(entry -> {
                    String name = entry.getKey();
                    long totalNs = entry.getValue().sum();
                    long count = checkCounts.get(name).sum();
                    long avgNs = count > 0 ? totalNs / count : 0;

                    plugin.getLogger().info(String.format(
                            "Check: %-20s | Avg: %-8dns | Total: %-6dms | Calls: %d",
                            name, avgNs, totalNs / 1_000_000, count
                    ));
                });

        plugin.getLogger().info("§e========================================");
    }

    public void reset() {
        checkDurations.clear();
        checkCounts.clear();
    }
}
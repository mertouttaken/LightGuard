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
        plugin.getLogger().info("§e[LightGuard] === Performance Benchmark ===");

        if (checkDurations.isEmpty()) {
            plugin.getLogger().info("§cNo data collected yet.");
            return;
        }

        checkDurations.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .limit(10)
                .forEach(entry -> {
                    String name = entry.getKey();
                    long totalNs = entry.getValue().sum();
                    long count = checkCounts.get(name).sum();
                    long avgNs = count > 0 ? totalNs / count : 0;

                    plugin.getLogger().info(String.format(
                            "§7- §f%-18s §8| §eAvg: %-6dns §8| §7Calls: %d",
                            name, avgNs, count
                    ));
                });
    }

    public void reset() {
        checkDurations.clear();
        checkCounts.clear();
    }
}
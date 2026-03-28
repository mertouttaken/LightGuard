package com.mertout.lightguard.monitor;

import com.mertout.lightguard.LightGuard;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class PerformanceMonitor {

    private final LightGuard plugin;
    private final Map<String, CheckData> statsMap = new ConcurrentHashMap<>();

    public PerformanceMonitor(LightGuard plugin) {
        this.plugin = plugin;
    }

    public void recordCheck(String checkName, long durationNs) {
        CheckData data = statsMap.get(checkName);
        if (data == null) {
            data = statsMap.computeIfAbsent(checkName, k -> new CheckData());
        }

        data.totalDuration.add(durationNs);
        data.callCount.increment();
    }

    public void printStats() {
        plugin.getLogger().info("§e[LightGuard] === Performance Benchmark ===");

        if (statsMap.isEmpty()) {
            plugin.getLogger().info("§cNo data collected yet.");
            return;
        }

        statsMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().totalDuration.sum(), a.getValue().totalDuration.sum()))
                .limit(10)
                .forEach(entry -> {
                    String name = entry.getKey();
                    CheckData data = entry.getValue();
                    long totalNs = data.totalDuration.sum();
                    long count = data.callCount.sum();
                    long avgNs = count > 0 ? totalNs / count : 0;

                    plugin.getLogger().info(String.format(
                            "§7- §f%-18s §8| §eAvg: %-6dns §8| §7Calls: %d",
                            name, avgNs, count
                    ));
                });
    }

    public void reset() {
        statsMap.clear();
    }

    private static class CheckData {
        final LongAdder totalDuration = new LongAdder();
        final LongAdder callCount = new LongAdder();
    }
}
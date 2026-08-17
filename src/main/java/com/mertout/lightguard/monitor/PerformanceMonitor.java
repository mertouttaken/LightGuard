package com.mertout.lightguard.monitor;

import com.mertout.lightguard.LightGuard;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class PerformanceMonitor {

    private final LightGuard plugin;
    private final Map<Class<?>, CheckData> statsMap = new ConcurrentHashMap<>();

    public PerformanceMonitor(LightGuard plugin) {
        this.plugin = plugin;
    }

    public void recordCheck(Class<?> checkClass, long durationNs) {
        CheckData data = statsMap.computeIfAbsent(checkClass, k -> new CheckData());

        data.totalDuration.add(durationNs);
        data.callCount.increment();
    }

    public void printStats() {
        plugin.getLogger().info("=== Performance Benchmark ===");

        if (statsMap.isEmpty()) {
            plugin.getLogger().info("§cNo data collected yet.");
            return;
        }

        statsMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().totalDuration.sum(), a.getValue().totalDuration.sum()))
                .limit(10)
                .forEach(entry -> {
                    String name = entry.getKey().getSimpleName();
                    CheckData data = entry.getValue();
                    long totalNs = data.totalDuration.sum();
                    long count = data.callCount.sum();
                    long avgNs = count > 0 ? totalNs / count : 0;

                    plugin.getLogger().info(String.format(
                            "§7- §f%-25s §8| §eAvg: %-6dns §8| §7Calls: %d",
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
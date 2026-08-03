package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SafeModeManager {

    private final LightGuard plugin;
    private final Map<String, ExceptionData> exceptionTracker = new ConcurrentHashMap<>();
    private final Set<String> disabledChecks = ConcurrentHashMap.newKeySet();
    private String[] criticalPatterns;

    private final int errorThreshold;
    private final long timeWindowMillis = 60000L;

    public SafeModeManager(LightGuard plugin) {
        this.plugin = plugin;
        this.errorThreshold = plugin.getConfig().getInt("settings.sentinel.safe-mode-threshold", 50);
        loadCriticalPatterns();
    }

    private void loadCriticalPatterns() {
        criticalPatterns = plugin.getConfig().getStringList("settings.sentinel.critical-checks").toArray(new String[0]);
    }

    public boolean isCheckEnabled(String checkName) {
        if (isCritical(checkName)) return true;
        return !disabledChecks.contains(checkName);
    }

    public boolean isCritical(String checkName) {
        if (checkName == null) return false;
        String lowerName = checkName.toLowerCase();

        for (String pattern : criticalPatterns) {
            if (lowerName.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public void recordException(String checkName, Throwable t) {
        if (isCritical(checkName)) {
            plugin.getLogger().warning("[LightGuard] Critical check '" + checkName + "' threw an error but will stay ENABLED for security.");
            return;
        }

        long now = System.currentTimeMillis();

        ExceptionData data = exceptionTracker.computeIfAbsent(checkName, k -> new ExceptionData(now));

        if (now - data.startTime > timeWindowMillis) {
            data.startTime = now;
            data.count.set(1);
        } else {
            int errors = data.count.incrementAndGet();
            if (errors >= errorThreshold && disabledChecks.add(checkName)) {
                plugin.getLogger().severe("╔══════════════════════════════════════════╗");
                plugin.getLogger().severe("║        SAFE MODE CRITICAL ALERT          ║");
                plugin.getLogger().severe("╠══════════════════════════════════════════╣");
                plugin.getLogger().severe("║  ✗ Check: " + checkName);
                plugin.getLogger().severe("║  ✗ Action: AUTOMATICALLY DISABLED        ║");
                plugin.getLogger().severe("║  ✗ Reason: " + errorThreshold + " errors within 60s!  ║");
                plugin.getLogger().severe("╚══════════════════════════════════════════╝");
            }
        }
    }

    public void reset() {
        exceptionTracker.clear();
        disabledChecks.clear();
    }

    public void reload() {
        loadCriticalPatterns();
        reset();
    }

    public Set<String> getDisabledChecks() {
        return disabledChecks;
    }

    public Map<String, Integer> getExceptionCounts() {
        Map<String, Integer> counts = new java.util.HashMap<>();
        exceptionTracker.forEach((name, data) -> counts.put(name, data.count.get()));
        return counts;
    }

    private static class ExceptionData {
        volatile long startTime;
        final AtomicInteger count;

        ExceptionData(long startTime) {
            this.startTime = startTime;
            this.count = new AtomicInteger(0);
        }
    }
}
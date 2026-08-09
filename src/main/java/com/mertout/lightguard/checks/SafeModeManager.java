package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class SafeModeManager {

    private final LightGuard plugin;

    private final Map<UUID, Map<String, ExceptionData>> exceptionTracker = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> disabledChecks = new ConcurrentHashMap<>();

    private final Set<String> criticalPatterns = new CopyOnWriteArraySet<>();

    private final int errorThreshold;
    private final long timeWindowMillis = 60000L;

    public SafeModeManager(LightGuard plugin) {
        this.plugin = plugin;
        this.errorThreshold = plugin.getConfig().getInt("settings.sentinel.safe-mode-threshold", 10);
        loadCriticalPatterns();
    }

    private void loadCriticalPatterns() {
        criticalPatterns.clear();
        List<String> list = plugin.getConfig().getStringList("settings.sentinel.critical-checks");

        if (list != null) {
            for (String s : list) {
                criticalPatterns.add(s.toLowerCase());
            }
        }

        criticalPatterns.addAll(Arrays.asList("itemexploit", "payload", "packetsize", "protocolexploit", "creative"));
    }

    public boolean isCheckEnabled(String checkName, UUID uuid) {
        if (isCritical(checkName)) return true;

        Set<String> disabledForPlayer = disabledChecks.get(uuid);
        return disabledForPlayer == null || !disabledForPlayer.contains(checkName);
    }

    public boolean isCritical(String checkName) {
        if (checkName == null) return false;
        String lowerName = checkName.toLowerCase();
        for (String pattern : criticalPatterns) {
            if (lowerName.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    public void recordException(String checkName, UUID uuid, Throwable t) {
        if (isCritical(checkName)) {
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, ExceptionData> playerTracker = exceptionTracker.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        ExceptionData data = playerTracker.computeIfAbsent(checkName, k -> new ExceptionData(now));

        synchronized (data) {
            if (now - data.startTime > timeWindowMillis) {
                data.startTime = now;
                data.count = 1;
            } else {
                data.count++;
            }

            if (data.count >= errorThreshold) {
                Set<String> disabledForPlayer = disabledChecks.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
                if (!disabledForPlayer.contains(checkName)) {
                    disabledForPlayer.add(checkName);

                    plugin.getLogger().warning("[Sentinel] Check '" + checkName + "' threw " + errorThreshold
                            + " errors for player " + uuid.toString().substring(0,8) + "... -> Disabled ONLY for this player.");
                }
            }
        }
    }

    public void clear(UUID uuid) {
        exceptionTracker.remove(uuid);
        disabledChecks.remove(uuid);
    }

    public void reset() {
        exceptionTracker.clear();
        disabledChecks.clear();
    }

    public void reload() {
        loadCriticalPatterns();
        reset();
    }

    private static class ExceptionData {
        long startTime;
        int count;

        ExceptionData(long startTime) {
            this.startTime = startTime;
            this.count = 0;
        }
    }
}
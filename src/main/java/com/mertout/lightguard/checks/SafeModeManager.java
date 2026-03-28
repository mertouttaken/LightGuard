package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SafeModeManager {

    private final LightGuard plugin;
    private final Map<String, Integer> exceptionCounts = new ConcurrentHashMap<>();
    private final Set<String> disabledChecks = ConcurrentHashMap.newKeySet();
    private final Set<String> criticalPatterns = new HashSet<>();
    private final int errorThreshold;

    public SafeModeManager(LightGuard plugin) {
        this.plugin = plugin;
        this.errorThreshold = plugin.getConfig().getInt("settings.sentinel.safe-mode-threshold", 10);
        loadCriticalPatterns();
    }

    private void loadCriticalPatterns() {
        criticalPatterns.clear();
        List<String> list = plugin.getConfig().getStringList("settings.sentinel.critical-checks");
        if (list != null) {
            criticalPatterns.addAll(list);
        }
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
            plugin.getLogger().warning("[Sentinel] Critical check '" + checkName + "' threw an exception but will stay ENABLED for security.");
            return;
        }

        int count = exceptionCounts.merge(checkName, 1, Integer::sum);

        if (count >= errorThreshold && !disabledChecks.contains(checkName)) {
            disabledChecks.add(checkName);
            plugin.getLogger().severe("╔══════════════════════════════════════════╗");
            plugin.getLogger().severe("║        SAFE MODE CRITICAL ALERT          ║");
            plugin.getLogger().severe("╠══════════════════════════════════════════╣");
            plugin.getLogger().severe("║  ✗ Check: " + checkName);
            plugin.getLogger().severe("║  ✗ Action: AUTOMATICALLY DISABLED        ║");
            plugin.getLogger().severe("║  ✗ Reason: Internal error limit reached  ║");
            plugin.getLogger().severe("╚══════════════════════════════════════════╝");
        }
    }

    public void reset() {
        exceptionCounts.clear();
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
        return exceptionCounts;
    }
}
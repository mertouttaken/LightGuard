package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NettyWatchdogManager {
    private final LightGuard plugin;
    private final PacketLoggerConfig config;
    private final Map<Thread, Long> threadProcessingStartTimes = new ConcurrentHashMap<>();
    private volatile boolean active = false;
    private Thread watchdogThread;

    public NettyWatchdogManager(LightGuard plugin, PacketLoggerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void startProcessing() { if (active) threadProcessingStartTimes.put(Thread.currentThread(), System.currentTimeMillis()); }
    public void endProcessing() { if (active) threadProcessingStartTimes.remove(Thread.currentThread()); }

    public void setEnabled(boolean enabled) {
        if (this.active == enabled) return;
        this.active = enabled;
        if (enabled) startWatchdog();
        else threadProcessingStartTimes.clear();
    }
    public boolean isActive() { return active; }

    private void startWatchdog() {
        watchdogThread = new Thread(() -> {
            while (active) {
                try {
                    Thread.sleep(config.getWatchdogCheckFrequency());
                    long now = System.currentTimeMillis();
                    for (Map.Entry<Thread, Long> entry : threadProcessingStartTimes.entrySet()) {
                        if (now - entry.getValue() > config.getWatchdogMaxTime()) {
                            plugin.getLogger().warning("Netty thread blocked for " + (now - entry.getValue()) + "ms!");
                        }
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        watchdogThread.start();
    }
}
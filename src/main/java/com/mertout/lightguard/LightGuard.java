package com.mertout.lightguard;

import com.mertout.lightguard.commands.LGCommand;
import com.mertout.lightguard.config.ConfigManager;
import com.mertout.lightguard.data.PlayerDataManager;
import com.mertout.lightguard.listeners.MechanicListener;
import com.mertout.lightguard.logger.PacketLoggerManager;
import com.mertout.lightguard.metrics.MetricsCollector;
import com.mertout.lightguard.monitor.PerformanceMonitor;
import com.mertout.lightguard.netty.PacketInjector;

import org.bukkit.plugin.java.JavaPlugin;


public class LightGuard extends JavaPlugin {

    private static LightGuard instance;
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private PacketInjector packetInjector;
    private PacketLoggerManager packetLoggerManager;
    private PerformanceMonitor performanceMonitor;
    private MetricsCollector metrics;

    private double currentTps = 20.0;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.packetLoggerManager = new PacketLoggerManager(this);
        this.playerDataManager = new PlayerDataManager();
        this.performanceMonitor = new PerformanceMonitor(this);
        this.metrics = new MetricsCollector(this);

        // Listeners & Commands
        getServer().getPluginManager().registerEvents(new MechanicListener(this), this);
        getCommand("lg").setExecutor(new LGCommand(this));

        // Netty Injection
        this.packetInjector = new PacketInjector(this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                double recentTps = net.minecraft.server.v1_16_R3.MinecraftServer.getServer().recentTps[0];

                this.currentTps = Math.min(20.0, Math.max(0.0, recentTps));
            } catch (Exception e) {
                this.currentTps = 20.0;
            }
        }, 40L, 40L);
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            long now = System.currentTimeMillis();
            for (com.mertout.lightguard.data.PlayerData data : getPlayerDataManager().getAllData()) {
                data.cleanOldKeepAlives();
                if (data.getCheckManager() != null) {
                    data.getCheckManager().cleanupChecks(now);
                }
            }
        }, 1200L, 1200L);

        getLogger().info("LightGuard (mert.out) Packet Protection Enabled!");
    }
    @Override
    public void onDisable() {
        if (packetInjector != null) packetInjector.ejectAll();
        if (packetLoggerManager != null) packetLoggerManager.shutdown();
        instance = null;
    }

    public static LightGuard getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public PacketLoggerManager getPacketLoggerManager() { return packetLoggerManager; }
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    public MetricsCollector getMetrics() { return metrics; }

    public double getTPS() { return currentTps; }
}
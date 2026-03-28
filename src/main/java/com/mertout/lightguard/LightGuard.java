package com.mertout.lightguard;

import com.mertout.lightguard.checks.LightGuardDispatcher;
import com.mertout.lightguard.commands.LGCommand;
import com.mertout.lightguard.commands.subcommands.LGProfileCommand;
import com.mertout.lightguard.config.ConfigManager;
import com.mertout.lightguard.core.ActionQueue;
import com.mertout.lightguard.data.ProtocolManager;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.listeners.MechanicListener;
import com.mertout.lightguard.listeners.WorldChunkManager;
import com.mertout.lightguard.logger.PacketLoggerManager;
import com.mertout.lightguard.metrics.MetricsCollector;
import com.mertout.lightguard.monitor.PerformanceMonitor;
import com.mertout.lightguard.netty.LGPacketInjector;
import com.mertout.lightguard.netty.LGStartupValidator;

import org.bukkit.plugin.java.JavaPlugin;

public class LightGuard extends JavaPlugin {

    private static LightGuard instance;
    private ConfigManager configManager;
    private LGPacketInjector packetInjector;
    private PacketLoggerManager packetLoggerManager;
    private PerformanceMonitor performanceMonitor;
    private MetricsCollector metrics;
    private ActionQueue actionQueue;
    private WorldChunkManager worldChunkManager;
    private ProtocolManager protocolManager;
    private LightGuardDispatcher checkDispatcher; // İsim Dispatcher olarak güncellendi

    private double currentTps = 20.0;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        if (!LGStartupValidator.validateAndInject(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.configManager = new ConfigManager(this);
        this.actionQueue = new ActionQueue(this);
        this.protocolManager = new ProtocolManager();
        this.worldChunkManager = new WorldChunkManager();
        this.checkDispatcher = new LightGuardDispatcher(this);

        this.packetInjector = new LGPacketInjector(this);

        this.packetLoggerManager = new PacketLoggerManager(this);
        this.performanceMonitor = new PerformanceMonitor(this);
        this.metrics = new MetricsCollector(this);

        getServer().getPluginManager().registerEvents(new MechanicListener(this), this);
        getCommand("lg").setExecutor(new LGCommand(this));

        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                double recentTps = net.minecraft.server.v1_16_R3.MinecraftServer.getServer().recentTps[0];
                this.currentTps = Math.min(20.0, Math.max(0.0, recentTps));
            } catch (Exception e) {
                this.currentTps = 20.0;
            }
        }, 40L, 40L);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (getProtocolManager() != null) {
                getProtocolManager().getPlayers().values().forEach(ProtocolPlayer::updatePPS);
            }
        }, 20L, 20L);

        getLogger().info("§aLightGuard V2 (mert.out) Protection ACTIVE!");
    }

    @Override
    public void onDisable() {
        LGProfileCommand.cancelAll();

        if (packetLoggerManager != null) packetLoggerManager.shutdown();

        if (protocolManager != null && packetInjector != null) {
            protocolManager.getPlayers().forEach((uuid, pp) -> {
                if (pp.getPlayer().isOnline()) packetInjector.removePlayer(pp.getPlayer());
            });
        }

        instance = null;
    }

    public void reloadCheckDispatcher() {
        this.checkDispatcher = new LightGuardDispatcher(this);
    }

    public static LightGuard getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public PacketLoggerManager getPacketLoggerManager() { return packetLoggerManager; }
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    public MetricsCollector getMetrics() { return metrics; }
    public ActionQueue getActionQueue() { return actionQueue; }
    public WorldChunkManager getWorldChunkManager() { return worldChunkManager; }
    public ProtocolManager getProtocolManager() { return protocolManager; }
    public LGPacketInjector getPacketInjector() { return packetInjector; }
    public LightGuardDispatcher getDispatcher() { return checkDispatcher; }

    public double getTPS() { return currentTps; }
}
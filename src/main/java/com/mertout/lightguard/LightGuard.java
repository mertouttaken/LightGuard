package com.mertout.lightguard;

import com.mertout.lightguard.checks.Dispatcher;
import com.mertout.lightguard.checks.ViolationRadar;
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

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

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
    private Dispatcher checkDispatcher;
    private ViolationRadar violationRadar;
    private MechanicListener mechanicListener;

    private double currentTps = 20.0;
    private Method getTpsMethod = null;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        if (!LGStartupValidator.validateAndInject(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            getTpsMethod = Bukkit.getServer().getClass().getMethod("getTPS");
        } catch (Exception ignored) {}

        this.configManager = new ConfigManager(this);
        this.metrics = new MetricsCollector(this);
        this.actionQueue = new ActionQueue(this);
        this.protocolManager = new ProtocolManager(this);
        this.worldChunkManager = new WorldChunkManager(this);
        this.performanceMonitor = new PerformanceMonitor(this);
        this.packetLoggerManager = new PacketLoggerManager(this);
        this.violationRadar = new ViolationRadar(this);
        this.checkDispatcher = new Dispatcher(this, violationRadar);
        this.packetInjector = new LGPacketInjector(this);

        this.mechanicListener = new MechanicListener(this);
        getServer().getPluginManager().registerEvents(mechanicListener, this);
        getCommand("lg").setExecutor(new LGCommand(this));

        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                if (getTpsMethod != null) {
                    double[] tpsArr = (double[]) getTpsMethod.invoke(Bukkit.getServer());
                    this.currentTps = Math.min(20.0, Math.max(0.0, tpsArr[0]));
                } else {
                    this.currentTps = 20.0;
                }
            } catch (Exception e) {
                this.currentTps = 20.0;
            }
        }, 40L, 40L);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (protocolManager != null) {
                protocolManager.getPlayers().values().forEach(ProtocolPlayer::updatePPS);
            }
        }, 20L, 20L);

        getLogger().info("§aLightGuard V2 (mert.out) Protection ACTIVE!");
    }

    @Override
    public void onDisable() {
        LGStartupValidator.uninject();
        LGProfileCommand.cancelAll();

        if (packetLoggerManager != null) {
            packetLoggerManager.shutdown();
        }

        if (protocolManager != null && packetInjector != null) {
            protocolManager.getPlayers().forEach((uuid, pp) -> {
                if (pp.getPlayer() != null && pp.getPlayer().isOnline()) {
                    packetInjector.removePlayer(pp.getPlayer());
                }
            });
        }

        instance = null;
    }

    public void reloadCheckDispatcher() {
        this.checkDispatcher = new Dispatcher(this, violationRadar);
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
    public Dispatcher getDispatcher() { return checkDispatcher; }
    public ViolationRadar getViolationRadar() { return violationRadar; }
    public MechanicListener getMechanicListener() { return mechanicListener; }
    public double getTPS() { return currentTps; }
}
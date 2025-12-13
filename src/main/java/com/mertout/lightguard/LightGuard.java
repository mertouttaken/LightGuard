package com.mertout.lightguard;

import com.mertout.lightguard.commands.LGCommand;
import com.mertout.lightguard.config.ConfigManager;
import com.mertout.lightguard.data.PlayerDataManager;
import com.mertout.lightguard.listeners.MechanicListener;
import com.mertout.lightguard.logger.PacketLoggerManager;
import com.mertout.lightguard.netty.PacketInjector;
import com.mertout.lightguard.utils.NBTChecker;
import net.minecraft.server.v1_16_R3.NBTBase;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;

public class LightGuard extends JavaPlugin {

    private static LightGuard instance;
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private PacketInjector packetInjector;
    private PacketLoggerManager packetLoggerManager;

    // Adaptive Flood için TPS sayacı
    private double currentTps = 20.0;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.packetLoggerManager = new PacketLoggerManager(this);
        this.playerDataManager = new PlayerDataManager();

        // Listeners & Commands
        getServer().getPluginManager().registerEvents(new MechanicListener(this), this);
        getCommand("lg").setExecutor(new LGCommand(this));

        // Netty Injection (En son başlat)
        this.packetInjector = new PacketInjector(this);

        // ➤ TPS İZLEME GÖREVİ (DÜZELTİLDİ)
        // Her 2 saniyede (40 tick) bir TPS'i günceller.
        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                // 1.16.5 NMS üzerinden son 1 dakikalık TPS ortalamasını alıyoruz.
                // recentTps[0] = 1dk, [1] = 5dk, [2] = 15dk
                double recentTps = net.minecraft.server.v1_16_R3.MinecraftServer.getServer().recentTps[0];

                // Değeri 0 ile 20 arasında sınırla (Bazen başlangıçta 20 üstü çıkabilir)
                this.currentTps = Math.min(20.0, Math.max(0.0, recentTps));
            } catch (Exception e) {
                this.currentTps = 20.0; // Hata olursa varsayılan değer
            }
        }, 40L, 40L);

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

    // Diğer sınıflar TPS'i buradan alacak
    public double getTPS() { return currentTps; }
}
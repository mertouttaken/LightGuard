package com.mertout.lightguard.config;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import com.mertout.lightguard.utils.NBTChecker;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final LightGuard plugin;

    public ConfigManager(LightGuard plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();

        NBTChecker.reload();

        for (PlayerData data : plugin.getPlayerDataManager().getAllData()) {
            if (data.getCheckManager() != null) {
                data.getCheckManager().reloadChecks();
            }
        }

        if (plugin.getPacketLoggerManager() != null) {
            plugin.getPacketLoggerManager().getConfig().reload(plugin);
        }

        plugin.getLogger().info("Configuration and caches reloaded successfully.");
    }

    public void save() {
        plugin.saveConfig();
    }
}
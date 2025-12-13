package com.mertout.lightguard.config;

import com.mertout.lightguard.LightGuard;
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
        plugin.getLogger().info("Configuration reloaded successfully.");
    }

    public void save() {
        plugin.saveConfig();
    }
}
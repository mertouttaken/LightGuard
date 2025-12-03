package com.mertout.lightguard.config;

import com.mertout.lightguard.LightGuard;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final LightGuard plugin;

    public ConfigManager(LightGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * config.yml dosyasına erişimi sağlar.
     */
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /**
     * Config dosyasını diskten tekrar yükler.
     * /lpx reload komutu burayı tetikler.
     */
    public void reload() {
        plugin.reloadConfig();
        plugin.getLogger().info("Configuration reloaded successfully.");
    }

    /**
     * Yapılan değişiklikleri diske kaydeder (Runtime değişiklikler için).
     */
    public void save() {
        plugin.saveConfig();
    }
}
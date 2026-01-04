package com.mertout.lightguard.config;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import com.mertout.lightguard.utils.NBTChecker;
import net.minecraft.server.v1_16_R3.IRegistry;
import net.minecraft.server.v1_16_R3.Items;
import net.minecraft.server.v1_16_R3.MinecraftKey;
import org.bukkit.configuration.file.FileConfiguration;
import net.minecraft.server.v1_16_R3.Item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigManager {

    private final LightGuard plugin;
    private Set<Item> cachedIllegalBlocks = null;
    public ConfigManager(LightGuard plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }
    public Set<Item> getIllegalBlocks() {
        if (cachedIllegalBlocks == null) {
            cachedIllegalBlocks = new HashSet<>();
            List<String> list = plugin.getConfig().getStringList("checks.block-place.illegal-blocks");

            for (String key : list) {
                try {
                    MinecraftKey mcKey = new MinecraftKey(key.toLowerCase());
                    Item item = IRegistry.ITEM.get(mcKey);
                    if (item != null && item != Items.AIR) {
                        cachedIllegalBlocks.add(item);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid block name in config: " + key);
                }
            }
        }
        return cachedIllegalBlocks;
    }
    public void reload() {
        plugin.reloadConfig();
        cachedIllegalBlocks = null;

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
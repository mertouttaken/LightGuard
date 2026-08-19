package com.mertout.lightguard.config;

import com.mertout.lightguard.LightGuard;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ConfigManager {

    private final LightGuard plugin;

    private volatile Set<Item> cachedIllegalBlocks = null;

    public ConfigManager(LightGuard plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public Set<Item> getIllegalBlocks() {
        Set<Item> localBlocks = cachedIllegalBlocks;
        if (localBlocks == null) {
            synchronized (this) {
                localBlocks = cachedIllegalBlocks;
                if (localBlocks == null) {
                    localBlocks = new HashSet<>();
                    List<String> list = plugin.getConfig().getStringList("checks.block-place.illegal-blocks");

                    for (String key : list) {
                        try {
                            ResourceLocation mcKey = ResourceLocation.parse(key.toLowerCase());
                            Optional<Holder.Reference<Item>> holder = BuiltInRegistries.ITEM.get(mcKey);

                            if (holder.isPresent()) {
                                Item item = holder.get().value();
                                if (item != null && item != Items.AIR) {
                                    localBlocks.add(item);
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Invalid block name in config: " + key);
                        }
                    }
                    cachedIllegalBlocks = localBlocks;
                }
            }
        }
        return localBlocks;
    }

    public void reload() {
        plugin.reloadConfig();
        cachedIllegalBlocks = null;

        if (plugin.getActionQueue() != null) {
            plugin.getActionQueue().loadConfig();
        }

        if (plugin.getViolationRadar() != null) {
            plugin.getViolationRadar().loadConfig();
        }

        if (plugin.getMechanicListener() != null) {
            plugin.getMechanicListener().loadConfig();
        }

        if (plugin.getDispatcher() != null) {
            plugin.reloadCheckDispatcher();
        }

        if (plugin.getPacketLoggerManager() != null) {
            plugin.getPacketLoggerManager().getConfig().reload(plugin);
        }

        if (plugin.getWorldChunkManager() != null) {
            plugin.getWorldChunkManager().clearCache();
        }

        plugin.getLogger().info("Configuration and caches reloaded successfully.");
    }

    public void save() {
        plugin.saveConfig();
    }
}
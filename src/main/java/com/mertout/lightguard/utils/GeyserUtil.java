package com.mertout.lightguard.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public class GeyserUtil {

    private static boolean floodgatePresent = false;

    static {
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            floodgatePresent = true;
        }
    }

    public static boolean isBedrockPlayer(Player player) {
        if (!floodgatePresent) return false;

        try {
            Object api = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                    .getMethod("getInstance").invoke(null);

            return (boolean) api.getClass().getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(api, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}
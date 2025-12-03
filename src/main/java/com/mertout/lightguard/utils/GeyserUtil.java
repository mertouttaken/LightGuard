package com.mertout.lightguard.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public class GeyserUtil {

    private static boolean floodgatePresent = false;

    static {
        // Sunucuda Floodgate eklentisi var mı kontrol et
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            floodgatePresent = true;
        }
    }

    /**
     * Oyuncunun Bedrock (Geyser) oyuncusu olup olmadığını kontrol eder.
     */
    public static boolean isBedrockPlayer(Player player) {
        if (!floodgatePresent) return false;

        try {
            // Floodgate API'sini Reflection ile çağırıyoruz (Dependency eklememek için)
            Object api = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                    .getMethod("getInstance").invoke(null);

            return (boolean) api.getClass().getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(api, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}
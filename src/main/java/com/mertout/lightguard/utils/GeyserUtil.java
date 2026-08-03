package com.mertout.lightguard.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

public class GeyserUtil {

    private static boolean floodgatePresent = false;
    private static Object floodgateApiInstance = null;
    private static MethodHandle isFloodgatePlayerMethod = null;

    static {
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");

                MethodHandle getInstanceMethod = lookup.findStatic(apiClass, "getInstance", MethodType.methodType(apiClass));
                floodgateApiInstance = getInstanceMethod.invoke();

                isFloodgatePlayerMethod = lookup.findVirtual(apiClass, "isFloodgatePlayer", MethodType.methodType(boolean.class, UUID.class));

                floodgatePresent = true;
            } catch (Throwable t) {
                floodgatePresent = false;
            }
        }
    }

    public static boolean isBedrockPlayer(Player player) {
        if (!floodgatePresent || isFloodgatePlayerMethod == null || floodgateApiInstance == null) {
            return false;
        }

        try {
            return (boolean) isFloodgatePlayerMethod.invoke(floodgateApiInstance, player.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }
}
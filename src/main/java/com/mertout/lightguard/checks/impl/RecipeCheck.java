package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;

public class RecipeCheck extends Check {

    private static final int MAX_RECIPE_PACKETS = 5;

    public RecipeCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (packet instanceof ServerboundPlaceRecipePacket) {
            try {
                if (player.incrementAndGetRecipeCount(System.currentTimeMillis()) > MAX_RECIPE_PACKETS) {
                    return false;
                }
                ServerboundPlaceRecipePacket placePacket = (ServerboundPlaceRecipePacket) packet;
                if (placePacket.recipe() == null || placePacket.containerId() < 0) {
                    return false;
                }
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        if (packet instanceof ServerboundRecipeBookSeenRecipePacket) {
            try {
                if (player.incrementAndGetRecipeCount(System.currentTimeMillis()) > MAX_RECIPE_PACKETS) {
                    return false;
                }
                ServerboundRecipeBookSeenRecipePacket seenPacket = (ServerboundRecipeBookSeenRecipePacket) packet;
                if (seenPacket.recipe() == null) {
                    return false;
                }
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        return true;
    }
}
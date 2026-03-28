package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.*;

public class RecipeCheck extends Check {

    private static final int MAX_RECIPE_PACKETS = 5;

    public RecipeCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (packet instanceof PacketPlayInAutoRecipe || packet instanceof PacketPlayInRecipeDisplayed) {

            if (player.incrementAndGetRecipeCount(System.currentTimeMillis()) > MAX_RECIPE_PACKETS) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Recipe Book Spam Detected");
                return false;
            }
        }
        return true;
    }
}
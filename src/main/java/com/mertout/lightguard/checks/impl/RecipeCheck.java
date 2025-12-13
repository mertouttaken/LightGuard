package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;

public class RecipeCheck extends Check {

    private long lastTime;
    private int count;
    private static final int MAX_RECIPE_PACKETS = 5;

    public RecipeCheck(PlayerData data) {
        super(data, "Recipe", "recipe");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInAutoRecipe || packet instanceof PacketPlayInRecipeDisplayed) {
            long now = System.currentTimeMillis();
            if (now - lastTime > 1000) {
                count = 0;
                lastTime = now;
            }
            count++;

            if (count > MAX_RECIPE_PACKETS) {
                flag("Recipe Book Flood (" + count + ")", packet.getClass().getSimpleName());
                return false;
            }
        }
        return true;
    }
}
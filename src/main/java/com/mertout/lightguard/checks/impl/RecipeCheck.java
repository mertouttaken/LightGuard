package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RecipeCheck extends Check {

    private final AtomicLong lastTime = new AtomicLong();
    private final AtomicInteger count = new AtomicInteger();
    private static final int MAX_RECIPE_PACKETS = 5;

    public RecipeCheck(PlayerData data) {
        super(data, "Recipe", "recipe");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;
        if (packet instanceof PacketPlayInAutoRecipe || packet instanceof PacketPlayInRecipeDisplayed) {
            long now = System.currentTimeMillis();
            if (now - lastTime.get() > 1000) {
                count.set(0);
                lastTime.set(now);
            }
            if (count.incrementAndGet() > MAX_RECIPE_PACKETS) {
                flag("Recipe Book Flood", packet.getClass().getSimpleName());
                return false;
            }
        }
        return true;
    }
}
package com.mertout.lightguard.checks.impl;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;

public class RecipeCheck extends Check {
    private long lastTime; private int count;
    public RecipeCheck(PlayerData data) { super(data, "Recipe"); }
    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.recipe.enabled")) return true;
        if (packet instanceof PacketPlayInAutoRecipe || packet instanceof PacketPlayInRecipeDisplayed) {
            long now = System.currentTimeMillis();
            if (now - lastTime > 1000) { count = 0; lastTime = now; }
            count++;
            if (count > 5) { flag("Flood"); return false; }
        }
        return true;
    }
}
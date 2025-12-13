package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import java.lang.reflect.Field;

public class GameStateCheck extends Check {

    private final boolean checkInvOpen;

    public GameStateCheck(PlayerData data) {
        super(data, "GameState", "game-state");
        this.checkInvOpen = plugin.getConfig().getBoolean("checks.game-state.check-inventory-open");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInWindowClick) {
            if (checkInvOpen) {
                int windowId = getWindowId((PacketPlayInWindowClick) packet);
                InventoryType currentType = data.getPlayer().getOpenInventory().getType();

                if (windowId > 0 && currentType == InventoryType.CRAFTING) {
                    flag("Clicking in Closed Inventory (WinID: " + windowId + ")", "PacketPlayInWindowClick");
                    Bukkit.getScheduler().runTask(plugin, () -> data.getPlayer().updateInventory());
                    return false;
                }
            }
        }
        return true;
    }

    private int getWindowId(PacketPlayInWindowClick packet) {
        try {
            Field f = packet.getClass().getDeclaredField("a");
            f.setAccessible(true);
            return f.getInt(packet);
        } catch (Exception e) { return 0; }
    }
}
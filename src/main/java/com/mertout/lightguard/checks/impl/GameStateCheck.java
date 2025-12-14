package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class GameStateCheck extends Check {

    private static final VarHandle WINDOW_ID;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInWindowClick.class, MethodHandles.lookup());
            WINDOW_ID = lookup.findVarHandle(PacketPlayInWindowClick.class, "a", int.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

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
                int windowId = (int) WINDOW_ID.get(packet);
                InventoryType currentType = data.getPlayer().getOpenInventory().getType();
                if (windowId > 0 && currentType == InventoryType.CRAFTING) {
                    flag("Click in Closed Inventory", "PacketPlayInWindowClick");
                    Bukkit.getScheduler().runTask(plugin, () -> data.getPlayer().updateInventory());
                    return false;
                }
            }
        }
        return true;
    }
}
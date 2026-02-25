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
        if (packet instanceof net.minecraft.server.v1_16_R3.PacketPlayInWindowClick) {
            if (checkInvOpen) {
                int windowId = (int) WINDOW_ID.get(packet);

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!data.getPlayer().isOnline()) return;

                    org.bukkit.event.inventory.InventoryType currentType = data.getPlayer().getOpenInventory().getType();
                    if (windowId > 0 && currentType == org.bukkit.event.inventory.InventoryType.CRAFTING) {

                        data.getPlayer().updateInventory();
                        plugin.getLogger().warning("§c[LightGuard] " + data.getPlayer().getName() + " flagged GameState (Click in Closed Inventory)");
                        plugin.getMetrics().recordViolation(data.getPlayer().getName(), "GameState");
                        data.getPlayer().kickPlayer("§cInvalid Game State");
                    }
                });
            }
        }
        return true;
    }
}
package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;

import java.lang.reflect.Field;

public class GameStateCheck extends Check {

    public GameStateCheck(PlayerData data) {
        super(data, "GameState");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.game-state.enabled")) return true;

        String packetName = packet.getClass().getSimpleName();

        // ➤ 1. Envanter Kapalıyken Tıklama (Inventory Desync)
        if (packet instanceof PacketPlayInWindowClick) {
            if (plugin.getConfig().getBoolean("checks.game-state.check-inventory-open")) {
                int windowId = getWindowId((PacketPlayInWindowClick) packet);

                // Bukkit API ile oyuncunun şu anki açık penceresine bakıyoruz.
                // Eğer oyuncunun açık bir menüsü yoksa, InventoryType.CRAFTING (Kendi envanteri) döner.
                InventoryType currentType = data.getPlayer().getOpenInventory().getType();

                // Kural: Eğer Window ID 0'dan büyükse (Sandık vb.) ama sunucuda oyuncunun
                // önünde sadece "CRAFTING" (kendi envanteri) açıksa, oyuncu hile ile paket yolluyordur.
                // (Nuker, AutoSteal veya ChestStealer hileleri bunu yapar)
                if (windowId > 0 && currentType == InventoryType.CRAFTING) {
                    flag("Clicking in Closed Inventory (WinID: " + windowId + ")", packetName);
                    // Envanteri senkronize et
                    Bukkit.getScheduler().runTask(plugin, () -> data.getPlayer().updateInventory());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * PacketPlayInWindowClick -> 'a' (Window ID)
     */
    private int getWindowId(PacketPlayInWindowClick packet) {
        try {
            Field f = packet.getClass().getDeclaredField("a");
            f.setAccessible(true);
            return f.getInt(packet);
        } catch (Exception e) { return 0; }
    }
}
package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;
import net.minecraft.server.v1_16_R3.InventoryClickType;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;

import java.lang.reflect.Field;

public class WindowCheck extends Check {

    // QuickMove (Shift-Click) Takibi
    private long lastQuickMoveTime;
    private int quickMoveCount;

    public WindowCheck(PlayerData data) {
        super(data, "Window");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.window.enabled")) return true;

        if (packet instanceof PacketPlayInWindowClick) {
            PacketPlayInWindowClick click = (PacketPlayInWindowClick) packet;

            // --- 1. REFLECTION İLE VERİ OKUMA ---
            int slot = getIntField(click, "slot");
            int windowId = getIntField(click, "a");
            InventoryClickType clickType = getClickType(click);
            // --------------------------------

            // --- 2. Invalid Slot Fix ---
            if (plugin.getConfig().getBoolean("checks.window.strict-slots")) {
                if (slot < -1 && slot != -999) {
                    flag("Invalid Slot ID: " + slot);
                    return false;
                }
                if (slot > 1000) {
                    flag("Oversized Slot ID: " + slot);
                    return false;
                }
            }

            // --- 3. Offline/Dead Packet Fix ---
            if (data.getPlayer().isDead() || !data.getPlayer().isValid()) return false;

            // Bukkit Envanter Görünümünü Al
            InventoryView view = data.getPlayer().getOpenInventory();
            InventoryType type = (view != null) ? view.getType() : InventoryType.CRAFTING;

            // --- 4. QuickMove (Shift-Click) Spam Exploit Fix ---
            if (clickType == InventoryClickType.QUICK_MOVE) {
                long now = System.currentTimeMillis();

                // Zamanlayıcıyı sıfırla (1 saniye)
                if (now - lastQuickMoveTime > 1000) {
                    quickMoveCount = 0;
                    lastQuickMoveTime = now;
                }
                quickMoveCount++;

                // Fırın ve Köylü gibi menülerde Shift-Click çok daha tehlikelidir (Desync/Lag)
                int limit = 15; // Normal limit
                if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE ||
                        type == InventoryType.SMOKER || type == InventoryType.MERCHANT) {
                    limit = 8; // Kritik menülerde daha sıkı limit (Exploit Fix)
                }

                if (quickMoveCount > limit) {
                    flag("QuickMove Spam (Rate: " + quickMoveCount + ")");
                    // Envanteri senkronize et ki hayalet item kalmasın
                    Bukkit.getScheduler().runTask(plugin, () -> data.getPlayer().updateInventory());
                    return false;
                }
            }

            // --- 5. Furnace & Merchant Sync Validator ---
            // Bu menülerde hızlı tıklamalar veya geçersiz slot işlemleri sunucuyu çökertebilir
            if (type == InventoryType.MERCHANT || type == InventoryType.FURNACE) {
                // Eğer oyuncu sonuç slotuna (Result Slot) spam yapıyorsa
                // Merchant: Slot 2, Furnace: Slot 2
                if (slot == 2) {
                    // Result slotuna sayı tuşlarıyla (Swap) basmak genelde crash/dupe sebebidir
                    if (clickType == InventoryClickType.SWAP) {
                        flag("Illegal Result Slot Swap");
                        return false;
                    }
                }
            }

            // --- 6. Lectern Crash Fix ---
            if (plugin.getConfig().getBoolean("checks.window.prevent-lectern-spam")) {
                if (type == InventoryType.LECTERN) {
                    if (clickType == InventoryClickType.QUICK_MOVE) {
                        flag("Lectern Illegal Click");
                        return false;
                    }
                }
            }

            // --- 7. GUI Swap Fix ---
            if (plugin.getConfig().getBoolean("checks.window.prevent-swap-in-gui", true)) {
                if (windowId > 0 && clickType == InventoryClickType.SWAP) {
                    flag("Illegal GUI Swap");
                    Bukkit.getScheduler().runTask(plugin, () -> data.getPlayer().updateInventory());
                    return false;
                }
            }
        }
        return true;
    }

    // --- YARDIMCI REFLECTION METOTLARI ---
    private int getIntField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(obj);
        } catch (Exception e) { return 0; }
    }

    private InventoryClickType getClickType(Object obj) {
        try {
            Field field = obj.getClass().getDeclaredField("shift");
            field.setAccessible(true);
            return (InventoryClickType) field.get(obj);
        } catch (Exception e) { return InventoryClickType.PICKUP; }
    }
}
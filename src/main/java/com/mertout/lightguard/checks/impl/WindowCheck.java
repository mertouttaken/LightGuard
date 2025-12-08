package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.Container;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;
import net.minecraft.server.v1_16_R3.InventoryClickType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
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
            String packetName = "PacketPlayInWindowClick";

            // --- 1. VERİ OKUMA (Reflection - 1.16.5 Uyumlu) ---
            // 'a' = Window ID
            // 'slot' = Slot Numarası
            // 'shift' = Tıklama Türü (InventoryClickType)
            int windowId = getIntField(click, "a");
            int slot = getIntField(click, "slot");
            InventoryClickType clickType = getClickType(click);

            // Oyuncunun Sunucudaki Aktif Penceresi (NMS)
            Container activeContainer = ((CraftPlayer) data.getPlayer()).getHandle().activeContainer;
            // --------------------------------

            // ➤ YENİ: 2. Window ID Tutarlılık (SpigotGuard)
            // Paket ID'si ile sunucudaki ID uyuşmuyor ve envanter (0) değilse.
            if (windowId != activeContainer.windowId && windowId != 0) {
                flag("Invalid Window ID (Server:" + activeContainer.windowId + " vs Client:" + windowId + ")", packetName);
                resync();
                return false;
            }

            // ➤ YENİ: 3. Negatif Slot Crash (SpigotGuard)
            // -999 (Dışarı tıklama) hariç negatif slotlar yasaktır.
            if (slot < 0 && slot != -999) {
                flag("Negative Slot Crash (Slot: " + slot + ")", packetName);
                return false;
            }

            // ➤ YENİ: 4. Slot Range/Limit (SpigotGuard)
            // Tıklanan slot, pencerenin toplam boyutundan büyük olamaz.
            if (slot != -999) {
                // Not: Spectator modunda bu bazen false-positive verebilir, kontrol edelim.
                if (!data.getPlayer().getGameMode().name().equals("SPECTATOR")) {
                    int maxSlots = activeContainer.slots.size();
                    // Max slot'tan büyükse veya eşitse (index 0'dan başlar)
                    if (slot >= maxSlots) {
                        flag("Slot Index Out of Bounds (" + slot + " >= " + maxSlots + ")", packetName);
                        return false;
                    }
                }
            }

            // --- 5. BUKKIT VERİLERİ ---
            InventoryView view = data.getPlayer().getOpenInventory();
            InventoryType type = (view != null) ? view.getType() : InventoryType.CRAFTING;

            // --- 6. QuickMove (Shift-Click) Spam Exploit Fix ---
            if (clickType == InventoryClickType.QUICK_MOVE) {
                long now = System.currentTimeMillis();

                if (now - lastQuickMoveTime > 1000) {
                    quickMoveCount = 0;
                    lastQuickMoveTime = now;
                }
                quickMoveCount++;

                int limit = 15;
                if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE ||
                        type == InventoryType.SMOKER || type == InventoryType.MERCHANT) {
                    limit = 8;
                }

                if (quickMoveCount > limit) {
                    flag("QuickMove Spam (Rate: " + quickMoveCount + ")", packetName);
                    resync();
                    return false;
                }
            }

            // --- 7. Furnace & Merchant Result Slot Fix ---
            if (type == InventoryType.MERCHANT || type == InventoryType.FURNACE) {
                // Result slotuna (2) SWAP işlemi yapılırsa crash/dupe olabilir.
                if (slot == 2 && clickType == InventoryClickType.SWAP) {
                    flag("Illegal Result Slot Swap", packetName);
                    return false;
                }
            }

            // --- 8. Lectern Crash Fix ---
            if (plugin.getConfig().getBoolean("checks.window.prevent-lectern-spam")) {
                if (type == InventoryType.LECTERN) {
                    // Kürsüde Shift-Click veya Swap yasakla (Sadece sayfa değişmeli veya kitap alınmalı)
                    if (clickType == InventoryClickType.QUICK_MOVE || clickType == InventoryClickType.SWAP) {
                        flag("Lectern Illegal Click", packetName);
                        return false;
                    }
                }
            }

            // --- 9. GUI Swap Fix ---
            if (plugin.getConfig().getBoolean("checks.window.prevent-swap-in-gui", true)) {
                // Envanter dışı (ID > 0) bir menüde numara tuşlarıyla (Swap) oynama
                // Çoğu dupe buradan çıkar.
                if (windowId > 0 && clickType == InventoryClickType.SWAP) {
                    // Sadece belirli tiplerde yasaklamak istersen burayı özelleştir
                    // Ama genel olarak GUI'lerde swap risklidir.
                    flag("Illegal GUI Swap", packetName);
                    resync();
                    return false;
                }
            }
        }
        return true;
    }

    // --- YARDIMCI METOTLAR ---

    private void resync() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            data.getPlayer().updateInventory();
            if (data.getPlayer().getOpenInventory() != null) {
                data.getPlayer().closeInventory(); // Çok agresif olursa bunu aç
            }
        });
    }

    private int getIntField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(obj);
        } catch (Exception e) {
            // 1.16.5'te 'slot' alanı bazen 'c' olabilir (mapping'e göre)
            // Eğer "slot" bulamazsa alternatifleri dene
            if (fieldName.equals("slot")) {
                try {
                    // Alternatif slot field isimleri (Spigot mappingine göre değişebilir)
                    Field f2 = obj.getClass().getDeclaredField("c");
                    f2.setAccessible(true);
                    return f2.getInt(obj);
                } catch (Exception ex) {}
            }
            return 0;
        }
    }

    private InventoryClickType getClickType(Object obj) {
        try {
            Field field = obj.getClass().getDeclaredField("shift"); // 1.16.5'te isim genelde 'shift'tir
            field.setAccessible(true);
            return (InventoryClickType) field.get(obj);
        } catch (Exception e) { return InventoryClickType.PICKUP; }
    }
}
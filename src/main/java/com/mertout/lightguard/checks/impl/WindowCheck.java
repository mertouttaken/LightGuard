package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.Container;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;
import net.minecraft.server.v1_16_R3.InventoryClickType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode; // GameMode eklendi
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class WindowCheck extends Check {

    // ➤ PERFORMANS: MethodHandle (Reflection yerine)
    // Bu yöntem Java'nın native hızıyla çalışır.
    private static final MethodHandle WINDOW_ID_GETTER;
    private static final MethodHandle SLOT_GETTER;
    private static final MethodHandle CLICK_TYPE_GETTER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            // 'a' = Window ID
            Field fWindowId = PacketPlayInWindowClick.class.getDeclaredField("a");
            fWindowId.setAccessible(true);
            WINDOW_ID_GETTER = lookup.unreflectGetter(fWindowId);

            // 'slot' = Slot Numarası
            Field fSlot = PacketPlayInWindowClick.class.getDeclaredField("slot");
            fSlot.setAccessible(true);
            SLOT_GETTER = lookup.unreflectGetter(fSlot);

            // 'shift' = InventoryClickType
            Field fShift = PacketPlayInWindowClick.class.getDeclaredField("shift");
            fShift.setAccessible(true);
            CLICK_TYPE_GETTER = lookup.unreflectGetter(fShift);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MethodHandles for WindowCheck", e);
        }
    }

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

            try {
                // ➤ 1. HIZLI VERİ OKUMA (MethodHandle)
                int windowId = (int) WINDOW_ID_GETTER.invoke(click);
                int slot = (int) SLOT_GETTER.invoke(click);
                InventoryClickType clickType = (InventoryClickType) CLICK_TYPE_GETTER.invoke(click);

                // Oyuncunun Sunucudaki Aktif Penceresi (NMS)
                Container activeContainer = ((CraftPlayer) data.getPlayer()).getHandle().activeContainer;

                // ➤ 2. Window ID Tutarlılık
                if (windowId != activeContainer.windowId && windowId != 0) {
                    // Creative modda bazen ID uyuşmazlığı olabilir, onu göz ardı etmek gerekebilir
                    // ama şimdilik strict tutuyoruz.
                    flag("Invalid Window ID (Server:" + activeContainer.windowId + " vs Client:" + windowId + ")", packetName);
                    resync();
                    return false;
                }

                // ➤ 3. Negatif Slot Crash
                // -999 (Dışarı tıklama) ve -1 (Creative bazen yollar) hariç negatifler yasak.
                if (slot < 0 && slot != -999 && slot != -1) {
                    flag("Negative Slot Crash (Slot: " + slot + ")", packetName);
                    return false;
                }

                // ➤ 4. Slot Range/Limit (DÜZELTİLDİ)
                // Creative ve Spectator modları bu kuraldan muaf tutulmalı.
                GameMode mode = data.getPlayer().getGameMode();

                if (slot != -999 && slot != -1) {
                    if (mode != GameMode.SPECTATOR && mode != GameMode.CREATIVE) {
                        int maxSlots = activeContainer.slots.size();
                        // Slot numarası pencere boyutunu aşamaz
                        if (slot >= maxSlots) {
                            flag("Slot Index Out of Bounds (" + slot + " >= " + maxSlots + ")", packetName);
                            resync();
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
                    if (slot == 2 && clickType == InventoryClickType.SWAP) {
                        flag("Illegal Result Slot Swap", packetName);
                        return false;
                    }
                }

                // --- 8. Lectern Crash Fix ---
                if (plugin.getConfig().getBoolean("checks.window.prevent-lectern-spam")) {
                    if (type == InventoryType.LECTERN) {
                        if (clickType == InventoryClickType.QUICK_MOVE || clickType == InventoryClickType.SWAP) {
                            flag("Lectern Illegal Click", packetName);
                            return false;
                        }
                    }
                }

                // --- 9. GUI Swap Fix ---
                if (plugin.getConfig().getBoolean("checks.window.prevent-swap-in-gui", true)) {
                    if (windowId > 0 && clickType == InventoryClickType.SWAP) {
                        flag("Illegal GUI Swap", packetName);
                        resync();
                        return false;
                    }
                }

            } catch (Throwable t) {
                // MethodHandle hatası olursa güvenli tarafta kal
                t.printStackTrace();
            }
        }
        return true;
    }

    private void resync() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            data.getPlayer().updateInventory();
            if (data.getPlayer().getOpenInventory() != null) {
                // data.getPlayer().closeInventory(); // İsteğe bağlı
            }
        });
    }
}
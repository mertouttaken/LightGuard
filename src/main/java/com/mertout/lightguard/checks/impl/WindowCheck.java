package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.Container;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;
import net.minecraft.server.v1_16_R3.InventoryClickType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class WindowCheck extends Check {

    private static final MethodHandle WINDOW_ID_GETTER, SLOT_GETTER, CLICK_TYPE_GETTER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Field fWindowId = PacketPlayInWindowClick.class.getDeclaredField("a"); fWindowId.setAccessible(true);
            WINDOW_ID_GETTER = lookup.unreflectGetter(fWindowId);
            Field fSlot = PacketPlayInWindowClick.class.getDeclaredField("slot"); fSlot.setAccessible(true);
            SLOT_GETTER = lookup.unreflectGetter(fSlot);
            Field fShift = PacketPlayInWindowClick.class.getDeclaredField("shift"); fShift.setAccessible(true);
            CLICK_TYPE_GETTER = lookup.unreflectGetter(fShift);
        } catch (Exception e) { throw new RuntimeException("Failed to init WindowCheck", e); }
    }

    private final boolean preventLecternSpam;
    private final boolean preventSwapInGui;
    private long lastQuickMoveTime;
    private int quickMoveCount;

    public WindowCheck(PlayerData data) {
        super(data, "Window", "window");
        this.preventLecternSpam = plugin.getConfig().getBoolean("checks.window.prevent-lectern-spam");
        this.preventSwapInGui = plugin.getConfig().getBoolean("checks.window.prevent-swap-in-gui", true);
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInWindowClick) {
            PacketPlayInWindowClick click = (PacketPlayInWindowClick) packet;
            String packetName = "PacketPlayInWindowClick";

            try {
                int windowId = (int) WINDOW_ID_GETTER.invoke(click);
                int slot = (int) SLOT_GETTER.invoke(click);
                InventoryClickType clickType = (InventoryClickType) CLICK_TYPE_GETTER.invoke(click);
                Container activeContainer = ((CraftPlayer) data.getPlayer()).getHandle().activeContainer;

                if (windowId != activeContainer.windowId && windowId != 0) {
                    flag("Invalid Window ID", packetName);
                    resync();
                    return false;
                }

                if (slot < 0 && slot != -999 && slot != -1) {
                    flag("Negative Slot Crash", packetName);
                    return false;
                }

                GameMode mode = data.getPlayer().getGameMode();
                if (slot != -999 && slot != -1 && mode != GameMode.SPECTATOR && mode != GameMode.CREATIVE) {
                    if (slot >= activeContainer.slots.size()) {
                        flag("Slot Index Out of Bounds", packetName);
                        resync();
                        return false;
                    }
                }

                InventoryView view = data.getPlayer().getOpenInventory();
                InventoryType type = (view != null) ? view.getType() : InventoryType.CRAFTING;

                if (clickType == InventoryClickType.QUICK_MOVE) {
                    long now = System.currentTimeMillis();
                    if (now - lastQuickMoveTime > 1000) { quickMoveCount = 0; lastQuickMoveTime = now; }
                    quickMoveCount++;
                    int limit = (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER || type == InventoryType.MERCHANT) ? 8 : 15;
                    if (quickMoveCount > limit) {
                        flag("QuickMove Spam", packetName);
                        resync();
                        return false;
                    }
                }

                if ((type == InventoryType.MERCHANT || type == InventoryType.FURNACE) && slot == 2 && clickType == InventoryClickType.SWAP) {
                    flag("Illegal Result Slot Swap", packetName);
                    return false;
                }

                if (preventLecternSpam && type == InventoryType.LECTERN && (clickType == InventoryClickType.QUICK_MOVE || clickType == InventoryClickType.SWAP)) {
                    flag("Lectern Illegal Click", packetName);
                    return false;
                }

                if (preventSwapInGui && windowId > 0 && clickType == InventoryClickType.SWAP) {
                    flag("Illegal GUI Swap", packetName);
                    resync();
                    return false;
                }

            } catch (Throwable t) { t.printStackTrace(); }
        }
        return true;
    }

    private void resync() {
        Bukkit.getScheduler().runTask(plugin, () -> data.getPlayer().updateInventory());
    }
}
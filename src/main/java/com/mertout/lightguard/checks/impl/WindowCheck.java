package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.Container;
import net.minecraft.server.v1_16_R3.ContainerLectern;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WindowCheck extends Check {

    private static final MethodHandle WINDOW_ID_GETTER, SLOT_GETTER, BUTTON_GETTER, CLICK_TYPE_GETTER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Field fWindowId = PacketPlayInWindowClick.class.getDeclaredField("a"); fWindowId.setAccessible(true);
            WINDOW_ID_GETTER = lookup.unreflectGetter(fWindowId);

            Field fSlot = PacketPlayInWindowClick.class.getDeclaredField("slot"); fSlot.setAccessible(true);
            SLOT_GETTER = lookup.unreflectGetter(fSlot);

            Field fButton = PacketPlayInWindowClick.class.getDeclaredField("button"); fButton.setAccessible(true);
            BUTTON_GETTER = lookup.unreflectGetter(fButton);

            Field fShift = PacketPlayInWindowClick.class.getDeclaredField("shift"); fShift.setAccessible(true);
            CLICK_TYPE_GETTER = lookup.unreflectGetter(fShift);
        } catch (Exception e) { throw new RuntimeException("Failed to init WindowCheck", e); }
    }

    private final boolean preventLecternSpam;
    private final boolean preventSwapInGui;

    private final AtomicLong lastQuickMoveTime = new AtomicLong();
    private final AtomicInteger quickMoveCount = new AtomicInteger();

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
                int button = (int) BUTTON_GETTER.invoke(click);

                Enum<?> clickTypeEnum = (Enum<?>) CLICK_TYPE_GETTER.invoke(click);
                String clickTypeName = clickTypeEnum != null ? clickTypeEnum.name() : "";

                if (button < 0 || button > 99) {
                    flag("Invalid Button ID Exploit (" + button + ")", packetName);
                    return false;
                }

                if (slot < 0 && slot != -999 && slot != -1) {
                    flag("Negative Slot Crash", packetName);
                    return false;
                }

                if (clickTypeName.equals(("QUICK_MOVE"))) {
                    long now = System.currentTimeMillis();
                    if (now - lastQuickMoveTime.get() > 1000) {
                        quickMoveCount.set(0);
                        lastQuickMoveTime.set(now);
                    }
                    if (quickMoveCount.incrementAndGet() > 50) {
                        flag("QuickMove Spam (Rate Limit)", packetName);
                        return false;
                    }
                }

                net.minecraft.server.v1_16_R3.EntityPlayer nmsPlayer = ((CraftPlayer) data.getPlayer()).getHandle();
                Container activeContainer = nmsPlayer.activeContainer;

                if (activeContainer == null) {
                    resync();
                    return false;
                }

                if (windowId != 0 && windowId != activeContainer.windowId) {
                    resync();
                    return false;
                }

                if (preventLecternSpam && activeContainer instanceof ContainerLectern) {
                    if (clickTypeName.equals("QUICK_MOVE") || clickTypeName.equals("SWAP")) {
                        flag("Lectern Exploit Attempt", packetName);
                        resync();
                        return false;
                    }
                }

                GameMode mode = data.getPlayer().getGameMode();
                if (slot != -999 && slot != -1 && mode != GameMode.SPECTATOR && mode != GameMode.CREATIVE) {

                    java.util.List<?> slotsList = activeContainer.slots;
                    if (slotsList == null || slot >= slotsList.size()) {
                        resync();
                        return false;
                    }
                }

                if (preventSwapInGui && windowId > 0 && clickTypeName.equals("SWAP")) {
                    flag("Illegal GUI Swap", packetName);
                    resync();
                    return false;
                }

            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }

    private void resync() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (data.getPlayer().isOnline()) {
                data.getPlayer().updateInventory();
            }
        });
    }
}
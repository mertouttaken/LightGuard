package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

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
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public WindowCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInWindowClick)) return true;

        try {
            int windowId = (int) WINDOW_ID_GETTER.invoke(packet);
            int slot = (int) SLOT_GETTER.invoke(packet);
            int button = (int) BUTTON_GETTER.invoke(packet);
            Enum<?> type = (Enum<?>) CLICK_TYPE_GETTER.invoke(packet);
            String typeName = type != null ? type.name() : "";

            if (button < 0 || button > 99 || slot > 127) return false;

            // 🛡️ DÜZELTME: Netty Thread'de QuickMove Sayacı (ProtocolPlayer Cache)
            if (typeName.equals("QUICK_MOVE") && player.incrementAndGetQuickMoveCount(System.currentTimeMillis()) > 50) {
                return false;
            }

            // 🛡️ KRİTİK: Window ID Doğrulaması (Desync Dupe Koruması)
            if (windowId > 0 && player.getOpenWindowId() != windowId) {
                // Oyuncunun ekranında açık olmayan bir menüye paket yolluyor!
                return false;
            }

            if (windowId > 0 && typeName.equals("SWAP")) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Illegal GUI Swap Exploit");
                return false;
            }

        } catch (Throwable t) { return false; }
        return true;
    }
}
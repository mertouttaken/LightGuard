package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;

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

    public GameStateCheck(LightGuard plugin) {
        super(plugin);
        this.checkInvOpen = plugin.getConfig().getBoolean("checks.game-state.check-inventory-open");
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInWindowClick)) return true;

        if (checkInvOpen) {
            int windowId = (int) WINDOW_ID.get(packet);

            if (windowId > 0 && !player.isInventoryOpen()) {

                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Game State (Ghost Click)");
                return false;
            }

            if (windowId > 0 && player.getOpenWindowId() != windowId) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Window ID Mismatch (Spoof)");
                return false;
            }
        }
        return true;
    }
}
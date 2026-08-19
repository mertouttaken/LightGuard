package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

public class NoComCheck extends Check {

    public NoComCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundPlayerActionPacket)) return true;

        try {
            ServerboundPlayerActionPacket actionPacket = (ServerboundPlayerActionPacket) packet;
            ServerboundPlayerActionPacket.Action action = actionPacket.getAction();

            if (action == ServerboundPlayerActionPacket.Action.DROP_ITEM ||
                    action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS ||
                    action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND ||
                    action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                return true;
            }

            BlockPos pos = actionPacket.getPos();
            if (pos == null) return true;

            double pX = player.getLastX();
            double pY = player.getLastY();
            double pZ = player.getLastZ();

            if (!player.hasBaseline()) return true;

            double dx = pX - pos.getX();
            double dy = pY - pos.getY();
            double dz = pZ - pos.getZ();
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);

            if (distSq > 10000.0) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
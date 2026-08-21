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
            if (pos == null) return false;

            if (Math.abs(pos.getX()) > 30000000 || Math.abs(pos.getZ()) > 30000000 || pos.getY() < -500 || pos.getY() > 500) {
                return false;
            }

            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;

            if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(player.getCurrentWorldName(), chunkX, chunkZ)) {
                return false;
            }

            if (player.hasBaseline() && !player.isInTeleportGrace()) {
                double pX = player.getLastX();
                double pY = player.getLastY() + 1.62;
                double pZ = player.getLastZ();

                double dx = pX - (pos.getX() + 0.5);
                double dy = pY - (pos.getY() + 0.5);
                double dz = pZ - (pos.getZ() + 0.5);
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);

                double maxReachSq = player.isCreativeOrSpectator() ? 64.0 : 49.0;
                if (player.isBedrock()) maxReachSq = 100.0;

                if (distSq > maxReachSq + 15.0) {
                    return false;
                }
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
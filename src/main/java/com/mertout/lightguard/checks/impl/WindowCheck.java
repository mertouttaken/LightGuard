package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;

public class WindowCheck extends Check {

    public WindowCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundContainerClickPacket)) return true;

        try {
            ServerboundContainerClickPacket clickPacket = (ServerboundContainerClickPacket) packet;

            int slot = clickPacket.getSlotNum();
            int button = clickPacket.getButtonNum();
            ClickType type = clickPacket.getClickType();

            if (button < 0 || button > 100 || slot > 127 || (slot < -1 && slot != -999)) {
                return false;
            }

            if (type == ClickType.QUICK_MOVE) {
                if (player.incrementAndGetQuickMoveCount(System.currentTimeMillis()) > 50) {
                    return false;
                }
            } else if (type == ClickType.CLONE) {
                if (!player.isCreativeOrSpectator()) {
                    return false;
                }
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
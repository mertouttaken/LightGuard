package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

public class HeldItemSlotCheck extends Check {

    public HeldItemSlotCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundSetCarriedItemPacket)) return true;

        try {
            ServerboundSetCarriedItemPacket slotPacket = (ServerboundSetCarriedItemPacket) packet;
            int slot = slotPacket.getSlot();

            if (slot < 0 || slot >= 36) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
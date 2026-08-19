package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;

public class CreativeCheck extends Check {

    public CreativeCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundSetCreativeModeSlotPacket)) return true;

        try {
            if (!player.isCreativeOrSpectator()) {
                return false;
            }

            ServerboundSetCreativeModeSlotPacket creativePacket = (ServerboundSetCreativeModeSlotPacket) packet;
            int slot = creativePacket.slotNum();
            ItemStack item = creativePacket.itemStack();

            if (slot < -1 || slot > 45) {
                return false;
            }

            if (item == null || item.isEmpty()) {
                return true;
            }

            int count = item.getCount();

            if (count > 127 || count < 1) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
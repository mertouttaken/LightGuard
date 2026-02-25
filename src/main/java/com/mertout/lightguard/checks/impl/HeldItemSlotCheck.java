package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInHeldItemSlot;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class HeldItemSlotCheck extends Check {

    private static final MethodHandle SLOT_GETTER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Field fSlot;

            try {
                fSlot = PacketPlayInHeldItemSlot.class.getDeclaredField("itemInHandIndex");
            } catch (NoSuchFieldException e1) {
                try {
                    fSlot = PacketPlayInHeldItemSlot.class.getDeclaredField("slot");
                } catch (NoSuchFieldException e2) {
                    fSlot = PacketPlayInHeldItemSlot.class.getDeclaredField("a");
                }
            }

            fSlot.setAccessible(true);
            SLOT_GETTER = lookup.unreflectGetter(fSlot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init HeldItemSlotCheck", e);
        }
    }

    public HeldItemSlotCheck(PlayerData data) {
        super(data, "HeldItemSlot", "bad-packets");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInHeldItemSlot) {
            try {
                int slot = (int) SLOT_GETTER.invoke(packet);

                if (slot < 0 || slot >= 36) {
                    flag("Invalid Held Item Slot Exploit (" + slot + ")", "PacketPlayInHeldItemSlot");
                    return false;
                }

            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }
}
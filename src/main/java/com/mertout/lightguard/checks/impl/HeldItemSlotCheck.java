package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInHeldItemSlot;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class HeldItemSlotCheck extends Check {

    private static final VarHandle SLOT_FIELD;

    static {
        VarHandle slot = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInHeldItemSlot.class, MethodHandles.lookup());
            for (Field field : PacketPlayInHeldItemSlot.class.getDeclaredFields()) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    slot = lookup.unreflectVarHandle(field);
                    break;
                }
            }
        } catch (Exception ignored) {}
        SLOT_FIELD = slot;
    }

    public HeldItemSlotCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInHeldItemSlot)) return true;

        try {
            if (SLOT_FIELD == null) return true;

            int slot = (int) SLOT_FIELD.get(packet);

            if (slot < 0 || slot >= 36) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
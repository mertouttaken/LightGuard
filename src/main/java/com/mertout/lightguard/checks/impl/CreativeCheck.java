package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.ItemStack;
import net.minecraft.server.v1_16_R3.PacketPlayInSetCreativeSlot;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class CreativeCheck extends Check {

    private static final VarHandle SLOT_FIELD;
    private static final VarHandle ITEM_FIELD;

    static {
        VarHandle slot = null;
        VarHandle item = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInSetCreativeSlot.class, MethodHandles.lookup());
            for (Field field : PacketPlayInSetCreativeSlot.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == int.class && slot == null) {
                    slot = lookup.unreflectVarHandle(field);
                } else if (field.getType() == ItemStack.class && item == null) {
                    item = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}
        SLOT_FIELD = slot;
        ITEM_FIELD = item;
    }

    public CreativeCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInSetCreativeSlot)) return true;

        try {
            if (!player.isCreativeOrSpectator()) {
                return false;
            }

            if (SLOT_FIELD == null || ITEM_FIELD == null) {
                return true;
            }

            int slot = (int) SLOT_FIELD.get(packet);
            ItemStack item = (ItemStack) ITEM_FIELD.get(packet);

            if (slot < -1 || slot > 45) {
                return false;
            }

            if (item == null || item.isEmpty()) {
                return true;
            }

            int count = item.getCount();
            int maxStack = item.getMaxStackSize();

            if (count > maxStack || count < 1) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
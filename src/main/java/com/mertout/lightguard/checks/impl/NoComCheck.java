package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.PacketPlayInBlockDig;
import net.minecraft.server.v1_16_R3.PacketPlayInBlockDig.EnumPlayerDigType;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class NoComCheck extends Check {

    private static final VarHandle POS_FIELD;
    private static final VarHandle TYPE_FIELD;

    static {
        VarHandle pos = null;
        VarHandle type = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInBlockDig.class, MethodHandles.lookup());
            for (Field field : PacketPlayInBlockDig.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == BlockPosition.class && pos == null) {
                    pos = lookup.unreflectVarHandle(field);
                } else if (field.getType() == EnumPlayerDigType.class && type == null) {
                    type = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}
        POS_FIELD = pos;
        TYPE_FIELD = type;
    }

    public NoComCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInBlockDig)) return true;

        try {
            if (TYPE_FIELD == null || POS_FIELD == null) return true;

            EnumPlayerDigType type = (EnumPlayerDigType) TYPE_FIELD.get(packet);

            if (type == EnumPlayerDigType.DROP_ITEM ||
                    type == EnumPlayerDigType.DROP_ALL_ITEMS ||
                    type == EnumPlayerDigType.SWAP_ITEM_WITH_OFFHAND ||
                    type == EnumPlayerDigType.RELEASE_USE_ITEM) {
                return true;
            }

            BlockPosition pos = (BlockPosition) POS_FIELD.get(packet);
            if (pos == null) return true;

            double pX = player.getLastX();
            double pY = player.getLastY();
            double pZ = player.getLastZ();

            if (pX == -1) return true;

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
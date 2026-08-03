package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity.EnumEntityUseAction;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class EntityCheck extends Check {

    private static final VarHandle ENTITY_ID;
    private static final VarHandle ACTION_FIELD;

    static {
        VarHandle entityId = null;
        VarHandle action = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUseEntity.class, MethodHandles.lookup());
            for (Field field : PacketPlayInUseEntity.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == int.class && entityId == null) {
                    entityId = lookup.unreflectVarHandle(field);
                } else if (field.getType() == EnumEntityUseAction.class && action == null) {
                    action = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}
        ENTITY_ID = entityId;
        ACTION_FIELD = action;
    }

    private final boolean preventArmorStandSpam;

    public EntityCheck(LightGuard plugin) {
        super(plugin);
        this.preventArmorStandSpam = plugin.getConfig().getBoolean("checks.entity.prevent-armorstand-spam", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInUseEntity)) return true;

        try {
            if (player.isDead()) {
                return false;
            }

            if (player.isInventoryOpen() && player.getOpenWindowId() > 0) {
                return false;
            }

            if (preventArmorStandSpam && ACTION_FIELD != null) {
                EnumEntityUseAction action = (EnumEntityUseAction) ACTION_FIELD.get(packet);

                if (action == EnumEntityUseAction.ATTACK) {
                    long now = System.currentTimeMillis();
                    if (player.checkAndSetEntityInteractDelay(now, 50)) {
                        return false;
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
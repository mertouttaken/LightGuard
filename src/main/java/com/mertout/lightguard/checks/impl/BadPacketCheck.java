package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_16_R3.Vec3D;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class BadPacketCheck extends Check {

    private static final VarHandle ENTITY_ID;
    private static final VarHandle TARGET_VECTOR;
    private static final VarHandle ACTION_FIELD;

    static {
        VarHandle entityId = null;
        VarHandle targetVector = null;
        VarHandle actionField = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUseEntity.class, MethodHandles.lookup());
            for (Field field : PacketPlayInUseEntity.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == int.class && entityId == null) {
                    entityId = lookup.unreflectVarHandle(field);
                } else if (field.getType() == Vec3D.class && targetVector == null) {
                    targetVector = lookup.unreflectVarHandle(field);
                } else if (field.getType().isEnum() && actionField == null) {
                    actionField = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}

        ENTITY_ID = entityId;
        TARGET_VECTOR = targetVector;
        ACTION_FIELD = actionField;
    }

    private final boolean preventSelfInteract;

    public BadPacketCheck(LightGuard plugin) {
        super(plugin);
        this.preventSelfInteract = plugin.getConfig().getBoolean("checks.bad-packets.prevent-self-interact", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInUseEntity)) return true;

        try {
            if (ENTITY_ID == null) return true;

            int entityId = (int) ENTITY_ID.get(packet);

            if (preventSelfInteract && player.getPlayer() != null && entityId == player.getPlayer().getEntityId()) {
                return false;
            }

            if (ACTION_FIELD != null) {
                Enum<?> actionEnum = (Enum<?>) ACTION_FIELD.get(packet);

                if (actionEnum != null && actionEnum.name().equals("INTERACT_AT")) {
                    if (TARGET_VECTOR != null) {
                        Vec3D target = (Vec3D) TARGET_VECTOR.get(packet);
                        if (target != null) {
                            if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
                                return false;
                            }
                            if (Math.abs(target.x) > 30 || Math.abs(target.y) > 30 || Math.abs(target.z) > 30) {
                                return false;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
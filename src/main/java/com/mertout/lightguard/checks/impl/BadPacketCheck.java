package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_16_R3.Vec3D;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class BadPacketCheck extends Check {

    private static final VarHandle ENTITY_ID;
    private static final VarHandle TARGET_VECTOR;
    private static final MethodHandle ACTION_GETTER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUseEntity.class, MethodHandles.lookup());

            ENTITY_ID = lookup.findVarHandle(PacketPlayInUseEntity.class, "a", int.class);

            VarHandle targetHandle = null;
            try {
                targetHandle = lookup.findVarHandle(PacketPlayInUseEntity.class, "c", Vec3D.class);
            } catch (Exception e) {
                try {
                    targetHandle = lookup.findVarHandle(PacketPlayInUseEntity.class, "d", Vec3D.class);
                } catch (Exception ignored) {}
            }
            TARGET_VECTOR = targetHandle;

            MethodHandle actionHandle = null;
            for (Field field : PacketPlayInUseEntity.class.getDeclaredFields()) {
                if (field.getType().isEnum() && field.getName().equals("b")) {
                    field.setAccessible(true);
                    actionHandle = lookup.unreflectGetter(field);
                    break;
                }
            }
            ACTION_GETTER = actionHandle;

        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
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
            PacketPlayInUseEntity useEntity = (PacketPlayInUseEntity) packet;
            int entityId = (int) ENTITY_ID.get(useEntity);

            if (preventSelfInteract && player.getPlayer() != null && entityId == player.getPlayer().getEntityId()) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Self Interaction (Bad Packet)");
                return false;
            }

            if (ACTION_GETTER != null) {
                Enum<?> actionEnum = (Enum<?>) ACTION_GETTER.invoke(useEntity);

                if (actionEnum != null && actionEnum.name().equals("INTERACT_AT")) {

                    if (TARGET_VECTOR != null) {
                        Vec3D target = (Vec3D) TARGET_VECTOR.get(useEntity);
                        if (target != null) {
                            if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
                                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Interaction Vector");
                                return false;
                            }
                            if (Math.abs(target.x) > 30 || Math.abs(target.y) > 30 || Math.abs(target.z) > 30) {
                                plugin.getActionQueue().pushKick(player.getPlayer(), "Interaction Vector Out of Range");
                                return false;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return true;
        }

        return true;
    }
}
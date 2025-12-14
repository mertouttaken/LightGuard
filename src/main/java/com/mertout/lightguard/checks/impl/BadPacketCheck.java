package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_16_R3.Vec3D;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class BadPacketCheck extends Check {

    private static final VarHandle ENTITY_ID;
    private static final VarHandle TARGET_VECTOR;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUseEntity.class, MethodHandles.lookup());
            ENTITY_ID = lookup.findVarHandle(PacketPlayInUseEntity.class, "a", int.class);
            TARGET_VECTOR = lookup.findVarHandle(PacketPlayInUseEntity.class, "c", Vec3D.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private final boolean preventSelfInteract;

    public BadPacketCheck(PlayerData data) {
        super(data, "BadPacket", "bad-packets");
        this.preventSelfInteract = plugin.getConfig().getBoolean("checks.bad-packets.prevent-self-interact");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInUseEntity) {
            PacketPlayInUseEntity useEntity = (PacketPlayInUseEntity) packet;
            int entityId = (int) ENTITY_ID.get(useEntity);

            if (entityId == data.getPlayer().getEntityId() && preventSelfInteract) {
                flag("Self Interaction", "PacketPlayInUseEntity");
                return false;
            }

            Vec3D target = (Vec3D) TARGET_VECTOR.get(useEntity);
            if (target != null) {
                if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
                    flag("Invalid Interaction Vector", "PacketPlayInUseEntity");
                    return false;
                }
                if (Math.abs(target.x) > 30 || Math.abs(target.y) > 30 || Math.abs(target.z) > 30) {
                    flag("Interaction Vector Out of Range", "PacketPlayInUseEntity");
                    return false;
                }
            }
        }
        return true;
    }
}
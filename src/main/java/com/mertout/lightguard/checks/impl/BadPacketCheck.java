package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_16_R3.Vec3D;
import java.lang.reflect.Field;

public class BadPacketCheck extends Check {

    // Cache
    private final boolean preventSelfInteract;

    public BadPacketCheck(PlayerData data) {
        super(data, "BadPacket", "bad-packets");
        // Ayarı hafızaya al
        this.preventSelfInteract = plugin.getConfig().getBoolean("checks.bad-packets.prevent-self-interact");
    }

    @Override
    public boolean check(Object packet) {
        // 1. Ana şalter kapalıysa direkt çık
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInUseEntity) {
            PacketPlayInUseEntity useEntity = (PacketPlayInUseEntity) packet;

            // 2. Self Interact Kontrolü (Sadece bu ayar açıksa çalışır)
            int entityId = getEntityId(useEntity);
            if (entityId == data.getPlayer().getEntityId()) {
                if (preventSelfInteract) {
                    flag("Self Interaction", "PacketPlayInUseEntity");
                    return false;
                }
            }

            // 3. Vector Crash Fix (Bu ayardan bağımsız her zaman çalışmalı!)
            Vec3D target = getTargetVector(useEntity);
            if (target != null) {
                if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
                    flag("Invalid Interaction Vector (NaN/Infinity)", "PacketPlayInUseEntity");
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

    private int getEntityId(PacketPlayInUseEntity packet) {
        try {
            Field f = packet.getClass().getDeclaredField("a");
            f.setAccessible(true);
            return f.getInt(packet);
        } catch (Exception e) { return -1; }
    }

    private Vec3D getTargetVector(PacketPlayInUseEntity packet) {
        try {
            for (Field f : packet.getClass().getDeclaredFields()) {
                if (f.getType() == Vec3D.class) {
                    f.setAccessible(true);
                    return (Vec3D) f.get(packet);
                }
            }
        } catch (Exception e) {}
        return null;
    }
}
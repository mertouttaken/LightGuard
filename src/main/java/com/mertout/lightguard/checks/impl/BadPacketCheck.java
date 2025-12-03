package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_16_R3.Vec3D;
import java.lang.reflect.Field;

public class BadPacketCheck extends Check {

    public BadPacketCheck(PlayerData data) {
        super(data, "BadPacket");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.bad-packets.enabled")) return true;

        if (packet instanceof PacketPlayInUseEntity) {
            PacketPlayInUseEntity useEntity = (PacketPlayInUseEntity) packet;

            // 1. Entity Self-Interact (Kendine Tıklama)
            int entityId = getEntityId(useEntity);
            if (entityId == data.getPlayer().getEntityId()) {
                if (plugin.getConfig().getBoolean("checks.bad-packets.prevent-self-interact")) {
                    flag("Self Interaction");
                    return false;
                }
            }

            // ➤ YENİ: Vector (InteractAt) Crash Fix (Zırh Askısı)
            // Eğer etkileşim "INTERACT_AT" türündeyse, bir hedef vektörü (Vec3D) içerir.
            // Bu vektör NaN veya Infinity olursa sunucu çöker.
            Vec3D target = getTargetVector(useEntity);
            if (target != null) {
                if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
                    flag("Invalid Interaction Vector (NaN/Infinity)");
                    return false;
                }

                // Ekstra: Vektör çok büyükse (Örn: 30 blok öteye tıklama)
                if (Math.abs(target.x) > 30 || Math.abs(target.y) > 30 || Math.abs(target.z) > 30) {
                    flag("Interaction Vector Out of Range");
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

    // Reflection ile Vec3D (Hedef noktası) verisini okuma
    private Vec3D getTargetVector(PacketPlayInUseEntity packet) {
        try {
            // 1.16.5'te hedef vektör 'c' (Vec3D) field'ında tutulur (InteractAt için).
            // Field ismi farklı olabilir, garanti yöntem 'Vec3D' tipindeki field'ı aramaktır.
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
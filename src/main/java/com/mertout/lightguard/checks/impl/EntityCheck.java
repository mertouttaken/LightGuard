package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public class EntityCheck extends Check {

    private long lastArmorStandInteract = 0; // Zırh askısı zamanlayıcısı

    public EntityCheck(PlayerData data) {
        super(data, "EntitySecurity");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.entity.enabled")) return true;

        if (packet instanceof PacketPlayInUseEntity) {
            PacketPlayInUseEntity useEntity = (PacketPlayInUseEntity) packet;
            Player player = data.getPlayer();

            int entityId = getEntityId(useEntity);

            if (plugin.getConfig().getBoolean("checks.entity.prevent-invalid-entity")) {
                net.minecraft.server.v1_16_R3.Entity nmsEntity = ((CraftPlayer) player).getHandle().world.getEntity(entityId);

                if (nmsEntity == null) {
                    return false;
                }

                if (plugin.getConfig().getBoolean("checks.entity.prevent-armorstand-spam")) {
                    Entity bukkitEntity = nmsEntity.getBukkitEntity();
                    if (bukkitEntity.getType() == EntityType.ARMOR_STAND) {
                        long now = System.currentTimeMillis();

                        if (now - lastArmorStandInteract < 200) {
                            return false;
                        }

                        lastArmorStandInteract = now;
                    }
                }
            }
        }
        return true;
    }

    private int getEntityId(PacketPlayInUseEntity packet) {
        try {
            Field f = packet.getClass().getDeclaredField("a"); // 1.16.5'te 'a' field'ı entityId'dir
            f.setAccessible(true);
            return f.getInt(packet);
        } catch (Exception e) { return 0; }
    }
}
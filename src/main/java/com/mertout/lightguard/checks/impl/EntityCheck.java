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

    private long lastArmorStandInteract = 0;
    private final boolean preventInvalidEntity;
    private final boolean preventArmorStandSpam;

    public EntityCheck(PlayerData data) {
        super(data, "EntitySecurity", "entity");
        this.preventInvalidEntity = plugin.getConfig().getBoolean("checks.entity.prevent-invalid-entity");
        this.preventArmorStandSpam = plugin.getConfig().getBoolean("checks.entity.prevent-armorstand-spam");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInUseEntity) {
            PacketPlayInUseEntity useEntity = (PacketPlayInUseEntity) packet;
            int entityId = getEntityId(useEntity);

            if (preventInvalidEntity) {
                net.minecraft.server.v1_16_R3.Entity nmsEntity = ((CraftPlayer) data.getPlayer()).getHandle().world.getEntity(entityId);
                if (nmsEntity == null) return false;

                if (preventArmorStandSpam) {
                    Entity bukkitEntity = nmsEntity.getBukkitEntity();
                    if (bukkitEntity.getType() == EntityType.ARMOR_STAND) {
                        long now = System.currentTimeMillis();
                        if (now - lastArmorStandInteract < 200) return false;
                        lastArmorStandInteract = now;
                    }
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
        } catch (Exception e) { return 0; }
    }
}
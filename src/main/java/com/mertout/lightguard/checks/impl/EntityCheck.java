package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

public class EntityCheck extends Check {

    private final AtomicLong lastArmorStandInteract = new AtomicLong(0);

    private static final VarHandle ENTITY_ID;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUseEntity.class, MethodHandles.lookup());
            ENTITY_ID = lookup.findVarHandle(PacketPlayInUseEntity.class, "a", int.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

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
            int entityId = (int) ENTITY_ID.get(useEntity);

            if (preventInvalidEntity) {
                net.minecraft.server.v1_16_R3.Entity nmsEntity = ((CraftPlayer) data.getPlayer()).getHandle().world.getEntity(entityId);
                if (nmsEntity == null) return false;

                if (preventArmorStandSpam) {
                    Entity bukkitEntity = nmsEntity.getBukkitEntity();
                    if (bukkitEntity.getType() == EntityType.ARMOR_STAND) {
                        long now = System.currentTimeMillis();
                        if (now - lastArmorStandInteract.get() < 200) return false;
                        lastArmorStandInteract.set(now);
                    }
                }
            }
        }
        return true;
    }
}
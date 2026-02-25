package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

public class EntityCheck extends Check {

    private final AtomicLong lastArmorStandInteract = new AtomicLong(0);

    private static final VarHandle ENTITY_ID;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUseEntity.class, MethodHandles.lookup());
            VarHandle handle;
            try {
                handle = lookup.findVarHandle(PacketPlayInUseEntity.class, "a", int.class);
            } catch (NoSuchFieldException e) {
                handle = lookup.findVarHandle(PacketPlayInUseEntity.class, "entityId", int.class);
            }
            ENTITY_ID = handle;
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

            EntityPlayer nmsPlayer = ((CraftPlayer) data.getPlayer()).getHandle();

            if (nmsPlayer.getHealth() <= 0 || nmsPlayer.dead) {
                flag("Invalid Sequence (Interact while Dead)", "PacketPlayInUseEntity");
                return false;
            }

            if (nmsPlayer.activeContainer != null && nmsPlayer.activeContainer.windowId > 0) {
                flag("Invalid Sequence (Interact while GUI Open)", "PacketPlayInUseEntity");
                return false;
            }

            if (preventInvalidEntity) {
                net.minecraft.server.v1_16_R3.Entity nmsEntity = nmsPlayer.world.getEntity(entityId);

                if (nmsEntity == null) return false;

                if (preventArmorStandSpam) {
                    if (nmsEntity instanceof net.minecraft.server.v1_16_R3.EntityArmorStand) {
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
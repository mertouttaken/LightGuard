package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInUseEntity;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class EntityCheck extends Check {

    private static final VarHandle ENTITY_ID;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUseEntity.class, MethodHandles.lookup());
            ENTITY_ID = lookup.findVarHandle(PacketPlayInUseEntity.class, "a", int.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private final boolean preventArmorStandSpam;

    public EntityCheck(LightGuard plugin) {
        super(plugin);
        this.preventArmorStandSpam = plugin.getConfig().getBoolean("checks.entity.prevent-armorstand-spam", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInUseEntity)) return true;

        if (player.isDead()) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Sequence (Interact while Dead)");
            return false;
        }

        if (player.isInventoryOpen()) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Sequence (Interact while GUI Open)");
            return false;
        }

        if (preventArmorStandSpam) {
            long now = System.currentTimeMillis();
            if (player.checkAndSetEntityInteractDelay(now, 200)) {
                return false;
            }
        }

        return true;
    }
}
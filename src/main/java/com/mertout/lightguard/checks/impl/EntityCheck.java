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

        // 🛡️ DÜZELTME 1: NMS/Bukkit çağrıları yerine ProtocolPlayer Cache kullanıldı
        if (player.isDead()) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Sequence (Interact while Dead)");
            return false;
        }

        if (player.isInventoryOpen()) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Sequence (Interact while GUI Open)");
            return false;
        }

        if (preventArmorStandSpam) {
            // NOT: Netty thread'inde nmsPlayer.world.getEntity() yapmak tehlikelidir.
            // Bu nedenle sadece zamanlama (spam) kontrolünü oyuncuya özel sayaçla yapıyoruz.
            // Eğer daha derin bir kontrol (ArmorStand mı değil mi) gerekiyorsa ana thread'e asenkron sorulmalı.
            // Ancak paket bazlı spam koruması için tüm etkileşimlere 200ms sınırı koymak en güvenlisidir.

            long now = System.currentTimeMillis();
            if (player.checkAndSetEntityInteractDelay(now, 200)) {
                // Çok hızlı etkileşim (Spam), paketi sessizce yut
                return false;
            }
        }

        return true;
    }
}
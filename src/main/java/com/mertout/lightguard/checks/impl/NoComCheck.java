package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.PacketPlayInBlockDig;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class NoComCheck extends Check {

    private static final VarHandle POS_FIELD;

    static {
        try {
            POS_FIELD = MethodHandles.privateLookupIn(PacketPlayInBlockDig.class, MethodHandles.lookup())
                    .findVarHandle(PacketPlayInBlockDig.class, "a", BlockPosition.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    public NoComCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInBlockDig)) return true;

        try {
            BlockPosition pos = (BlockPosition) POS_FIELD.get(packet);
            if (pos == null) return true;

            // 🛡️ DÜZELTME: Bukkit API YASAK! Oyuncunun rotasını Netty'deki PositionCheck kaydediyordu, biz de oradan okuyoruz!
            double pX = player.getLastX();
            double pY = player.getLastY();
            double pZ = player.getLastZ();

            // Eğer adam daha önce hiç yürümediyse (-1 ise) geç
            if (pX != -1) {
                // Hızlı Mesafe Matematiği (Karekök almamak CPU'yu rahatlatır)
                double distSq = Math.pow(pX - pos.getX(), 2) + Math.pow(pY - pos.getY(), 2) + Math.pow(pZ - pos.getZ(), 2);

                // Normalde erişim mesafesi 6 blok (36.0 distSq). Tolerans payı ile 2500 limitinde tutmuşsun (NoCom için mantıklı).
                if (distSq > 2500.0) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "NoCom Exploit (Out of Range Block Interaction)");
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
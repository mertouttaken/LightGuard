package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.PacketPlayInBlockDig;
import org.bukkit.Location;

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

    public NoComCheck(PlayerData data) {
        super(data, "NoCom", "bad-packets");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInBlockDig) {
            try {
                BlockPosition pos = (BlockPosition) POS_FIELD.get(packet);
                if (pos == null) return true;

                Location pLoc = data.getPlayer().getLocation();

                double distSq = Math.pow(pLoc.getX() - pos.getX(), 2) +
                        Math.pow(pLoc.getY() - pos.getY(), 2) +
                        Math.pow(pLoc.getZ() - pos.getZ(), 2);

                if (distSq > 2500.0) {
                    flag("NoCom Exploit (Out of Range Block Interaction)", "PacketPlayInBlockDig");
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
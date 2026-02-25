package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;
import org.bukkit.GameMode;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class PositionCheck extends Check {

    private static final VarHandle X, Y, Z, HAS_POS;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            X = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "x", double.class);
            Y = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "y", double.class);
            Z = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "z", double.class);
            HAS_POS = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "hasPos", boolean.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private double lastX = -1, lastY = -1, lastZ = -1;

    public PositionCheck(PlayerData data) {
        super(data, "Position", "position");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInFlying) {
            try {
                boolean hasPos = (boolean) HAS_POS.get(packet);
                if (!hasPos) return true;

                double x = (double) X.get(packet);
                double y = (double) Y.get(packet);
                double z = (double) Z.get(packet);

                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    flag("Invalid Player Coordinates", "PacketPlayInFlying");
                    return false;
                }

                if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                    flag("World Border Exploit", "PacketPlayInFlying");
                    return false;
                }

                if (data.getGameMode() == GameMode.SPECTATOR || data.getGameMode() == GameMode.CREATIVE) {
                    lastX = x; lastY = y; lastZ = z;
                    return true;
                }

                if (lastX != -1) {
                    double diffX = Math.abs(x - lastX);
                    double diffY = Math.abs(y - lastY);
                    double diffZ = Math.abs(z - lastZ);

                    if (data.isTeleporting()) {
                        lastX = x; lastY = y; lastZ = z;
                        return true;
                    }

                    if ((diffX >= 10.0 && diffX % 1.0 == 0.0) ||
                            (diffZ >= 10.0 && diffZ % 1.0 == 0.0) ||
                            (diffY >= 10.0 && diffY % 1.0 == 0.0)) {

                        flag("Chunk Crasher (Integer Step)", "PacketPlayInFlying");
                        return false;
                    }

                    double distSq = (diffX * diffX) + (diffY * diffY) + (diffZ * diffZ);
                    if (distSq > 100.0) {
                        flag("Excessive Speed / Blink Exploit", "PacketPlayInFlying");
                        return false;
                    }
                }

                lastX = x;
                lastY = y;
                lastZ = z;

            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class PositionCheck extends Check {

    private static final VarHandle X, Y, Z, HAS_POS, YAW, PITCH, HAS_LOOK, ON_GROUND;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            X = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "x", double.class);
            Y = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "y", double.class);
            Z = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "z", double.class);
            HAS_POS = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "hasPos", boolean.class);
            YAW = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "yaw", float.class);
            PITCH = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "pitch", float.class);
            HAS_LOOK = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "hasLook", boolean.class);
            ON_GROUND = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "f", boolean.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private final double outOfWorldLimit;

    public PositionCheck(LightGuard plugin) {
        super(plugin);
        this.outOfWorldLimit = plugin.getConfig().getDouble("checks.position.out-of-world-limit", 29999984.0);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInFlying)) return true;

        try {
            boolean hasLook = (boolean) HAS_LOOK.get(packet);
            if (hasLook) {
                float yaw = (float) YAW.get(packet);
                float pitch = (float) PITCH.get(packet);

                if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                    return false;
                }

                if (Math.abs(pitch) > 90.0F) {
                    return false;
                }
                if (Math.abs(yaw) > 3.0E7F) {
                    return false;
                }
            }

            boolean hasPos = (boolean) HAS_POS.get(packet);
            if (!hasPos) return true;

            double x = (double) X.get(packet);
            double y = (double) Y.get(packet);
            double z = (double) Z.get(packet);

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return false;
            }

            if (Math.abs(x) > outOfWorldLimit || Math.abs(z) > outOfWorldLimit || Math.abs(y) > 1000000) {
                return false;
            }
            player.setLastLocation(x, y, z);

        } catch (Throwable t) {
            return true;
        }

        return true;
    }

    @Override
    public boolean isBedrockCompatible() {
        return false;
    }
}
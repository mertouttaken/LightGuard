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

    public PositionCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInFlying)) return true;

        try {
            if (player.isDead()) {
                return false;
            }

            boolean hasLook = (boolean) HAS_LOOK.get(packet);
            if (hasLook) {
                float yaw = (float) YAW.get(packet);
                float pitch = (float) PITCH.get(packet);

                if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Rotation (NaN/Inf)");
                    return false;
                }

                if (Math.abs(pitch) > 90.0F) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Pitch Angle (" + pitch + ")");
                    return false;
                }

                if (Math.abs(yaw) > 3.0E7F) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Extreme Yaw Angle Exploit");
                    return false;
                }
            }

            boolean hasPos = (boolean) HAS_POS.get(packet);
            if (!hasPos) return true;

            double x = (double) X.get(packet);
            double y = (double) Y.get(packet);
            double z = (double) Z.get(packet);

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Coordinates (NaN/Inf)");
                return false;
            }

            if (Math.abs(x) > 29999984 || Math.abs(z) > 29999984 || Math.abs(y) > 1000000) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Illegal Position (Border/Overflow)");
                return false;
            }

            int chunkX = (int) x >> 4;
            int chunkZ = (int) z >> 4;
            String worldName = player.getPlayer().getWorld().getName();

            if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, chunkX, chunkZ)) {
                if (!player.isInTeleportGrace()) {
                    return false;
                }
            }

            double lastX = player.getLastX();
            double lastY = player.getLastY();
            double lastZ = player.getLastZ();

            if (lastX != -1 && !player.isInTeleportGrace()) {
                double diffX = x - lastX;
                double diffY = y - lastY;
                double diffZ = z - lastZ;

                if (Math.abs(Math.abs(diffY) - 0.1) < 1e-7) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Vertical Step (0.1 Fly)");
                    return false;
                }

                if ((diffX >= 10.0 && diffX % 1.0 == 0.0) ||
                        (diffZ >= 10.0 && diffZ % 1.0 == 0.0) ||
                        (diffY >= 10.0 && diffY % 1.0 == 0.0)) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Integer Step Exploit");
                    return false;
                }

                double distSq = (diffX * diffX) + (diffY * diffY) + (diffZ * diffZ);
                if (distSq > 100.0) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Excessive Movement (Blink)");
                    return false;
                }
            }

            player.setLastLocation(x, y, z);

        } catch (Exception e) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isBedrockCompatible() {
        return false;
    }
}
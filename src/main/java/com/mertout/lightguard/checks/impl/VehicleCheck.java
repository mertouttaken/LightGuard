package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInVehicleMove;
import net.minecraft.server.v1_16_R3.PacketPlayInSteerVehicle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VehicleCheck extends Check {

    private static final VarHandle X, Y, Z, STEER_X, STEER_Z, JUMP_FIELD, UNMOUNT_FIELD;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            X = MethodHandles.privateLookupIn(PacketPlayInVehicleMove.class, lookup).findVarHandle(PacketPlayInVehicleMove.class, "a", double.class);
            Y = MethodHandles.privateLookupIn(PacketPlayInVehicleMove.class, lookup).findVarHandle(PacketPlayInVehicleMove.class, "b", double.class);
            Z = MethodHandles.privateLookupIn(PacketPlayInVehicleMove.class, lookup).findVarHandle(PacketPlayInVehicleMove.class, "c", double.class);
            STEER_X = MethodHandles.privateLookupIn(PacketPlayInSteerVehicle.class, lookup).findVarHandle(PacketPlayInSteerVehicle.class, "a", float.class);
            STEER_Z = MethodHandles.privateLookupIn(PacketPlayInSteerVehicle.class, lookup).findVarHandle(PacketPlayInSteerVehicle.class, "b", float.class);
            JUMP_FIELD = MethodHandles.privateLookupIn(PacketPlayInSteerVehicle.class, lookup).findVarHandle(PacketPlayInSteerVehicle.class, "c", boolean.class);
            UNMOUNT_FIELD = MethodHandles.privateLookupIn(PacketPlayInSteerVehicle.class, lookup).findVarHandle(PacketPlayInSteerVehicle.class, "d", boolean.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    public VehicleCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (packet instanceof PacketPlayInSteerVehicle) {
            try {
                float steerX = (float) STEER_X.get(packet);
                float steerZ = (float) STEER_Z.get(packet);
                if (!Float.isFinite(steerX) || !Float.isFinite(steerZ)) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Steering Data (NaN)");
                    return false;
                }
                if ((boolean) JUMP_FIELD.get(packet) && (boolean) UNMOUNT_FIELD.get(packet)) return false;
            } catch (Exception e) { return false; }
        }

        if (packet instanceof PacketPlayInVehicleMove) {
            try {
                double x = (double) X.get(packet);
                double y = (double) Y.get(packet);
                double z = (double) Z.get(packet);

                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;

                // 🛡️ DÜZELTME: Asenkron Chunk Kontrolü (Sıfır Lag)
                String worldName = player.getPlayer().getWorld().getName();
                if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, (int)x >> 4, (int)z >> 4)) {
                    return false;
                }

                double lX = player.getLastVehicleX();
                if (lX != -1) {
                    double distSq = Math.pow(x - lX, 2) + Math.pow(y - player.getLastVehicleY(), 2) + Math.pow(z - player.getLastVehicleZ(), 2);
                    if (distSq > 100.0 && !player.isInTeleportGrace()) {
                        plugin.getActionQueue().pushKick(player.getPlayer(), "Vehicle Speed/Blink Exploit");
                        return false;
                    }
                }
                player.setLastVehicleLocation(x, y, z);
            } catch (Exception e) { return false; }
        }
        return true;
    }
}
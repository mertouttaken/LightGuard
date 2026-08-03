package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInVehicleMove;
import net.minecraft.server.v1_16_R3.PacketPlayInSteerVehicle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class VehicleCheck extends Check {

    private static final VarHandle X, Y, Z, STEER_X, STEER_Z, JUMP_FIELD, UNMOUNT_FIELD;

    static {
        VarHandle x = null, y = null, z = null;
        VarHandle steerX = null, steerZ = null, jump = null, unmount = null;
        try {
            MethodHandles.Lookup moveLookup = MethodHandles.privateLookupIn(PacketPlayInVehicleMove.class, MethodHandles.lookup());
            int moveDoubleCount = 0;
            for (Field field : PacketPlayInVehicleMove.class.getDeclaredFields()) {
                if (field.getType() == double.class) {
                    field.setAccessible(true);
                    if (moveDoubleCount == 0) x = moveLookup.unreflectVarHandle(field);
                    else if (moveDoubleCount == 1) y = moveLookup.unreflectVarHandle(field);
                    else if (moveDoubleCount == 2) z = moveLookup.unreflectVarHandle(field);
                    moveDoubleCount++;
                }
            }

            MethodHandles.Lookup steerLookup = MethodHandles.privateLookupIn(PacketPlayInSteerVehicle.class, MethodHandles.lookup());
            int steerFloatCount = 0, steerBoolCount = 0;
            for (Field field : PacketPlayInSteerVehicle.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == float.class) {
                    if (steerFloatCount == 0) steerX = steerLookup.unreflectVarHandle(field);
                    else if (steerFloatCount == 1) steerZ = steerLookup.unreflectVarHandle(field);
                    steerFloatCount++;
                } else if (field.getType() == boolean.class) {
                    if (steerBoolCount == 0) jump = steerLookup.unreflectVarHandle(field);
                    else if (steerBoolCount == 1) unmount = steerLookup.unreflectVarHandle(field);
                    steerBoolCount++;
                }
            }
        } catch (Exception ignored) {}
        X = x; Y = y; Z = z;
        STEER_X = steerX; STEER_Z = steerZ;
        JUMP_FIELD = jump; UNMOUNT_FIELD = unmount;
    }

    public VehicleCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (packet instanceof PacketPlayInSteerVehicle) {
            try {
                if (STEER_X == null || STEER_Z == null || JUMP_FIELD == null || UNMOUNT_FIELD == null) return true;

                float steerX = (float) STEER_X.get(packet);
                float steerZ = (float) STEER_Z.get(packet);

                if (!Float.isFinite(steerX) || !Float.isFinite(steerZ)) {
                    return false;
                }
                if ((boolean) JUMP_FIELD.get(packet) && (boolean) UNMOUNT_FIELD.get(packet)) {
                    return false;
                }
            } catch (Throwable t) { return false; }
        }

        if (packet instanceof PacketPlayInVehicleMove) {
            try {
                if (X == null || Y == null || Z == null) return true;

                double pX = (double) X.get(packet);
                double pY = (double) Y.get(packet);
                double pZ = (double) Z.get(packet);

                if (!Double.isFinite(pX) || !Double.isFinite(pY) || !Double.isFinite(pZ)) return false;

                String worldName = player.getCurrentWorldName();
                if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, (int) pX >> 4, (int) pZ >> 4)) {
                    return false;
                }

                double lX = player.getLastVehicleX();
                if (lX != -1) {
                    double distSq = Math.pow(pX - lX, 2) + Math.pow(pY - player.getLastVehicleY(), 2) + Math.pow(pZ - player.getLastVehicleZ(), 2);
                    if (distSq > 100.0 && !player.isInTeleportGrace()) {
                        return false;
                    }
                }

                player.setLastVehicleLocation(pX, pY, pZ);
            } catch (Throwable t) { return false; }
        }
        return true;
    }
}
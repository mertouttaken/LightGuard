package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInVehicleMove;
import org.bukkit.Location;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VehicleCheck extends Check {

    private static final VarHandle X, Y, Z;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInVehicleMove.class, MethodHandles.lookup());
            X = lookup.findVarHandle(PacketPlayInVehicleMove.class, "x", double.class);
            Y = lookup.findVarHandle(PacketPlayInVehicleMove.class, "y", double.class);
            Z = lookup.findVarHandle(PacketPlayInVehicleMove.class, "z", double.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private double lastX = -1;
    private double lastY = -1;
    private double lastZ = -1;

    public VehicleCheck(PlayerData data) {
        super(data, "Vehicle", "vehicle");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInVehicleMove) {
            try {
                double x = (double) X.get(packet);
                double y = (double) Y.get(packet);
                double z = (double) Z.get(packet);

                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    flag("Invalid/Infinite Vehicle Coordinates", "PacketPlayInVehicleMove");
                    return false;
                }

                if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                    flag("Vehicle World Border Exploit", "PacketPlayInVehicleMove");
                    return false;
                }

                int chunkX = (int)x >> 4;
                int chunkZ = (int)z >> 4;
                if (!data.getPlayer().getWorld().isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }

                if (lastX != -1) {
                    double deltaX = x - lastX;
                    double deltaY = y - lastY;
                    double deltaZ = z - lastZ;
                    double distSq = (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ);

                    if (distSq > 16.0) {
                        if (!data.isTeleporting()) {
                            flag("Vehicle Speed/Blink Exploit", "PacketPlayInVehicleMove");
                            return false;
                        }
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
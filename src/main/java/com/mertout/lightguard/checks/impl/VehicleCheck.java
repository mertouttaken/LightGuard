package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInSteerVehicle;
import net.minecraft.server.v1_16_R3.PacketPlayInVehicleMove;

public class VehicleCheck extends Check {

    private final boolean preventInvalidMove;

    public VehicleCheck(PlayerData data) {
        super(data, "Vehicle", "vehicle");
        this.preventInvalidMove = plugin.getConfig().getBoolean("checks.vehicle.prevent-invalid-move");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInSteerVehicle) {
            PacketPlayInSteerVehicle steer = (PacketPlayInSteerVehicle) packet;
            float strafe = steer.b();
            float forward = steer.c();

            if (!Float.isFinite(strafe) || !Float.isFinite(forward)) {
                flag("Invalid Steer Values (NaN/Infinity)", "PacketPlayInSteerVehicle");
                return false;
            }

            if (steer.d()) {
                long now = System.currentTimeMillis();
                if (now - data.getLastVehicleJump() < 100) return false;
                data.setLastVehicleJump(now);
            }
        }

        if (packet instanceof PacketPlayInVehicleMove) {
            PacketPlayInVehicleMove move = (PacketPlayInVehicleMove) packet;
            String packetName = "PacketPlayInVehicleMove";
            double x = move.getX();
            double y = move.getY();
            double z = move.getZ();

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                flag("Invalid Vehicle Coordinates", packetName);
                return false;
            }
            if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                flag("Vehicle Out of World", packetName);
                return false;
            }
            if (preventInvalidMove && data.getPlayer().getVehicle() == null) {
                return false;
            }
        }
        return true;
    }
}
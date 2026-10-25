package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;

public class VehicleCheck extends Check {

    public VehicleCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (packet instanceof ServerboundMoveVehiclePacket movePacket) {
            try {
                Vec3 pos = movePacket.position();
                double pX = pos.x();
                double pY = pos.y();
                double pZ = pos.z();

                if (!Double.isFinite(pX) || !Double.isFinite(pY) || !Double.isFinite(pZ)
                        || !Float.isFinite(movePacket.yRot()) || !Float.isFinite(movePacket.xRot())) {
                    return false;
                }

                if (Math.abs(pX) > 29999984.0 || Math.abs(pZ) > 29999984.0 || Math.abs(pY) > 100000.0) {
                    return false;
                }

                String worldName = player.getCurrentWorldName();
                int chunkX = ((int) Math.floor(pX)) >> 4;
                int chunkZ = ((int) Math.floor(pZ)) >> 4;

                if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, chunkX, chunkZ)) {
                    return false;
                }

                if (player.hasBaseline() && !player.isInTeleportGrace()) {
                    double dx = pX - player.getLastX();
                    double dy = pY - player.getLastY();
                    double dz = pZ - player.getLastZ();
                    double distSqFromPlayer = (dx * dx) + (dy * dy) + (dz * dz);

                    if (distSqFromPlayer > 225.0) {
                        return false;
                    }
                }

                player.setLastVehicleLocation(pX, pY, pZ);
                player.setLastLocation(pX, pY, pZ);
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }
}
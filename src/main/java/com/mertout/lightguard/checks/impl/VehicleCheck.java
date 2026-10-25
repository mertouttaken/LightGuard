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

                double lX = player.getLastVehicleX();
                double lY = player.getLastVehicleY();
                double lZ = player.getLastVehicleZ();

                if (lX != -1.0 || lY != -1.0 || lZ != -1.0) {
                    double distSq = Math.pow(pX - lX, 2) + Math.pow(pY - lY, 2) + Math.pow(pZ - lZ, 2);

                    if (distSq > 2500.0) {
                        player.setLastVehicleLocation(pX, pY, pZ);
                        double playerDistSq = Math.pow(pX - player.getLastX(), 2) + Math.pow(pZ - player.getLastZ(), 2);
                        if (player.getLastX() != -1.0 && playerDistSq > 400.0 && !player.isInTeleportGrace()) {
                            return false;
                        }
                        return true;
                    }

                    if (distSq > 225.0 && !player.isInTeleportGrace()) {
                        return false;
                    }
                }

                player.setLastVehicleLocation(pX, pY, pZ);
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }
}
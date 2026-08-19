package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.phys.Vec3;

public class VehicleCheck extends Check {

    public VehicleCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (packet instanceof ServerboundPlayerInputPacket) {
            try {
                ServerboundPlayerInputPacket inputPacket = (ServerboundPlayerInputPacket) packet;

                if (inputPacket.input().jump() && inputPacket.input().shift()) {
                    return false;
                }
            } catch (Throwable t) {
                return false;
            }
        }

        if (packet instanceof ServerboundMoveVehiclePacket) {
            try {
                ServerboundMoveVehiclePacket movePacket = (ServerboundMoveVehiclePacket) packet;

                Vec3 pos = movePacket.position();
                double pX = pos.x();
                double pY = pos.y();
                double pZ = pos.z();

                if (!Double.isFinite(pX) || !Double.isFinite(pY) || !Double.isFinite(pZ)) return false;

                String worldName = player.getCurrentWorldName();

                int chunkX = (int) Math.floor(pX) >> 4;
                int chunkZ = (int) Math.floor(pZ) >> 4;

                if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, chunkX, chunkZ)) {
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
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }
}
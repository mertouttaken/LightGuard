package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;

/**
 * Validates vehicle movement.
 *
 * <p>This check deliberately does not look at {@code ServerboundPlayerInputPacket}. Up to 1.21.1
 * that packet was only sent while riding a vehicle, but 1.21.2 repurposed it to carry <em>all</em>
 * player input, every tick, for every player. It is now a record of seven booleans with no
 * invalid encoding, so there is nothing left to validate — and the previous rule here (reject when
 * jump and shift are held together) fired on ordinary sneak-jumping, dropping the packet and
 * recording a violation until ViolationRadar kicked the player.
 */
public class VehicleCheck extends Check {

    private static final double MAX_VEHICLE_MOVE_SQ = 100.0;

    public VehicleCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundMoveVehiclePacket)) return true;

        try {
            ServerboundMoveVehiclePacket movePacket = (ServerboundMoveVehiclePacket) packet;

            Vec3 pos = movePacket.position();
            double pX = pos.x();
            double pY = pos.y();
            double pZ = pos.z();

            if (!Double.isFinite(pX) || !Double.isFinite(pY) || !Double.isFinite(pZ)) return false;

            float yRot = movePacket.yRot();
            float xRot = movePacket.xRot();
            if (!Float.isFinite(yRot) || !Float.isFinite(xRot) || Math.abs(xRot) > 90.0F || Math.abs(yRot) > 3.0E7F) {
                return false;
            }

            int chunkX = (int) Math.floor(pX) >> 4;
            int chunkZ = (int) Math.floor(pZ) >> 4;

            if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(player.getCurrentWorldName(), chunkX, chunkZ)) {
                return false;
            }

            if (player.hasVehicleBaseline() && !player.isInTeleportGrace()) {
                double dX = pX - player.getLastVehicleX();
                double dY = pY - player.getLastVehicleY();
                double dZ = pZ - player.getLastVehicleZ();

                if ((dX * dX) + (dY * dY) + (dZ * dZ) > MAX_VEHICLE_MOVE_SQ) {
                    // Baseline is deliberately not updated here: the packet is dropped, so the
                    // server never moves the vehicle there. Recording the rejected position would
                    // measure the next legitimate packet against a position that never existed.
                    return false;
                }
            }

            player.setLastVehicleLocation(pX, pY, pZ);

        } catch (Throwable t) {
            return false;
        }
        return true;
    }
}

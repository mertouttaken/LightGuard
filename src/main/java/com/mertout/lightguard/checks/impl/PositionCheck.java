package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class PositionCheck extends Check {

    private final double maxOffsetSq;
    private final double maxElytraOffsetSq;
    private final double maxVerticalDist;
    private final double outOfWorldLimit;

    public PositionCheck(LightGuard plugin) {
        super(plugin);
        double offset = plugin.getConfig().getDouble("checks.position.max-offset", 25.0);
        this.maxOffsetSq = offset * offset;

        double elytraOffset = plugin.getConfig().getDouble("checks.position.elytra-max-distance", 50.0);
        this.maxElytraOffsetSq = elytraOffset * elytraOffset;

        this.maxVerticalDist = plugin.getConfig().getDouble("checks.position.max-vertical-distance", 50.0);
        this.outOfWorldLimit = plugin.getConfig().getDouble("checks.position.out-of-world-limit", 29999984.0);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundMovePlayerPacket)) return true;

        try {
            ServerboundMovePlayerPacket movePacket = (ServerboundMovePlayerPacket) packet;

            if (movePacket.hasRotation()) {
                float yaw = movePacket.getYRot(0);
                float pitch = movePacket.getXRot(0);

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

            if (!movePacket.hasPosition()) return true;

            double x = movePacket.getX(player.getLastX());
            double y = movePacket.getY(player.getLastY());
            double z = movePacket.getZ(player.getLastZ());

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return false;
            }

            if (Math.abs(x) > outOfWorldLimit || Math.abs(z) > outOfWorldLimit || Math.abs(y) > 1000000) {
                return false;
            }

            int chunkX = (int) Math.floor(x) >> 4;
            int chunkZ = (int) Math.floor(z) >> 4;

            String worldName = player.getCurrentWorldName();

            boolean chunkLoaded = plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, chunkX, chunkZ);

            if (!chunkLoaded && !player.isInTeleportGrace()) {
                return false;
            }

            double lastX = player.getLastX();
            double lastY = player.getLastY();
            double lastZ = player.getLastZ();

            if (lastX != -1 && !player.isInTeleportGrace()) {
                double diffX = x - lastX;
                double diffY = y - lastY;
                double diffZ = z - lastZ;

                double epsilon = 1e-5;
                boolean isXInteger = Math.abs(Math.abs(diffX) - Math.round(Math.abs(diffX))) < epsilon;
                boolean isYInteger = Math.abs(Math.abs(diffY) - Math.round(Math.abs(diffY))) < epsilon;
                boolean isZInteger = Math.abs(Math.abs(diffZ) - Math.round(Math.abs(diffZ))) < epsilon;

                if ((Math.abs(diffX) >= 10.0 && isXInteger) ||
                        (Math.abs(diffZ) >= 10.0 && isZInteger) ||
                        (Math.abs(diffY) >= 10.0 && isYInteger)) {
                    player.setLastLocation(x, y, z);
                    return false;
                }

                double horizontalDistSq = (diffX * diffX) + (diffZ * diffZ);
                double verticalDist = Math.abs(diffY);

                if (horizontalDistSq > maxOffsetSq) {
                    if (!player.isGliding()) {
                        player.setLastLocation(x, y, z);
                        return false;
                    } else if (horizontalDistSq > maxElytraOffsetSq) {
                        player.setLastLocation(x, y, z);
                        return false;
                    }
                }

                if (verticalDist > maxVerticalDist) {
                    player.setLastLocation(x, y, z);
                    return false;
                }

                if (player.isGliding()) {
                    double totalDistSq = horizontalDistSq + (diffY * diffY);
                    double combinedLimitSq = maxElytraOffsetSq + (maxVerticalDist * maxVerticalDist);
                    if (totalDistSq > combinedLimitSq) {
                        player.setLastLocation(x, y, z);
                        return false;
                    }
                }
            }

            player.setLastLocation(x, y, z);

        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isBedrockCompatible() {
        return false;
    }
}
package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class PositionCheck extends Check {

    private static final double ABSURD_Y = 1_000_000.0;
    private static final double INTEGER_JUMP_THRESHOLD = 10.0;
    private static final double INTEGER_EPSILON = 1e-5;

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
        if (!(packet instanceof ServerboundMovePlayerPacket movePacket)) return true;

        try {
            if (movePacket.hasRotation()) {
                float yaw = movePacket.getYRot(0.0F);
                float pitch = movePacket.getXRot(0.0F);

                if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return false;
                if (Math.abs(pitch) > 90.01F) return false;
                if (Math.abs(yaw) > 3.0E7F) return false;
            }

            if (!movePacket.hasPosition()) return true;

            double x = movePacket.getX(player.getLastX());
            double y = movePacket.getY(player.getLastY());
            double z = movePacket.getZ(player.getLastZ());

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;

            if (Math.abs(x) > outOfWorldLimit || Math.abs(z) > outOfWorldLimit || Math.abs(y) > ABSURD_Y) {
                return false;
            }

            int chunkX = (int) Math.floor(x) >> 4;
            int chunkZ = (int) Math.floor(z) >> 4;

            boolean chunkLoaded = plugin.getWorldChunkManager()
                    .isChunkLoadedAsynchronously(player.getCurrentWorldName(), chunkX, chunkZ);

            if (!chunkLoaded && !player.isInTeleportGrace()) return false;

            if (player.hasBaseline() && !player.isInTeleportGrace()) {
                double diffX = x - player.getLastX();
                double diffY = y - player.getLastY();
                double diffZ = z - player.getLastZ();

                if (!player.isGliding() && (isIntegerJump(diffX) || isIntegerJump(diffZ))) {
                    return false;
                }

                double horizontalDistSq = (diffX * diffX) + (diffZ * diffZ);

                if (horizontalDistSq > maxOffsetSq) {
                    if (!player.isGliding() || horizontalDistSq > maxElytraOffsetSq) {
                        return false;
                    }
                }

                if (Math.abs(diffY) > maxVerticalDist) return false;

                if (player.isGliding()) {
                    double totalDistSq = horizontalDistSq + (diffY * diffY);
                    double combinedLimitSq = maxElytraOffsetSq + (maxVerticalDist * maxVerticalDist);
                    if (totalDistSq > combinedLimitSq) return false;
                }
            }

            player.setLastLocation(x, y, z);

        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    private static boolean isIntegerJump(double diff) {
        double abs = Math.abs(diff);
        if (abs < INTEGER_JUMP_THRESHOLD) return false;
        return Math.abs(abs - Math.round(abs)) < INTEGER_EPSILON;
    }

    @Override
    public boolean isBedrockCompatible() {
        return false;
    }
}
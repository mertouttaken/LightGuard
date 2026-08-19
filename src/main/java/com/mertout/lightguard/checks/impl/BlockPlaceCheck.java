package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BlockPlaceCheck extends Check {

    private final int maxPPS;
    private final boolean printerCheckEnabled;
    private final int printerThreshold;
    private final boolean checkCoordinates;

    public BlockPlaceCheck(LightGuard plugin) {
        super(plugin);
        this.maxPPS = plugin.getConfig().getInt("checks.block-place.max-pps", 15);
        this.printerCheckEnabled = plugin.getConfig().getBoolean("checks.block-place.printer-check");
        this.printerThreshold = plugin.getConfig().getInt("checks.block-place.printer-threshold", 10);
        this.checkCoordinates = plugin.getConfig().getBoolean("checks.block-place.check-coordinates", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        boolean isAirClick = packet instanceof ServerboundUseItemPacket;
        boolean isBlockClick = packet instanceof ServerboundUseItemOnPacket;

        if (!isAirClick && !isBlockClick) return true;

        try {
            long now = System.currentTimeMillis();

            if (player.isDead()) {
                return false;
            }

            if (player.incrementAndGetBlockPlaceCount(now) > maxPPS) {
                return false;
            }

            if (printerCheckEnabled) {
                if (player.incrementAndGetPrinterCount(now) > printerThreshold) {
                    player.setPrinterMode(true);
                }
            }

            if (isBlockClick) {
                ServerboundUseItemOnPacket usePacket = (ServerboundUseItemOnPacket) packet;
                BlockHitResult hitResult = usePacket.getHitResult();

                if (hitResult == null) {
                    return true;
                }

                if (checkCoordinates) {
                    Vec3 vec = hitResult.getLocation();

                    if (!Double.isFinite(vec.x()) || !Double.isFinite(vec.y()) || !Double.isFinite(vec.z())) {
                        return false;
                    }

                    BlockPos pos = hitResult.getBlockPos();

                    if (Math.abs(pos.getX()) > 30000000 || Math.abs(pos.getZ()) > 30000000 || pos.getY() < -500 || pos.getY() > 500) {
                        return false;
                    }

                    double px = player.getLastX();
                    double py = player.getLastY();
                    double pz = player.getLastZ();

                    if (px != -1 && !player.isInTeleportGrace()) {
                        double dx = px - vec.x();
                        double dy = (py + 1.62) - vec.y();
                        double dz = pz - vec.z();

                        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                        double maxReachSq = player.isCreativeOrSpectator() ? 49.0 : 36.0;

                        if (distSq > maxReachSq + 10.0) {
                            return false;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
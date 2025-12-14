package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;
import java.util.concurrent.atomic.AtomicReference;

public class PositionCheck extends Check {

    private static class Position {
        final double x, y, z;
        Position(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private final AtomicReference<Position> lastPos = new AtomicReference<>(new Position(-1, -1, -1));

    public PositionCheck(PlayerData data) {
        super(data, "Position", "position");
    }

    @Override
    public boolean isBedrockCompatible() {
        return false;
    }

    @Override
    public boolean check(Object packet) {
        if (data.isTeleporting()) {
            if (packet instanceof PacketPlayInFlying && ((PacketPlayInFlying) packet).hasPos) {
                PacketPlayInFlying p = (PacketPlayInFlying) packet;
                updateLastPos(p.a(0), p.b(0), p.c(0));
            }
            return true;
        }

        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInFlying) {
            PacketPlayInFlying p = (PacketPlayInFlying) packet;
            if (p.hasPos) {
                double x = p.a(0);
                double y = p.b(0);
                double z = p.c(0);

                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    flag("NaN/Infinity Coordinate", "PacketPlayInFlying");
                    return false;
                }

                double worldLimit = plugin.getConfig().getDouble("checks.position.out-of-world-limit", 30000000.0);
                if (Math.abs(x) > worldLimit || Math.abs(z) > worldLimit) {
                    flag("Out of World Limit", "PacketPlayInFlying");
                    return false;
                }

                Position last = lastPos.get();
                if (last.x != -1) {
                    double deltaX = x - last.x;
                    double deltaY = y - last.y;
                    double deltaZ = z - last.z;
                    double speedSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

                    if (data.getPlayer().isGliding()) {
                        double limit = plugin.getConfig().getDouble("checks.position.elytra-speed-limit", 4.0);
                        if (speedSquared > limit * limit) {
                            flag("Elytra Speed Limit (" + String.format("%.2f", Math.sqrt(speedSquared)) + ")", "PacketPlayInFlying");
                            return false;
                        }
                    }

                    double maxOffset = plugin.getConfig().getDouble("checks.position.max-offset", 20.0);
                    if (speedSquared > maxOffset * maxOffset) {
                        flag("Moved too fast", "PacketPlayInFlying");
                        return false;
                    }
                }
                updateLastPos(x, y, z);
            }
        }
        return true;
    }

    private void updateLastPos(double x, double y, double z) {
        lastPos.set(new Position(x, y, z));
    }
}
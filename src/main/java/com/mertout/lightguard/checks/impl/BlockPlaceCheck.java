package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class BlockPlaceCheck extends Check {

    private static final VarHandle POSITION_BLOCK_FIELD;

    static {
        VarHandle posBlock = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUseItem.class, MethodHandles.lookup());
            for (Field field : PacketPlayInUseItem.class.getDeclaredFields()) {
                if (field.getType() == MovingObjectPositionBlock.class) {
                    field.setAccessible(true);
                    posBlock = lookup.unreflectVarHandle(field);
                    break;
                }
            }
        } catch (Exception ignored) {}
        POSITION_BLOCK_FIELD = posBlock;
    }

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
        if (!(packet instanceof PacketPlayInUseItem)) return true;

        try {
            if (POSITION_BLOCK_FIELD == null) {
                return true;
            }

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

            MovingObjectPositionBlock position = (MovingObjectPositionBlock) POSITION_BLOCK_FIELD.get(packet);

            if (position == null || position.getDirection() == null || position.getPos() == null || position.getBlockPosition() == null) {
                return true;
            }

            if (checkCoordinates) {
                Vec3D vec = position.getPos();

                if (!Double.isFinite(vec.x) || !Double.isFinite(vec.y) || !Double.isFinite(vec.z)) {
                    return false;
                }

                BlockPosition pos = position.getBlockPosition();

                if (Math.abs(pos.getX()) > 30000000 || Math.abs(pos.getZ()) > 30000000 || pos.getY() < -500 || pos.getY() > 500) {
                    return false;
                }

                double px = player.getLastX();
                double py = player.getLastY();
                double pz = player.getLastZ();

                if (px != -1 && !player.isInTeleportGrace()) {
                    double dx = px - vec.x;
                    double dy = (py + 1.62) - vec.y;
                    double dz = pz - vec.z;

                    double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                    double maxReachSq = player.isCreativeOrSpectator() ? 49.0 : 36.0;

                    if (distSq > maxReachSq + 10.0) {
                        return false;
                    }
                }
            }
        } catch (Throwable t) {
            return true;
        }

        return true;
    }
}
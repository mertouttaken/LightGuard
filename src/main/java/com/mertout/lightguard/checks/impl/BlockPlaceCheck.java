package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BlockPlaceCheck extends Check {

    private final int maxPPS;
    private final boolean checkCoordinates;
    private final Set<String> illegalBlocks = new HashSet<>();

    public BlockPlaceCheck(LightGuard plugin) {
        super(plugin);
        this.maxPPS = plugin.getConfig().getInt("checks.block-place.max-pps", 15);
        this.checkCoordinates = plugin.getConfig().getBoolean("checks.block-place.check-coordinates", true);

        for (String block : plugin.getConfig().getStringList("checks.block-place.illegal-blocks")) {
            String name = block.toLowerCase(Locale.ROOT);
            if (name.startsWith("minecraft:")) {
                name = name.substring(10);
            }
            illegalBlocks.add(name);
        }
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

            Player bp = player.getPlayer();
            if (!player.isAdmin() && bp != null) {
                ItemStack main = bp.getInventory().getItemInMainHand();
                ItemStack off = bp.getInventory().getItemInOffHand();
                if (isIllegalItem(main) || isIllegalItem(off)) {
                    return false;
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

                    int chunkX = pos.getX() >> 4;
                    int chunkZ = pos.getZ() >> 4;
                    if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(player.getCurrentWorldName(), chunkX, chunkZ)) {
                        return false;
                    }

                    double px = player.getLastX();
                    double py = player.getLastY();
                    double pz = player.getLastZ();

                    if (player.hasBaseline() && !player.isInTeleportGrace()) {
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

    private boolean isIllegalItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name().toLowerCase(Locale.ROOT);
        return illegalBlocks.contains(name);
    }
}
package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import com.mertout.lightguard.utils.NBTChecker;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BlockPlaceCheck extends Check {

    private final AtomicLong lastPlaceTime = new AtomicLong();
    private final AtomicInteger placePackets = new AtomicInteger();
    private final AtomicLong lastPrinterCheck = new AtomicLong();
    private final AtomicInteger printerPackets = new AtomicInteger();

    private final int maxPPS;
    private final boolean printerCheckEnabled;
    private final int printerThreshold;
    private final boolean checkCoordinates;
    private final boolean preventIllegalBlocks;
    private final boolean kickOnIllegal;
    private final int maxItemDepth;

    private final Set<Item> illegalNMSItems = new HashSet<>();

    public BlockPlaceCheck(PlayerData data) {
        super(data, "BlockPlace", "block-place");
        this.maxPPS = plugin.getConfig().getInt("checks.block-place.max-pps", 15);
        this.printerCheckEnabled = plugin.getConfig().getBoolean("checks.block-place.printer-check");
        this.printerThreshold = plugin.getConfig().getInt("checks.block-place.printer-threshold", 10);
        this.checkCoordinates = plugin.getConfig().getBoolean("checks.block-place.check-coordinates", true);
        this.preventIllegalBlocks = plugin.getConfig().getBoolean("checks.block-place.prevent-illegal-blocks");
        this.kickOnIllegal = plugin.getConfig().getBoolean("checks.block-place.kick-on-illegal-block", false);
        this.maxItemDepth = plugin.getConfig().getInt("checks.item.max-depth", 15);

        this.illegalNMSItems.addAll(plugin.getConfigManager().getIllegalBlocks());
    }

    @Override
    public boolean isBedrockCompatible() {
        return false;
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInUseItem) {
            PacketPlayInUseItem p = (PacketPlayInUseItem) packet;
            String packetName = "PacketPlayInUseItem";
            long now = System.currentTimeMillis();

            if (now - lastPlaceTime.get() > 1000) {
                placePackets.set(0);
                lastPlaceTime.set(now);
            }
            if (placePackets.incrementAndGet() > maxPPS) {
                flag("Block Place Flood", packetName);
                return false;
            }

            if (printerCheckEnabled) {
                if (now - lastPrinterCheck.get() > 1000) {
                    printerPackets.set(0);
                    lastPrinterCheck.set(now);
                }
                if (printerPackets.incrementAndGet() > printerThreshold) {
                    data.setPrinterMode(true);
                }
            }

            MovingObjectPositionBlock position = p.c();
            if (position == null || position.getDirection() == null) return false;

            if (checkCoordinates) {
                Vec3D vec = position.getPos();
                if (!Double.isFinite(vec.x) || !Double.isFinite(vec.y) || !Double.isFinite(vec.z)) {
                    flag("Invalid Cursor", packetName); return false;
                }
                BlockPosition pos = position.getBlockPosition();
                if (Math.abs(pos.getX()) > 30000000 || Math.abs(pos.getZ()) > 30000000) {
                    flag("Invalid Coordinates", packetName); return false;
                }
            }

            EnumHand hand = p.b();
            EntityPlayer nmsPlayer = ((CraftPlayer) data.getPlayer()).getHandle();
            ItemStack nmsItem = (hand == EnumHand.MAIN_HAND) ? nmsPlayer.inventory.getItemInHand() : nmsPlayer.inventory.extraSlots.get(0);

            if (nmsItem != null && !nmsItem.isEmpty()) {
                if (preventIllegalBlocks) {
                    if (illegalNMSItems.contains(nmsItem.getItem())) {
                        if (kickOnIllegal) {
                            flag("Illegal Block", packetName);
                        }
                        return false;
                    }
                }

                if (nmsItem.hasTag() && NBTChecker.isNBTDangerous(nmsItem.getTag(), maxItemDepth)) {
                    flag("Dangerous NBT", packetName);
                    return false;
                }
            }
        }
        return true;
    }
}
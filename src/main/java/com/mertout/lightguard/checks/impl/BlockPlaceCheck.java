package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.*;

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
        if (!(packet instanceof PacketPlayInUseItem)) return true;

        PacketPlayInUseItem p = (PacketPlayInUseItem) packet;
        long now = System.currentTimeMillis();

        // 🛡️ DÜZELTME 1: NMS Player okumak YASAK. Tüm statüler ProtocolPlayer önbelleğinden gelir.
        if (player.isDead()) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Sequence (Place while Dead)");
            return false;
        }

        if (player.isInventoryOpen()) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Sequence (Place while GUI Open)");
            return false;
        }

        // 🛡️ DÜZELTME 2: Veri Kanamasını önlemek için sayım ProtocolPlayer üzerinde yapılır.
        if (player.incrementAndGetBlockPlaceCount(now) > maxPPS) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Block Place Flood");
            return false;
        }

        if (printerCheckEnabled) {
            if (player.incrementAndGetPrinterCount(now) > printerThreshold) {
                player.setPrinterMode(true);
            }
        }

        // 3. KOORDİNAT KONTROLLERİ
        MovingObjectPositionBlock position = p.c();
        if (position == null || position.getDirection() == null) return false;

        if (checkCoordinates) {
            Vec3D vec = position.getPos();
            if (!Double.isFinite(vec.x) || !Double.isFinite(vec.y) || !Double.isFinite(vec.z)) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Cursor");
                return false;
            }
            BlockPosition pos = position.getBlockPosition();
            if (Math.abs(pos.getX()) > 30000000 || Math.abs(pos.getZ()) > 30000000) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Coordinates");
                return false;
            }
        }
        return true;
    }
}
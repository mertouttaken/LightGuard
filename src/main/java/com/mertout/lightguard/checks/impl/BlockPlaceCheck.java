package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import com.mertout.lightguard.utils.NBTChecker;
import net.minecraft.server.v1_16_R3.*; // NMS
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.Material;
import org.bukkit.Bukkit;

public class BlockPlaceCheck extends Check {

    private long lastPlaceTime;
    private int placePackets;
    private long lastPrinterCheck;
    private int printerPackets;

    public BlockPlaceCheck(PlayerData data) {
        super(data, "BlockPlace");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.block-place.enabled")) return true;

        // 1.16.5'te Blok Koyma verisi "PacketPlayInUseItem" içindedir.
        if (packet instanceof PacketPlayInUseItem) {
            PacketPlayInUseItem p = (PacketPlayInUseItem) packet;
            long now = System.currentTimeMillis();

            // --- 1. Flood Control ---
            if (now - lastPlaceTime > 1000) {
                placePackets = 0;
                lastPlaceTime = now;
            }
            placePackets++;
            if (placePackets > plugin.getConfig().getInt("checks.block-place.max-pps", 15)) {
                flag("Block Place Flood");
                return false;
            }

            // --- 2. Printer Mode ---
            if (plugin.getConfig().getBoolean("checks.block-place.printer-check")) {
                if (now - lastPrinterCheck > 1000) {
                    printerPackets = 0;
                    lastPrinterCheck = now;
                }
                printerPackets++;
                if (printerPackets > plugin.getConfig().getInt("checks.block-place.printer-threshold", 10)) {
                    data.setPrinterMode(true);
                }
            }

            // --- 3. Verileri Alma (DOĞRU METOTLAR) ---
            // 1.16.5 NMS:
            // p.c() -> MovingObjectPositionBlock (Tıklanan Blok)
            // p.b() -> EnumHand (El)

            MovingObjectPositionBlock position = p.c();

            if (position == null || position.getDirection() == null) {
                return false;
            }

            BlockPosition pos = position.getBlockPosition();

            // --- 4. World Boundary ---
            if (Math.abs(pos.getX()) > 30000000 || Math.abs(pos.getZ()) > 30000000) {
                flag("Invalid Coordinates (OOB)");
                return false;
            }

            // --- 5. Item ve Blok Kontrolü ---
            EnumHand hand = p.b();

            org.bukkit.inventory.ItemStack item = (hand == EnumHand.MAIN_HAND) ?
                    data.getPlayer().getInventory().getItemInMainHand() :
                    data.getPlayer().getInventory().getItemInOffHand();

            if (item != null && item.getType() != Material.AIR) {
                Material type = item.getType();

                // ➤ Dispenser Fix
                if (type == Material.DISPENSER || type == Material.DROPPER) {
                    int y = pos.getY();
                    EnumDirection face = position.getDirection();
                    if ((y <= 0 && face == EnumDirection.DOWN) || (y >= 255 && face == EnumDirection.UP) || y < 1 || y > 254) {
                        flag("Invalid Dispenser Position");
                        return false;
                    }
                }

                // ➤ Illegal Blocks (Bedrock Testi Burada Yapılıyor)
                if (plugin.getConfig().getBoolean("checks.block-place.prevent-illegal-blocks")) {
                    if (plugin.getConfig().getStringList("checks.block-place.illegal-blocks").contains(type.name())) {

                        if (plugin.getConfig().getBoolean("checks.block-place.kick-on-illegal-block", false)) {
                            flag("Illegal Block Placement: " + type.name());
                        } else {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                data.getPlayer().sendMessage("§c§lLightGuard: §7Bu bloğu (" + type.name() + ") koymanız yasaklanmıştır.");
                                // Zorla silmek istersen:
                                // data.getPlayer().getInventory().setItemInMainHand(null);
                            });
                        }

                        return false; // Paketi iptal et
                    }
                }

                // ➤ NBT Check
                net.minecraft.server.v1_16_R3.ItemStack nms = CraftItemStack.asNMSCopy(item);
                if (nms.hasTag()) {
                    if (NBTChecker.isNBTDangerous(nms.getTag(), plugin.getConfig())) {
                        flag("Dangerous NBT Data");
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
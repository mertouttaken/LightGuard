package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.world.inventory.ClickType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

public class WindowCheck extends Check {

    public WindowCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (packet instanceof ServerboundPickItemFromBlockPacket) {
            try {
                ServerboundPickItemFromBlockPacket pickPacket = (ServerboundPickItemFromBlockPacket) packet;
                if (pickPacket.includeData() && !player.isCreativeOrSpectator()) {
                    return false;
                }
                if (pickPacket.pos() != null) {
                    int cx = pickPacket.pos().getX() >> 4;
                    int cz = pickPacket.pos().getZ() >> 4;
                    if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(player.getCurrentWorldName(), cx, cz)) {
                        return false;
                    }
                }
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        if (packet instanceof ServerboundPickItemFromEntityPacket) {
            try {
                ServerboundPickItemFromEntityPacket pickPacket = (ServerboundPickItemFromEntityPacket) packet;
                if (pickPacket.includeData() && !player.isCreativeOrSpectator()) {
                    return false;
                }
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        if (!(packet instanceof ServerboundContainerClickPacket)) return true;

        try {
            if (player.isDead()) return false;

            ServerboundContainerClickPacket clickPacket = (ServerboundContainerClickPacket) packet;

            int slot = clickPacket.getSlotNum();
            int button = clickPacket.getButtonNum();
            ClickType type = clickPacket.getClickType();

            if (type == null) {
                return false;
            }

            if (slot < -1 && slot != -999) {
                return false;
            }

            Player bukkitPlayer = player.getPlayer();
            if (bukkitPlayer != null && slot >= 0) {
                InventoryView view = bukkitPlayer.getOpenInventory();
                if (view != null) {
                    int maxSlots = view.countSlots();
                    if (slot >= maxSlots) {
                        return false;
                    }
                    if (type == ClickType.QUICK_MOVE && maxSlots <= 0) {
                        return false;
                    }
                }
            }

            if (type == ClickType.SWAP) {
                // Hotbar swap: slots 0-8 or offhand swap 40
                if (slot < 0 || (button < 0 || (button > 8 && button != 40))) {
                    return false;
                }
            } else if (type == ClickType.QUICK_MOVE) {
                if (player.incrementAndGetQuickMoveCount(System.currentTimeMillis()) > 50) {
                    return false;
                }
            } else if (type == ClickType.CLONE) {
                if (!player.isCreativeOrSpectator()) {
                    return false;
                }
            } else if (type == ClickType.THROW || type == ClickType.PICKUP || type == ClickType.PICKUP_ALL) {
                if (button != 0 && button != 1) {
                    return false;
                }
            } else if (button < 0 || button > 100) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}